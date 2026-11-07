# TTX Waze Mod 10 Bridge — kiểm thử & giới hạn

## Nguồn
Phân tích tĩnh tệp `Waze_Mod_10_release_20261012_233501.apk` của người dùng. Đây là giao diện không chính thức, không phải SDK Waze.

- Service `com.waze.gw.WazeModDataService`, action `com.wazemod.api.action.BIND`.
- Consent `com.waze.gw.ApiConsentActivity`, action `com.wazemod.api.action.REQUEST_ACCESS`.
- Descriptor `com.wazemod.api.IWazeModData`, transaction version=2, snapshot=3, register=4, unregister=5.
- Kết quả snapshot dạng `Bundle`: `speed`, `speed_limit`, `overspeed`, `current_street`, `alerts`, `navigating`, `next_street`, `turn_distance_m`.
- `alerts` có mã `type`, `distance_m`, `value`; dữ liệu không đồng nghĩa với vị trí GIS hay kho biển báo.
- Kiểm soát quyền theo caller UID/chữ ký; không bỏ qua consent.

## Build & acceptance

1. Mở GitHub Actions, chọn run có commit SHA đúng bản thử.
2. Job **Mandatory sovereignty and release-surface gate** phải SUCCESS.
3. Job assemble phải SUCCESS và có artifact APK hợp lệ.
4. Cài `com.waze` Mod 10 và bridge cùng thiết bị Android không root.
5. Xác minh màn hình Consent của Waze Mod xuất hiện và cấp quyền cho bridge.
6. Đọc snapshot có `speed`, `speed_limit`, `alerts`, thử ở nơi biết rõ biển thực tế.
7. Bật HUD; thử chuyển app, khóa/mở màn hình, tắt Waze; ghi nhận độ trễ và dữ liệu stale.
8. Không cấp PASS cho dữ liệu biển báo đến khi đối chiếu video và GPX thực tế.

## Bộ kiểm tra hiển thị bản đồ (chưa có map trong prototype)
- Bắt buộc giữ policy labels: Hoàng Sa (Việt Nam), Trường Sa (Việt Nam), Biển Đông.
- Chặn mọi nguồn tile/map/search/map screenshots mới cho đến khi có style/tile provenance, ảnh chụp ở nhiều zoom và kiểm tra tên/đường biên.
- Tile raster phải QA từng snapshot; tile vector phải kiểm tra source-layer và style chuyển tên theo zoom.
- Dữ liệu search phải có post-filter/provenance và kiểm thử query địa danh ở nhiều ngôn ngữ.
- Gói release phải xuất artifact test cùng hash source, style và dữ liệu, đối soát trước khi ký phát hành.
- CI hiện xác nhận **không có đầu ra bản đồ**; chưa xác nhận bản đồ tương lai hiển thị đạt yêu cầu.
- Chưa thể ép Waze hoặc dữ liệu bên thứ ba thay đổi nhãn. Để kiểm soát hoàn toàn cần tile pipeline tự quản lý và quyền dữ liệu hợp lệ.
- GitHub Ruleset cho `main` phải yêu cầu status check và review sửa policy/workflow: repo hiện chưa thể thực thi khóa này bằng chính script.

## Khi cần phát triển map thật
Không thay `release_map_assets_enabled` từ false sang true để ép pass. Thêm module quản lý và kiểm thử dữ liệu/ảnh render riêng, mở PR để review trước khi bật đường build phát hành. Lưu ý các tranh chấp chủ quyền tồn tại và sản phẩm áp dụng quy ước trình bày do chủ dự án lựa chọn.
