# V0.2 — Kiểm tra ảnh HUD 0 km/h / dấu "—"

## Ảnh thử ngày 2026-11-07

Trên ảnh, màn hình chính đã hiển thị trạng thái Binder "Đã kết nối WazeMod Data API..." và overlay xuất hiện.
HUD báo dữ liệu cũ, biển "—", tốc độ GPS 0 km/h, tên đường chưa nhận. Điều này **chưa** xác nhận rằng giới hạn tốc độ từ Waze được truyền.

## Lỗi kỹ thuật đã được xác định và sửa

V0.1: sau khi đọc snapshot lúc bind, Bridge phụ thuộc callback `REGISTER_LISTENER`.
Mod 10 có thể chỉ gửi callback **khi chữ ký trạng thái thay đổi**; lúc xe đứng yên/không đổi, callback không hoạt động như heartbeat. UI cũ đánh dấu stale sau 5 giây mà không đọc lại snapshot.

V0.2: sau bind và kiểm tra API=1, Bridge gọi `GET_SNAPSHOT` (TX3) định kỳ 1 giây trong khi dịch vụ còn kết nối. Listener vẫn hoạt động để có cập nhật nhanh. Cảnh báo lỗi snapshot, quyền bị từ chối và thiếu dữ liệu được tách riêng. Không sử dụng quyền vượt xác thực.

## Nghiệm thu khi xe đứng yên, không lái

1. Cài APK mới nhất từ workflow SUCCESS sau merge, Waze Mod 10 chạy cùng điện thoại.
2. Mở Waze Mod, chờ vị trí GPS xác định và nếu có thì hiện biển tốc độ trên Waze.
3. Mở TTX Bridge → Xin quyền (nếu cần) → Kết nối lại → Bật HUD.
4. Nhìn dòng **Nguồn dữ liệu** của Bridge:
   - "Đã nhận snapshot mới" + 0 km/h: Binder đang nhận nhưng Waze chưa có dữ liệu giới hạn/tên đường.
   - "Đã đồng bộ dữ liệu Waze" + giới hạn khác 0: đã có dữ liệu để đối chiếu.
   - "Snapshot không có timestamp" hoặc "snapshot cũ": cần ghi log để xác định contract/timestamp.
   - "Chưa cấp quyền": quay lại màn consent.
5. Trở lại Waze Mod và quan sát HUD 30–60 giây. 0 km/h khi đứng yên là **bình thường**, giới hạn 0 là **chưa biết**.
6. Nếu không có giới hạn, chụp cùng thời điểm **giao diện Waze Mod đang hiện biển giới hạn** và màn hình Bridge. So sánh khi xe đỗ an toàn.

## Kết quả PASS

- Compile + CI gate: cần PASS trên SHA của phiên bản mới.
- Kết nối Binder: trạng thái v1 + snapshot có timestamp.
- Đồng bộ độ tươi: sau 30s không thay đổi vẫn có dữ liệu mới (nếu Waze cung cấp).
- Tốc độ giới hạn: so sánh trực tiếp số trên Waze Mod với Bridge, không suy luận từ ảnh trên đường.
- Đối với biển báo: cần cảnh báo thực và đối chiếu khoảng cách; ảnh 0 alert không phải bằng chứng lỗi.

## Giới hạn

Polling 1Hz tăng số IPC nhưng không tự tạo dữ liệu GPS hay giới hạn tốc độ.
Không có bản đồ, tile, geocoding hay ảnh bản đồ nào được thêm vào; mandatory sovereignty gate vẫn phải PASS trước Gradle.
