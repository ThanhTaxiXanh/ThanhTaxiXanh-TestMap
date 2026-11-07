package vn.thanhtaxixanh.wazebridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel

/**
 * Reverse-engineered, consent-gated data interface from the supplied
 * Waze Mod 10 V15.3 APK only. NOT an official Waze API.
 * Never try to bypass WazeMod's UID/certificate grant checks.
 */
class WazeModClient(
    context: Context,
    private val onStatus: (String) -> Unit,
    private val onData: (WazeSnapshot) -> Unit
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var bound = false
    private var remote: IBinder? = null
    private var listenerRegistered = false
    private val pollIntervalMs = 1_000L
    private var polling = false
    private var consecutiveReadFailures = 0

    /**
     * Callback notifications are change-driven, not guaranteed heartbeats.
     * Explicit GET_SNAPSHOT refreshes the timestamp even while the car is stopped.
     * No Waze private storage, network endpoints or permission bypass is used.
     */
    private val snapshotPoll = object : Runnable {
        override fun run() {
            if (!polling || remote == null) return
            try {
                val snapshot = getSnapshot()
                if (snapshot != null) {
                    consecutiveReadFailures = 0
                    onData(snapshot)
                } else {
                    consecutiveReadFailures++
                    if (consecutiveReadFailures == 1 || consecutiveReadFailures == 5) {
                        onStatus("Đã kết nối API nhưng Waze Mod chưa trả snapshot")
                    }
                }
            } catch (ex: SecurityException) {
                stopPolling()
                onStatus("Quyền đọc Waze Mod bị từ chối/thu hồi. Hãy xin cấp lại.")
                return
            } catch (ex: Exception) {
                consecutiveReadFailures++
                if (consecutiveReadFailures == 1 || consecutiveReadFailures == 5) {
                    onStatus("Không đọc được dữ liệu Waze: ${ex.javaClass.simpleName}")
                }
            }
            if (polling && remote != null) main.postDelayed(this, pollIntervalMs)
        }
    }

    private fun startPolling() {
        stopPolling()
        consecutiveReadFailures = 0
        polling = true
        main.post(snapshotPoll)
    }

    private fun stopPolling() {
        polling = false
        main.removeCallbacks(snapshotPoll)
    }

    companion object {
        const val WAZE_PACKAGE = "com.waze"
        const val SERVICE = "com.waze.gw.WazeModDataService"
        const val CONSENT_ACTIVITY = "com.waze.gw.ApiConsentActivity"
        const val ACTION_REQUEST = "com.wazemod.api.action.REQUEST_ACCESS"
        const val DATA_INTERFACE = "com.wazemod.api.IWazeModData"
        const val LISTENER_INTERFACE = "com.wazemod.api.IWazeModListener"
        const val TX_VERSION = 2
        const val TX_SNAPSHOT = 3
        const val TX_REGISTER = 4
        const val TX_UNREGISTER = 5
        const val CALLBACK_UPDATE = 2
        const val CALLBACK_REVOKED = 3
    }

    /** Launch from Activity FOR RESULT: consent uses Activity.getCallingPackage(). */
    fun consentIntent() = Intent(ACTION_REQUEST).setClassName(WAZE_PACKAGE, CONSENT_ACTIVITY)

    private val callback = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == IBinder.INTERFACE_TRANSACTION) {
                reply?.writeString(LISTENER_INTERFACE)
                return true
            }
            when (code) {
                CALLBACK_UPDATE -> {
                    data.enforceInterface(LISTENER_INTERFACE)
                    val snapshot = if (data.readInt() != 0) {
                        Bundle.CREATOR.createFromParcel(data)
                    } else null
                    if (snapshot != null) {
                        main.post { onData(WazeSnapshot.from(snapshot)) }
                    }
                    return true
                }
                CALLBACK_REVOKED -> {
                    data.enforceInterface(LISTENER_INTERFACE)
                    main.post { onStatus("Waze Mod đã thu hồi quyền. Xin cấp lại trong ứng dụng.") }
                    return true
                }
            }
            return super.onTransact(code, data, reply, flags)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remote = service
            try {
                val v = readVersion()
                if (v != 1) {
                    stopPolling()
                    onStatus("WazeMod API v$v chưa được thử nghiệm (Bridge hỗ trợ v1)")
                    return
                }
                // A listener provides low latency; periodic snapshots remain the fallback.
                val subscribed = try {
                    register()
                    true
                } catch (ex: SecurityException) {
                    throw ex
                } catch (_: Exception) {
                    false
                }
                onStatus(if (subscribed) "Đã cấp quyền · kết nối WazeMod API v$v" else
                    "Đã kết nối WazeMod API v$v · dùng chế độ đọc định kỳ")
                startPolling()
            } catch (ex: SecurityException) {
                stopPolling()
                onStatus("Chưa cấp quyền. Bấm 'Xin quyền Waze Mod 10'.")
            } catch (ex: Exception) {
                stopPolling()
                onStatus("Lỗi Binder: ${ex.javaClass.simpleName}: ${ex.message.orEmpty()}")
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            stopPolling()
            remote = null
            listenerRegistered = false
            onStatus("Mất kết nối Waze Mod")
        }
    }

    fun connect() {
        if (bound) return
        val target = Intent("com.wazemod.api.action.BIND").setClassName(WAZE_PACKAGE, SERVICE)
        try {
            bound = appContext.bindService(target, connection, Context.BIND_AUTO_CREATE)
            onStatus(if (bound) "Đang kết nối..." else "Không tìm thấy Waze Mod 10 (com.waze)")
        } catch (e: Exception) {
            bound = false
            onStatus("Không bind được: ${e.javaClass.simpleName}")
        }
    }

    fun disconnect() {
        stopPolling()
        try {
            if (listenerRegistered) changeListener(TX_UNREGISTER)
        } catch (_: Exception) { /* service may already be gone */ }
        listenerRegistered = false
        remote = null
        if (bound) {
            try { appContext.unbindService(connection) } catch (_: Exception) { }
            bound = false
        }
    }

    fun reconnect() {
        disconnect()
        connect()
    }

    private inline fun <T> transact(code: Int, reader: (Parcel) -> T): T {
        val binder = remote ?: throw IllegalStateException("Service chưa kết nối")
        val req = Parcel.obtain()
        val res = Parcel.obtain()
        try {
            req.writeInterfaceToken(DATA_INTERFACE)
            if (!binder.transact(code, req, res, 0)) throw IllegalStateException("Giao dịch ${code} không được hỗ trợ")
            res.readException() // Enforces original service's access policy
            return reader(res)
        } finally {
            res.recycle()
            req.recycle()
        }
    }

    private fun readVersion(): Int = transact(TX_VERSION) { it.readInt() }

    fun getSnapshot(): WazeSnapshot? = transact(TX_SNAPSHOT) { reply ->
        if (reply.readInt() == 0) null else WazeSnapshot.from(Bundle.CREATOR.createFromParcel(reply))
    }

    private fun changeListener(code: Int) {
        val binder = remote ?: return
        val req = Parcel.obtain()
        val res = Parcel.obtain()
        try {
            req.writeInterfaceToken(DATA_INTERFACE)
            req.writeStrongBinder(callback)
            if (!binder.transact(code, req, res, 0)) throw IllegalStateException("Không hỗ trợ listener")
            res.readException()
        } finally {
            res.recycle()
            req.recycle()
        }
    }

    private fun register() {
        changeListener(TX_REGISTER)
        listenerRegistered = true
    }
}
