# Waze Mod 10 — Hợp đồng Binder IPC để hiển thị biển báo/tốc độ

**Phạm vi bằng chứng:** APK `Waze_Mod_10_release_20261012_233501.apk`; phân tích tĩnh DEX/Manifest, chưa có kiểm chứng runtime.

## Kết nối và phân quyền

| Thành phần | Giá trị |
|---|---|
| Package Waze | `com.waze` |
| Exported service | `com.waze.gw.WazeModDataService` |
| Bind action | `com.wazemod.api.action.BIND` |
| Consent activity | `com.waze.gw.ApiConsentActivity` |
| Consent action | `com.wazemod.api.action.REQUEST_ACCESS` |
| Interface token | `com.wazemod.api.IWazeModData` |
| Listener token | `com.wazemod.api.IWazeModListener` |

Theo bytecode, `DataApiGrants.isAllowedUid()` kiểm tra caller UID, package và SHA-256 chứng chỉ ký. Xin quyền qua Activity for result, không bypass phân quyền.

### Binder transaction

| Code | Hành động |
|---:|---|
| 2 | Get API version (dự kiến 1) |
| 3 | Get snapshot Bundle |
| 4 | Register listener Binder |
| 5 | Unregister listener Binder |

Listener code 2 trả callback snapshot, code 3 thông báo revoked. Chu kỳ handler 200 ms khi có thay đổi, không bảo đảm cập nhật đều 5 Hz. Đăng ký callback cần có quyền.

## Bundle snapshot

| Key | Loại | Ý nghĩa |
|---|---|---|
| `api` | Int | version |
| `timestamp` | Long | `SystemClock.elapsedRealtime` |
| `speed` | Int | GPS km/h |
| `speed_limit` | Int | giới hạn km/h, 0 là chưa rõ |
| `speed_min` | Int | tốc độ tối thiểu nếu có |
| `overspeed` | Boolean | trạng thái vượt tốc |
| `current_street` | String | tên đường |
| `navigating` | Boolean | dẫn đường |
| `turn`, `then_turn` | Int | chỉ dẫn rẽ |
| `turn_distance_m` | Int | khoảng cách rẽ |
| `roundabout_exit` | Int | vòng xuyến |
| `lanes` | Bundle[] | lane directions/recommended |
| `next_street`, `eta_clock` | String | đường sắp đến, ETA |
| `eta_minutes`, `remaining_m` | Int | ETA và quãng đường còn lại |
| `avg_zone`, `avg_remaining_m`, `avg_recommended_speed`, `avg_percent` | mixed | kiểm soát tốc độ trung bình |
| `alerts` | Bundle[] | tối đa 3 cảnh báo |

Bundle của mỗi alert: `type`, `distance_m`, `value`, `jam_severity`, `jam_delay_min`. Diễn giải loại cảnh báo theo CSV. Không có tọa độ lat/lon hay segment ID trong IPC này.

## Dataset nội bộ (không thuộc hợp đồng IPC)

Bytecode tham chiếu `RoadNoticeClient`, `RoadNoticeDatabase`, `DatasetEnvelope`. Dấu vết tĩnh:
- Host strings: `https://wazemod.io.vn`, `https://wazemod.chisadin.id.vn`.
- Path strings: `/api/v1/road-notices/manifest`, `/api/v1/road-notices/files/`, `/road_notices.pb`.
- Format labels: `road-notices`, `wme-point-notices`, `wme-context-aware-alerts`; `AES-256-GCM/v1`, SHA-256, Ed25519.

Không xác nhận endpoint hoạt động hoặc cho phép tải lại/redistribute; dự án này không giải mã hoặc sao chép kho dữ liệu bên ngoài.

## Tích hợp và giới hạn

- Android Phone: Binder có cấp quyền → `WazeSnapshot` → UI tốc độ/biển → overlay với `SYSTEM_ALERT_WINDOW`.
- Android Auto: Xây module AndroidX Car App Library riêng trong app tương thích, không coi overlay điện thoại là Android Auto.
- Không tìm kiếm hàng loạt mọi biển báo Việt Nam thông qua snapshot; ứng dụng cần nguồn dữ liệu địa lý hợp lệ nếu muốn hiển thị vị trí biển độc lập.

## Checklist nghiệm thu

- [ ] CI `:app:assembleDebug` SUCCESS; artifact APK xuất hiện.
- [ ] Mở consent của Mod 10 và kết quả RESULT_OK.
- [ ] Bind được service, TX2 trả phiên bản; TX3 trả Bundle có speed/limit/alerts.
- [ ] Hiển thị 0 km/h khi xe dừng và đối chiếu tốc độ trên đường.
- [ ] Kiểm chứng speed_limit với biển báo thực tế, xử lý UNKNOWN=0.
- [ ] Kiểm chứng loại alert/distance bằng hiện trường và tính hướng di chuyển.
- [ ] Listener 5 phút ổn định, kiểm tra stale và khi chuyển app.
- [ ] Thu hồi quyền, kiểm tra Bridge thông báo và ẩn thông tin.
- [ ] Overlay chạy trên điện thoại; Android Auto vẫn CHƯA nghiệm thu.

**Cảnh báo:** Đây là API không chính thức trong bản Mod, có thể thay đổi hoặc không hoạt động tùy môi trường thiết bị.
