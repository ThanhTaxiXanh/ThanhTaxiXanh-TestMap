# v0.4.0 — Hiển thị biển báo khi không mở Waze

## Tiêu chí bắt buộc
- Waze chính thức hoặc Waze Mod 10 có thể đã cài (com.waze) nhưng **người dùng không được yêu cầu mở Waze** trước khi chạy TTX.
- TTX không dùng startActivity / launcher Intent đến com.waze trong đường xử lý dữ liệu tự động. Chỉ thao tác chủ động "Xin quyền Waze Mod" có thể mở màn xác nhận, vốn không cần để dùng OSM.
- Ngay khi GPS chính xác, TTX tra SQLite riêng. Nếu có Internet và dữ liệu cache khu vực chưa có/hết hạn, trình cập nhật OSM tự động hỏi biển báo có sẵn trong vùng lân cận. Dữ liệu tải thành công tồn tại khi tắt mạng.
- **Không cam kết toàn bộ biển giống Waze**: dữ liệu OSM là nguồn độc lập và có thể không có biển ở vị trí cụ thể.

## Nguồn dữ liệu/đạo đức sử dụng
- OpenStreetMap ODbL: https://www.openstreetmap.org/copyright — © OpenStreetMap contributors
- Overpass API công khai: https://wiki.openstreetmap.org/wiki/Overpass_API
- Mỗi truy vấn chỉ lấy node traffic_sign và highway=speed_camera trong bán kính 2,5 km; tối đa một yêu cầu/10 phút, không quá 24 yêu cầu/ngày/thiết bị. Lỗi mạng/quá tải: nghỉ tối thiểu 30 phút. Cache tile 24 giờ; bản ghi giữ tối đa 30 ngày. Không sử dụng cho quy mô lớn; nên thay bằng gói xuất bản vùng từ Geofabrik khi phát triển sản phẩm.
- Không truy vấn API Waze không công khai, không sao chép kho dữ liệu hay giải mã tài sản riêng Waze.

## Thành phần
- OsmSignRefresh.kt: HTTPS, giới hạn kích thước trả lời, parser node và mapping chỉ các mã chắc chắn; bỏ qua biển không rõ nghĩa.
- OfflineRoadDatabase.kt: SQLite v2 có osm_signs riêng; giữ nguyên roads/signs do người dùng nhập trong nâng cấp từ v1.
- IndependentEngine.kt: GPS -> SQLite -> cập nhật riêng OSM ngoài luồng GPS -> làm mới nếu vị trí còn mới.
- DataSourceCoordinator.kt: Waze Binder giữ làm tùy chọn có consent; GPS và OSM không phụ thuộc Mod.
- Unit tests OsmSignTagsTest.kt: không bịa biển lạ, phân biệt km/h với mph, hướng.

## Quy trình nghiệm thu thiết bị
1. Từ GitHub Actions build SUCCESS đúng SHA, tải artifact TTX-Independent-SpeedSigns-debug-apk. Không dùng bản APK cũ.
2. Cài TTX 0.4.0, bật vị trí chính xác và mạng. Nếu Waze đã cài, **không mở Waze** (có thể Force stop Waze để thử rõ).
3. Trong TTX chọn **Chỉ GPS + dữ liệu ngoại tuyến**. Không bấm Xin quyền Waze Mod 10.
4. Sau khi GPS xác định, nhìn trạng thái OSM: cập nhật N biển. N có thể bằng 0 nếu OSM khu vực không gắn thẻ. Chụp trạng thái và số điểm đã cache.
5. Đối chiếu biển thật được OSM gắn vị trí với HUD, hướng và khoảng cách. Khi xe đứng yên không có bearing tin cậy, việc không phát cảnh báo là dự kiến. Nên ghi hình khi hành khách hỗ trợ quan sát; không tương tác khi lái.
6. Tắt Internet sau lần tải thành công (không tắt GPS). Kiểm tra cùng vùng biển đó vẫn xuất hiện khi có hướng di chuyển hợp lệ.
7. Thử Waze Mod 10 đã cài nhưng chưa mở: kết quả OSM không đổi; trạng thái Binder không phải bằng chứng nhận biển từ Waze.
8. Thử quyền GPS bị thu hồi hoặc GPS sai: TTX không hiển thị cảnh báo không đáng tin cậy như thể xác thực.
9. Ghi video + nhật ký nguồn để đối chiếu. **CI xanh không thay thế bằng chứng điện thoại.**

## Chưa hoàn tất
- Chưa có bộ biển toàn quốc đầy đủ; chưa kiểm thử trên thiết bị.
- Tốc độ giới hạn đoạn đường vẫn cần JSON OSM có geometry; không suy diễn từ biển lân cận.
- Biển thiếu hướng phải đối chiếu thực địa; hướng khớp không tương đương map matching theo segment ID.
- Overpass có thể từ chối hoặc thiếu dữ liệu; không cam kết 100%.
- Android Auto chưa triển khai.
