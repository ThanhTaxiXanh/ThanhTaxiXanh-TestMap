# TTX Speed & Signs — GPS độc lập, Waze Mod 10 tùy chọn

**Phiên bản thử nghiệm:** 0.3.0-independent. **Không bắt buộc cài Waze**. Có thể sử dụng trên Android 10+ với GPS riêng và bộ dữ liệu tốc độ/biển báo ngoại tuyến do người dùng nhập. Dữ liệu Waze Mod 10 là nguồn bổ sung có cấp quyền, không dùng mã/dữ liệu proprietary của Waze.

## Trạng thái chức năng

| Nguồn | Không cài Waze | Khi Waze bị đóng |
|---|---|---|
| GPS hiện tại | ✅ với quyền vị trí | ✅ |
| Giới hạn tốc độ OSM | ✅ **sau khi nhập** tệp tốc độ OSM hợp lệ | ✅ |
| Biển báo dạng điểm | ✅ **sau khi nhập** tệp biển báo hợp lệ có tọa độ và hướng | ✅ |
| Dữ liệu giới hạn/cảnh báo Waze | Không có | Chuyển dự phòng ngoại tuyến |
| HUD nổi điện thoại | ✅ sau khi cấp quyền | ✅ |
| Android Auto | Chưa triển khai | Chưa triển khai |

**Quan trọng:** APK không đóng gói sẵn dữ liệu biển báo toàn quốc. Nếu chưa nhập tệp tốc độ, TTX vẫn đọc GPS nhưng giới hạn tốc độ sẽ là “—”. Chỉ dùng dữ liệu có nguồn gốc và quyền sử dụng rõ ràng; biển báo ngoài đường là căn cứ ưu tiên. Không sử dụng để thay thế quan sát giao thông thực tế.

## Nhận diện Waze Mod 10 qua com.waze

- TTX dùng Android PackageManager để phát hiện **package `com.waze`**, giống phần chọn nguồn trong ảnh bạn cung cấp.
- Phát hiện `com.waze` **không đồng nghĩa** Waze Mod 10. TTX xác minh bằng Binder API v1 `com.waze.gw.WazeModDataService`.
- Nếu bạn cho phép trong màn hình consent của Waze Mod 10, TTX có thể ưu tiên giới hạn/cảnh báo Waze khi Waze đang thực sự cung cấp dữ liệu.
- Nếu package không có, Binder không được cấp quyền hoặc dữ liệu Waze cũ, **TTX vẫn tiếp tục GPS và bộ dữ liệu ngoại tuyến**; không cố vượt quyền và không cố giữ Waze chạy bằng hack.
- Có thể chọn **Tự động**, **Chỉ ngoại tuyến** hoặc **Ưu tiên Waze Mod 10**; mặc định là Tự động.

## Nhập dữ liệu OSM Việt Nam bằng điện thoại

1. Cài APK từ GitHub Actions build SUCCESS.
2. Cấp quyền vị trí chính xác cho TTX (bật Location/GPS trên điện thoại).
3. Nhấn **Nhập dữ liệu tốc độ / biển báo từ JSON**, chọn `vietnam_speed_limits_1.json` trên điện thoại. Tệp từng được bạn cung cấp có schema `features` với `way_id`, `maxspeed_kmh`, `forward_kmh`, `backward_kmh`, bbox và `geometry` dạng chuỗi các cặp **[vĩ độ, kinh độ]**. Importer đọc theo luồng và lưu SQLite, không cần root.
4. Nếu có bộ dữ liệu biển báo tự thu thập/được phép tái sử dụng, nhập thêm tệp JSON với khóa `signs`; xem `docs/OFFLINE_FORMAT_AND_TEST.md`.
5. Xem thống kê số đoạn đường/biển đã nhập ở phần **Trạng thái nguồn dữ liệu**.

Nếu tệp trước đây không còn trên điện thoại, bạn cần tải/copy tệp đó sang điện thoại trước khi nhập; chương trình không tự trích xuất dữ liệu Waze. OSM tuân theo ODbL và cần ghi công nguồn: © OpenStreetMap contributors.

**Phạm vi chính xác:** mẫu địa lý 35–60m không thay thế map matching đầy đủ; nếu đường song song/thiếu hướng hoặc độ chính xác GPS thấp, TTX báo chưa xác định. Không tự suy diễn tốc độ mặc định theo loại đường. Cảnh báo chỉ hiển thị ở phía trước khi có hướng chuyển động GPS đủ tin cậy và bản ghi điểm biển báo hợp lệ.

## Build trên GitHub Actions

Workflow `.github/workflows/build-android.yml` phải chạy job `Mandatory sovereignty gate` và các bài regression trước. Job `Build APK only after gate PASS` mới được biên dịch. Bản mới bổ sung unit tests hình học và chỉ xuất `TTX-Independent-SpeedSigns-debug-apk` khi thành công.

**Ruleset main:** `TTX-Main-Sovereignty-Protection` yêu cầu PR và 2 status checks PASS. Quy ước sản phẩm Hoàng Sa (Việt Nam), Trường Sa (Việt Nam), Biển Đông nằm tại `policy/`; prototype chưa vẽ map/tile/search/ảnh bản đồ và gate sẽ chặn nguồn bản đồ chưa kiểm chứng.

## Modules

- `OfflineRoadDatabase.kt`: import JSON theo luồng, SQLite transaction rollback, truy vấn theo bbox/geometry, điểm biển báo có hướng.
- `GeoMath.kt`: đo khoảng cách GPS–đoạn đường, hướng đi.
- `IndependentEngine.kt`: Android GPS + ngoại tuyến, không phụ thuộc Waze.
- `DataSourceCoordinator.kt`: nhận diện `com.waze`, ghép trạng thái nguồn, chuyển dự phòng.
- `WazeModClient.kt`: API Binder consent-gated Mod10 (không chính thức).
- `MainActivity.kt`, `HudOverlayService.kt`: giao diện chọn nguồn, nhập dữ liệu và HUD nổi điện thoại.
- `docs/OFFLINE_FORMAT_AND_TEST.md`: định dạng nhập và nghiệm thu.
