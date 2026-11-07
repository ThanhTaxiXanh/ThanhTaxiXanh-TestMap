# ThanhTaxiXanh-TestMap — TTX Waze Mod 10 Bridge

Ứng dụng thử nghiệm Android dùng **Binder IPC có cấp quyền** để nhận tốc độ GPS, giới hạn tốc độ và tối đa ba cảnh báo phía trước từ **Waze Mod 10**, hiển thị trong ứng dụng hoặc HUD nổi trên điện thoại.

**Trạng thái:** POC từ phân tích tĩnh APK; chưa xác nhận build thành công hoặc hoạt động trên máy thật. Đây không phải API chính thức của Waze. Không kèm dữ liệu bản đồ hay dữ liệu biển báo toàn quốc.

## Quy tắc cứng cho bản đồ / tile / tìm kiếm / ảnh bản đồ

Đối với các đầu ra bản đồ thuộc quyền quản lý của dự án, quy ước hiển thị do chủ dự án yêu cầu là **Hoàng Sa (Việt Nam)**, **Trường Sa (Việt Nam)** và **Biển Đông**. Không đưa nhãn hoặc đường ranh giới khẳng định chủ quyền của bên khác vào các bản đồ do dự án phát hành. Các đảo liên quan là đối tượng của tranh chấp quốc tế; quy ước hiển thị này là chính sách trình bày của sản phẩm, không thay thế kết luận pháp lý.

**Không bỏ qua gate:** mọi workflow build phải chạy `python3 scripts/sovereignty_gate.py` và `python3 -m unittest discover -s tests -v` **trước Gradle**. Job build chỉ chạy khi gate PASS. Hiện prototype **chưa vẽ bản đồ, dùng tile, tìm kiếm địa điểm hoặc phát hành ảnh bản đồ**. Gate mặc định **fail closed** nếu sau này bổ sung map SDK, tile/GeoJSON, ảnh hoặc nguồn địa lý; chưa có chứng nhận ảnh hoặc tile thực tế. Muốn bật các tính năng đó phải thiết kế các bài kiểm thử dữ liệu/visual snapshot và thông qua kiểm toán, không chỉ sửa cờ JSON.

Không thể áp đặt nhãn lên giao diện Waze hoặc tile/sản phẩm của bên thứ ba; cần dùng render/tile pipeline có kiểm soát trước khi phát hành. Để chống bypass qua push trực tiếp hoặc workflow mới, bật GitHub Branch Ruleset yêu cầu CI PASS và hạn chế sửa workflow, gate.

## Build trên GitHub Actions (không cần PC)

Vào **Actions → Build Android (sovereignty gated) → Run workflow**, hoặc push/PR để chạy tự động. Chỉ tải APK từ workflow có kết quả PASS. Nếu build FAIL, không dùng APK như bản nghiệm thu.

## Sử dụng

1. Cài Waze Mod 10 package `com.waze` và APK Bridge.
2. Mở Bridge → **Xin quyền Waze Mod 10** → xác nhận consent bên Waze Mod.
3. Chọn **Kết nối lại**; xem tốc độ, biển giới hạn và danh sách cảnh báo.
4. Bật **HUD nổi** và cấp quyền "Hiển thị trên ứng dụng khác" nếu muốn hiển thị trên ứng dụng Android khác.
5. `speed_limit=0` có nghĩa là *chưa biết*; dữ liệu quá 5 giây được coi là cũ. Đừng coi cảnh báo như dữ liệu pháp lý hay thay thế quan sát đường.

**Giới hạn:** Binder chỉ gửi trạng thái hiện tại, không trả toàn bộ tọa độ/đoạn đường/cơ sở dữ liệu quốc gia. HUD nổi không được coi là giao diện Android Auto. Không vượt qua kiểm tra UID/chứng chỉ/consent của Waze Mod.

Mã nguồn Kotlin: `app/src/main/java/vn/thanhtaxixanh/wazebridge/`. Bộ quy tắc: `policy/`, kiểm thử: `scripts/` + `tests/`.
