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
 * Unofficial interface reconstructed for Waze Mod 10 V15.3. NOT an official Waze SDK.
 * Requires explicit user consent in WazeMod; no UID/signature bypass.
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

    /** Must be launched FOR RESULT by an Activity to bind the consent to the caller. */
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
                    val bundle = if (data.readInt() != 0) Bundle.CREATOR.createFromParcel(data) else null
                    if (bundle != null) main.post { onData(WazeSnapshot.from(bundle)) }
                    return true
                }
                CALLBACK_REVOKED -> {
                    data.enforceInterface(LISTENER_INTERFACE)
                    main.post { onStatus("Waze Mod đã thu hồi quyền; cần xin cấp lại.") }
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
                val v = transact(TX_VERSION) { it.readInt() }
                onStatus("Đã kết nối Waze Mod API v$v")
                getSnapshot()?.let(onData)
                changeListener(TX_REGISTER)
                listenerRegistered = true
            } catch (e: SecurityException) {
                onStatus("Chưa được cấp quyền. Hãy chọn Xin quyền Waze Mod 10.")
            } catch (e: Exception) {
                onStatus("Lỗi IPC: ${e.javaClass.simpleName}: ${e.message.orEmpty()}")
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            listenerRegistered = false
            onStatus("Mất kết nối Waze Mod")
        }
    }

    fun connect() {
        if (bound) return
        val intent = Intent("com.wazemod.api.action.BIND").setClassName(WAZE_PACKAGE, SERVICE)
        try {
            bound = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            onStatus(if (bound) "Đang kết nối..." else "Không tìm thấy Waze Mod 10")
        } catch (e: Exception) {
            bound = false
            onStatus("Không kết nối được: ${e.javaClass.simpleName}")
        }
    }

    fun disconnect() {
        try {
            if (listenerRegistered && remote != null) changeListener(TX_UNREGISTER)
        } catch (_: Exception) { }
        listenerRegistered = false
        remote = null
        if (bound) {
            try { appContext.unbindService(connection) } catch (_: Exception) { }
            bound = false
        }
    }
    fun reconnect() { disconnect(); connect() }

    private fun <T> transact(code: Int, read: (Parcel) -> T): T {
        val binder = remote ?: error("Service chưa kết nối")
        val request = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            request.writeInterfaceToken(DATA_INTERFACE)
            if (!binder.transact(code, request, reply, 0)) error("Không hỗ trợ giao dịch $code")
            reply.readException()
            return read(reply)
        } finally {
            request.recycle()
            reply.recycle()
        }
    }

    fun getSnapshot(): WazeSnapshot? = transact(TX_SNAPSHOT) { parcel ->
        if (parcel.readInt() == 0) null else WazeSnapshot.from(Bundle.CREATOR.createFromParcel(parcel))
    }

    private fun changeListener(code: Int) {
        val binder = remote ?: return
        val request = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            request.writeInterfaceToken(DATA_INTERFACE)
            request.writeStrongBinder(callback)
            if (!binder.transact(code, request, reply, 0)) error("Listener không được hỗ trợ")
            reply.readException()
        } finally {
            request.recycle()
            reply.recycle()
        }
    }
}
