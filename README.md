# Gia Phả Việt – Hệ thống quản lý gia phả đa dòng họ

Nền tảng web cho phép nhiều dòng họ (tenant) độc lập cùng xây dựng cây phả hệ, phân quyền theo vai trò, quản lý ngày giỗ theo **âm lịch** và tự động nhắc lịch.

## Công nghệ

| Lớp | Công nghệ |
|---|---|
| Backend | Java 21, Spring Boot 3.3 (Web, Security + JWT, Data JPA, Validation, Mail, Scheduling), Flyway |
| Database | PostgreSQL 16 (truy vấn đệ quy `WITH RECURSIVE` cho nhánh con cháu / tổ tiên) |
| Frontend | Vue 3 (Composition API), Vite, Element Plus, Pinia, Vue Router |
| Lịch âm | Port thuật toán Hồ Ngọc Đức sang Java (múi giờ GMT+7), có unit test round-trip 1950–2100 |
| Triển khai | Docker Compose: `db` + `backend` + `frontend` (Nginx phục vụ SPA và proxy `/api`) |

## Chạy nhanh

```bash
cp .env.example .env      # sửa mật khẩu, JWT_SECRET... (tùy chọn khi chạy thử)
docker compose up -d --build
```

- Ứng dụng: http://localhost:8088
- API (gọi trực tiếp, bỏ qua Nginx): http://localhost:8081/api — PostgreSQL: `localhost:5433`
- Tài khoản Super Admin mặc định: `superadmin` / `Admin@123` (**đổi ngay** qua biến môi trường hoặc trang Hồ sơ)

### Chạy ở chế độ phát triển

```bash
docker compose up -d db                    # chỉ chạy PostgreSQL (cổng 5433)
cd backend && DB_URL=jdbc:postgresql://localhost:5433/giapha mvn spring-boot:run   # http://localhost:8080
cd frontend && npm install && npm run dev  # http://localhost:5173 (proxy /api -> 8080)
```

Chạy test backend: `cd backend && mvn test`

## Tính năng đã có

**Đa dòng họ & người dùng**
- Đăng ký / đăng nhập (JWT). Người dùng tự tạo dòng họ và trở thành **Tộc trưởng**.
- Dòng họ mới ở trạng thái *Chờ duyệt* cho đến khi Super Admin duyệt (tắt bằng `FAMILY_AUTO_APPROVE=true`).
- Tham gia bằng **mã mời / QR code** → gửi yêu cầu → Ban quản trị duyệt, chọn vai trò và gắn tài khoản vào đúng người trên phả hệ.

**Phân quyền (RBAC theo từng dòng họ)**

| Vai trò | Quyền |
|---|---|
| Super Admin (hệ thống) | Duyệt / khóa dòng họ, khóa tài khoản, chạy nhắc lịch thủ công |
| Tộc trưởng (OWNER) | Toàn quyền dòng họ, cấp/thu quyền Ban quản trị, xóa dòng họ |
| Ban quản trị (ADMIN) | Quản lý mọi thành viên, sự kiện, duyệt yêu cầu, phân quyền Trưởng chi / Thành viên / Khách |
| Trưởng chi (BRANCH_MANAGER) | Thêm/sửa con cháu **trong nhánh** được giao (tính bằng CTE đệ quy) và vợ/chồng của họ; quản lý sự kiện |
| Thành viên (MEMBER) | Xem toàn bộ, tự cập nhật hồ sơ của chính mình |
| Khách (VIEWER) | Chỉ xem; hồ sơ đánh dấu "không công khai" bị ẩn chi tiết |

**Cây phả hệ & hồ sơ**
- Sơ đồ cây trực quan: thu gọn/mở rộng nhánh, giới hạn số đời, xem từ một người bất kỳ, kéo để di chuyển, thu phóng.
- Bảng danh sách (tìm kiếm không dấu, lọc theo đời / còn sống / đã mất) và **bảng thế hệ**.
- Hồ sơ: tên húy/tự, đời thứ (tự tính theo cha mẹ), con thứ (trưởng/thứ), ngày sinh & ngày mất nhập theo **dương hoặc âm lịch** (tự quy đổi chiều còn lại), nơi an táng, quê quán, nghề nghiệp, tiểu sử, thành tựu.
- Hôn nhân: hỗ trợ **đa thê / tái hôn** (thứ tự vợ/chồng, trạng thái ly hôn/góa), đánh dấu con dâu / con rể (người ngoài họ).
- API lấy toàn bộ con cháu / tổ tiên: `GET /api/families/{id}/members/{memberId}/descendants|ancestors`.

**Hôn nhân & liên kết con cái** (`vn.giapha.marital`, bảng `marital_relations`)

| API | Mô tả |
|---|---|
| `GET /api/families/{fid}/members/{id}/spouses` | Các đời vợ/chồng theo `marriage_order`, kèm danh xưng (Vợ cả/Chính thất, Vợ hai/Kế thất…) và con chung |
| `POST /api/families/{fid}/members/{id}/spouses` | Gắn người có sẵn (`existingSpouseId`) hoặc tạo mới con dâu/con rể (`newSpouse`) trong một giao dịch |
| `PUT /api/families/{fid}/marital-relations/{rid}` | Đổi thứ tự, trạng thái `ACTIVE / DIVORCED / DECEASED`, ghi chú |
| `DELETE /api/families/{fid}/marital-relations/{rid}` | Gỡ quan hệ (`?removeSpouseOnlyMember=true` để xóa luôn dâu/rể không còn liên kết) |
| `POST /api/families/{fid}/children` | Thêm con, tự tính đời và "con thứ" |

Quy tắc (lỗi trả về `{code, message}`; 409 = trùng, 422 = vi phạm nghiệp vụ): mẹ phải là một đời vợ của cha (`PARENTS_NOT_MARRIED`), ít nhất cha hoặc mẹ thuộc huyết thống, không ghép hai người ngoại tộc, không kết hôn họ hàng trực hệ / anh chị em ruột, một người vợ chỉ có một hôn nhân `ACTIVE`, không trùng thứ tự kết hôn, không gỡ quan hệ đã có con chung. Quy tắc cha–mẹ áp dụng cho mọi API tạo/sửa thành viên (`ParentageValidator`).

**Thứ tự con cái** (`birth_order`, `SiblingOrderService`)
- Nhóm anh chị em = toàn bộ con của người cha thuộc huyết thống (mọi đời vợ); nếu cha là con rể thì theo người mẹ.
- Trong mỗi nhóm `birth_order` luôn liên tục 1..n: bỏ trống → xếp cuối (max + 1); nhập số → chèn vào vị trí, các con sau lùi xuống; xóa / đổi cha mẹ → tự dồn lại.
- `GET .../members/{parentId}/next-birth-order?spouseId=` gợi ý số tiếp theo; `GET .../members/{parentId}/children` danh sách đã sắp; `PUT .../members/{parentId}/children/order` đổi thứ tự (kéo thả trong hồ sơ).
- Danh xưng tự tính: *Con cả · Trưởng nam*, *Con thứ 2 · Trưởng nữ*, *Con út*, *Con một*.
- Mỗi dòng họ chọn cách hiển thị trên cây: theo thứ tự sinh, hoặc con trai trước (`child_sort_mode`).

**Quyền riêng tư** (`vn.giapha.privacy`)
- Mỗi thành viên: `is_visible_on_tree`, `is_public_profile`, `hide_birth_date`, `hide_death_date`, `hide_contact_info` (mặc định bật), `hide_bio`; thêm liên hệ `phone/email/address`.
- Ai thấy gì (`ViewerContext`):

| Người xem | Người bị ẩn khỏi cây | Trường chi tiết |
|---|---|---|
| Super Admin, Tộc trưởng, Ban quản trị, chính người đó, Trưởng chi trong nhánh | Thấy (vẽ mờ) | Thấy hết |
| Thành viên (MEMBER) | Không thấy | Thấy hết |
| Khách (VIEWER) | Không thấy | Hồ sơ không công khai → chỉ tên/đời/quan hệ; công khai → che theo từng cờ `hide_*` |

- Trường bị che trả `null` và được liệt kê trong `hiddenFields`. Người bị ẩn có con cháu còn hiện → thay bằng nút "Thành viên ẩn" để cây không đứt; xem trực tiếp → 404.
- Ngày giỗ tự động và nhắc lịch (in-app, email, Telegram) tôn trọng cùng quy tắc.
- `PATCH /api/families/{fid}/members/{id}/privacy` bật/tắt nhanh; thành viên tự chỉnh `hide_*` cho hồ sơ của mình, chỉ Ban quản trị / Trưởng chi được ẩn khỏi cây.
- **Chế độ người xem**: công tắc trên thanh công cụ cây phả hệ (URL `?as=viewer`). Frontend gửi header `X-View-As: VIEWER` với mọi request đọc; backend hạ quyền request đó xuống Khách ẩn danh nên dữ liệu trả về giống hệt tài khoản Khách thật. Header chỉ hạ quyền, không bao giờ nâng quyền, và vẫn yêu cầu là thành viên dòng họ.

**Xuất báo cáo / In** (`vn.giapha.export`, quyền: Ban quản trị trở lên)
- `GET /api/families/{fid}/export/pdf` và `/export/excel` với `fields=FULL_NAME,GENERATION,GENDER,BIRTH,DEATH,ROLE,PARENTS,SPOUSES,HOMETOWN,CONTACT,NOTE`, `sort=BRANCH|GENERATION`, `paper=A4|A3`, `landscape`, `publicView` (che như tài khoản Khách), `inline` (mở trong trình duyệt để in).
- PDF dùng OpenPDF + font Noto Serif nhúng sẵn (`resources/fonts`, giấy phép SIL OFL); Excel dùng Apache POI (cố định dòng tiêu đề, lọc tự động, thụt lề theo nhánh).
- Sơ đồ cây: `GET /api/families/{fid}/export/tree-pdf?paper=A4|A3|A2|A1|A0&landscape&publicView&rootId&maxDepth&showDates&inline` – vẽ ở máy chủ thành **PDF vector** (`vn.giapha.export.tree`: bố cục "tidy tree" + OpenPDF), không phụ thuộc trình duyệt, zoom hay cỡ chữ trên màn hình; in khổ lớn đến A0 vẫn nét.
- Cỡ chữ trên cây: nút A−/A+ (85%–150%) chỉ đổi cỡ chữ – bề rộng ô giữ nguyên (khác với zoom −/+), chữ to thì xuống dòng và ô cao thêm, đường nối tự vẽ lại.

Tài liệu API (Swagger): http://localhost:8088/api/swagger-ui.html

**Lịch âm & sự kiện**
- Chuyển đổi Âm ↔ Dương (có tháng nhuận, can chi), lịch tháng hiển thị ngày âm và sự kiện, chọn nhanh tháng / năm.
- Bộ chọn ngày âm lịch (`LunarDatePicker.vue`): năm + can chi → tháng (tháng nhuận đúng vị trí) → ngày (29/30 theo tháng đủ/thiếu), hoặc gõ `15/8/2026`, `1/2n/2023`. Dữ liệu từ `GET /api/calendar/lunar-year?year=`.
- Sự kiện dòng họ theo âm hoặc dương lịch, lặp hằng năm hoặc một lần (giỗ tổ, giỗ chi, họp họ, mừng thọ, khai xuân, khuyến học...).
- **Ngày giỗ tự động** sinh từ hồ sơ người đã mất – không cần nhập lại. Tháng thiếu (không có ngày 30) tự lùi về 29.

**Thông báo**
- Job chạy mỗi sáng 7:00 (`REMINDER_CRON`) gửi nhắc trước N ngày (mặc định `3,1`, cấu hình được cho từng sự kiện) + ngay trong ngày.
- Kênh: thông báo trong ứng dụng (luôn bật), **Email** (`MAIL_ENABLED=true` + SMTP), **Telegram** (`TELEGRAM_BOT_TOKEN` + chat ID nhóm của dòng họ). Có khóa chống gửi trùng.

## Cấu trúc mã nguồn

```
backend/src/main/java/vn/giapha/
  auth/          đăng ký, đăng nhập, hồ sơ tài khoản
  security/      JWT, filter, CurrentUser
  family/        dòng họ (tenant), vai trò, PermissionService (kiểm tra quyền tập trung)
  member/        thành viên phả hệ, hôn nhân, truy vấn đệ quy
  join/          yêu cầu tham gia
  event/         sự kiện, tính ngày diễn ra, ReminderScheduler, API lịch
  notification/  thông báo in-app / email / Telegram
  lunar/         thuật toán lịch âm
  admin/         API Super Admin
backend/src/main/resources/db/migration/   lược đồ CSDL (Flyway)
frontend/src/
  views/         các trang
  components/    cây phả hệ, hồ sơ thành viên, tab sự kiện/lịch/quản trị
```

## Hướng phát triển tiếp

- Quỹ dòng họ (thu/chi, danh sách công đức, quỹ khuyến học).
- Kho tư liệu số: upload ảnh, bản scan gia phả Hán/Nôm, văn tế (S3/MinIO).
- Quy trình **đề xuất chỉnh sửa** để Thành viên gửi thay đổi chờ Ban quản trị duyệt.
- Xuất cây phả hệ ra PDF/ảnh, nhập/xuất GEDCOM.
- Web Push, Zalo OA; cache cây phả hệ bằng Redis cho các dòng họ rất lớn.
- Refresh token, quên mật khẩu qua email, giới hạn tần suất đăng nhập.
