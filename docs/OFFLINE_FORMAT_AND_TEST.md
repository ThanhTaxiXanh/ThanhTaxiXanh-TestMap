# Định dạng dữ liệu ngoại tuyến và nghiệm thu

## 1. Tệp tốc độ đường OSM

Hỗ trợ **trực tiếp** schema đã được người dùng cung cấp trong tệp `vietnam_speed_limits_1.json`:

```json
{
  "schema": 1,
  "region": "VN",
  "type": "speed_limits",
  "source": "https://download.geofabrik.de/asia/vietnam-latest.osm.pbf",
  "license": "ODbL; © OpenStreetMap contributors",
  "features": [
    {
      "way_id": 12345,
      "name": "ĐƯỜNG MẪU, KHÔNG PHẢI DỮ LIỆU THỰC",
      "maxspeed_kmh": 50,
      "forward_kmh": null,
      "backward_kmh": null,
      "minlat": 0.0, "maxlat": 0.001,
      "minlon": 0.0, "maxlon": 0.001,
      "geometry": "[[0.0,0.0],[0.001,0.001]]"
    }
  ]
}
```

**Không sử dụng dữ liệu ví dụ này khi di chuyển**. Importer chỉ lưu các feature có `way_id`, bbox, geometry và giá trị speed có thực. Hình học là chuỗi JSON các tọa độ theo thứ tự **[lat, lon]**; hệ thống đo khoảng cách đến đường và xác định giới hạn theo chiều đi nếu có đủ hướng GPS. Khi nhiều đường gần nhau có giới hạn khác nhau, hiển thị không xác định thay vì đoán.

Tệp JSON người dùng cung cấp trước đây có dung lượng ~17 MB; importer dùng streaming JsonReader + SQLite, không giữ cả bộ JSON trong RAM.

## 2. Tệp điểm biển báo được cấp quyền

Tệp riêng có root `source`, `license`, và mảng `signs`:

```json
{
  "schema": 1,
  "region": "VN",
  "source": "User-surveyed signs — example only",
  "license": "Rights holder permission required before use",
  "signs": [
    {
      "id": "TEST-ONLY-001",
      "lat": 0.0, "lon": 0.0,
      "type": 23, "value": 0, "heading": 90,
      "source": "Example fixture — not a real sign"
    }
  ]
}
```

Không có danh sách vị trí biển thật được đóng gói sẵn. `heading` tính theo độ từ Bắc (0–360), là hướng lưu thông của biển. TTX chỉ hiển thị điểm nằm phía trước theo hướng GPS và cách không quá 500m, không cho rằng biển áp dụng cho mọi hướng. Thiếu hướng sẽ không loại bỏ kiểm tra vị trí phía trước theo GPS, nhưng độ tin cậy vẫn phải được xác minh.

## 3. Nghiệm thu trên điện thoại không root

- **Không cài Waze:** GPS vị trí chính xác hoạt động, tốc độ xe hiển thị. Trước khi nhập đường, biển giới hạn là “—”.
- **Sau nhập OSM:** ứng dụng báo số đoạn đường được lưu; khi có road-match đủ gần, hiển thị giới hạn cùng nguồn “GPS + dữ liệu ngoại tuyến”; kiểm tra biển ngoài thực tế.
- **Sau nhập điểm biển:** cảnh báo đúng loại và khoảng cách khi đi vào vùng cảnh báo đúng hướng; không kiểm chứng chỉ bằng thử điểm giả.
- **Cài `com.waze` chính thức:** package được phát hiện nhưng không mặc định xác nhận API Mod10; ngoại tuyến tiếp tục hoạt động.
- **Cài Waze Mod 10:** xin quyền, xác nhận Binder API v1; có dữ liệu thì dùng Waze; vuốt đóng Waze thì tự trở về nguồn ngoại tuyến.
- **Ngắt GPS:** thông báo nguồn hết dữ liệu, không hiển thị giới hạn/tốc độ cũ như giá trị mới.
- **Bật HUD nổi:** vẫn hiển thị GPS/giới hạn offline khi rời TTX và không cài Waze.

## 4. Giới hạn an toàn

Các giới hạn tốc độ có thể phụ thuộc hướng đi, giờ, loại phương tiện, biển tạm thời và quy định địa phương. Hệ thống không suy diễn những quy tắc đó khi thiếu dữ liệu. Kết quả OSM là thông tin tham khảo, không phải chỉ dẫn pháp lý.
