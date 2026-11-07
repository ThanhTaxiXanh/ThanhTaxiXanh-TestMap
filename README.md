# TTX Waze Mod 10 Bridge — nguồn Android thử nghiệm

> **Prototype 0.1.0:** hợp đồng IPC đã phân tích tĩnh từ APK Waze Mod 10 V15.3. Cần xác nhận build GitHub Actions và chạy trên điện thoại; KHÔNG phải ứng dụng Waze chính thức.

**Mục đích:** Đưa tốc độ GPS, giới hạn tốc độ, tên đường và cảnh báo phía trước từ **Waze Mod 10** sang HUD của ứng dụng Android riêng, chỉ sau khi người dùng cho phép.

**Repo:** https://github.com/ThanhTaxiXanh/ThanhTaxiXanh-TestMap

## Thành phần

- `WazeModClient.kt`: kết nối Binder với `com.waze.gw.WazeModDataService`, xin phép qua `ApiConsentActivity`, đọc snapshot và nhận callback.
- `WazeModels.kt`: mô hình speed, limit, overspeed, street, alerts, timestamp; phát hiện dữ liệu cũ.
- `AlertTypes.kt`: diễn giải 76 mã cảnh báo.
- `MainActivity.kt`: màn hình xin cấp quyền/đọc dữ liệu/test.
- `HudOverlayService.kt`: HUD nổi chỉ trên **điện thoại**, không phải Android Auto.
- `SpeedLimitSignView.kt`: biểu tượng giới hạn tốc độ vẽ bằng Android Canvas, không dùng tài sản Waze.
- `docs/PHAN_TICH_MOD10_IPC_VA_DU_LIEU.md`: hợp đồng kỹ thuật, ràng buộc và checklist.
- `docs/alert_types_full.csv`: bảng mã 0–75 lấy từ bytecode.

## Build APK chỉ bằng điện thoại và GitHub Actions

1. Mở tab **Actions** của repo này: https://github.com/ThanhTaxiXanh/ThanhTaxiXanh-TestMap/actions
2. Chọn workflow **Build TTX WazeMod10 Bridge APK**. Bản build cũng chạy tự động khi push vào `main`.
3. Chỉ khi job **assemble** có trạng thái **success** mới mở **Artifacts** và tải `TTX-WazeMod10-Bridge-debug-apk`.
4. Giải nén ZIP artifact và cài tệp `app-debug.apk` trên Android. Nếu CI chưa đạt, xem log trước, không coi là APK hoàn chỉnh.

Cấu hình: AGP 8.9.2, Kotlin 2.0.21, Gradle 8.11.1, Java 17, compileSdk 35, minSdk 29, targetSdk 35.

## Trình tự nghiệm thu trên Android

1. Cài và mở đúng `Waze_Mod_10_release_20261012_233501.apk` (package `com.waze`, kiến trúc ARM64). Không được cài đồng thời Mod 8 và Mod 10 nếu cùng package.
2. Cài Bridge APK được build thành công.
3. Nhấn **1 · Xin quyền Waze Mod 10**; cấp quyền trong màn hình Waze Mod nếu xuất hiện.
4. Nhấn **2 · Kết nối lại / đọc dữ liệu**. Ghi lại trạng thái, tốc độ, giới hạn và cảnh báo.
5. Nhấn **3 · Bật HUD nổi**; cấp quyền `Hiển thị trên ứng dụng khác` nếu điện thoại yêu cầu.
6. Mở ứng dụng bản đồ khác trên **điện thoại**, kiểm tra HUD và sự cập nhật trong ít nhất 5 phút. Dừng HUD bằng nút trong ứng dụng/thông báo.
7. Kiểm tra cả khi không dẫn đường và khi Waze đang dẫn đường; đối chiếu giới hạn tốc độ thực tế, độ trễ, mất kết nối và thu hồi quyền.

**Diễn giải dữ liệu:** `speed_limit = 0` nghĩa là *chưa có giới hạn được xác nhận*, không phải tốc độ giới hạn 0. `timestamp` dùng `SystemClock.elapsedRealtime()`. Sau 5 giây dữ liệu chưa cập nhật sẽ được đánh dấu cũ.

## Tích hợp vào ứng dụng Android khác

Chuyển 3 lớp `WazeModClient.kt`, `WazeModels.kt`, `AlertTypes.kt` vào app và sửa package. Khai báo `<queries><package android:name="com.waze"/></queries>`. Activity phải khởi chạy `consentIntent()` **for result**, sau `RESULT_OK` thì gọi `client.reconnect()`. Khi đóng app, `disconnect()`.

Binder action `com.wazemod.api.action.BIND` (interface `com.wazemod.api.IWazeModData`) hỗ trợ transaction 2=version, 3=snapshot, 4=register, 5=unregister. Listener nhận callback code 2=update hoặc 3=revoked.

API chỉ phát trạng thái HUD hiện tại và tối đa 3 cảnh báo phía trước; **không cấp lat/lon biển báo, geometry hay toàn bộ database Việt Nam**.

## Những điều chưa có

- Chưa xác minh thời gian chạy thật, cấp quyền thực tế hoặc độ trễ trên thiết bị.
- Chưa có Car App Service cho Android Auto. Android overlay trên điện thoại không thể vẽ trực tiếp lên Android Auto.
- Không đọc dữ liệu riêng tư trong app Waze, không giả mạo chữ ký hay vượt các kiểm tra UID/chứng chỉ.
- Không thể khẳng định quyền tái phân phối dữ liệu WazeMod hoặc độ chính xác pháp lý của biển báo.

### An toàn

Thông tin biển báo là dữ liệu hỗ trợ tham khảo; luôn ưu tiên biển báo và quy định ngoài đường. Cấp quyền theo luồng đồng ý của người dùng, không hardcode token/secret. Bản APK debug phục vụ thử nghiệm, không phải bản phát hành thương mại.
