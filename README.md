# Task Management System (Mini Jira)

Task Management System là một hệ thống quản lý công việc và dự án theo mô hình Agile/Kanban, được xây dựng dựa trên kiến trúc Spring Boot hiện đại, tích hợp hàng đợi xử lý bất đồng bộ ActiveMQ Artemis, hệ thống phân quyền đa tầng và báo cáo thống kê tiến độ.

---

## Mục lục

1. Giới thiệu tổng quan
2. Tính năng chính
3. Công nghệ sử dụng
4. Yêu cầu hệ thống
5. Hướng dẫn cài đặt và khởi chạy
6. Cấu hình biến môi trường
7. Tài khoản thử nghiệm mặc định
8. Cấu trúc thư mục dự án
9. Kiểm thử (Unit Testing)

---

## 1. Giới thiệu tổng quan

Dự án cung cấp giải pháp toàn diện cho việc quản lý vòng đời công việc trong các dự án phần mềm, bao gồm:
- Quản lý dự án, phân quyền thành viên theo từng dự án độc lập.
- Quản lý công việc (Issue) với định danh tự động (`PROJ-1`, `PROJ-2`), kiểm soát chuyển đổi trạng thái bằng máy trạng thái (State Machine).
- Xử lý gửi email thông báo và xác thực tài khoản bất đồng bộ thông qua hàng đợi tin nhắn JMS (Message Broker).
- Cơ chế bảo mật đa tầng: JWT kép (Access Token và Refresh Token với sliding expiration), Blacklist token khi đăng xuất, cơ chế chống tấn công Brute-force kèm Captcha.

---

## 2. Tính năng chính

### 2.1. Xác thực và Bảo mật
- **Đăng ký và xác thực email**: Gửi liên kết kích hoạt tài khoản có thời hạn qua email.
- **Quên và đặt lại mật khẩu**: Quy trình cấp lại mật khẩu an toàn qua token một lần (One-Time Token).
- **Cơ chế JWT kép**:
  - Access Token (thời hạn ngắn, mặc định 15 phút).
  - Refresh Token lưu trong database, hỗ trợ cơ chế gia hạn trượt (Sliding Expiration).
  - Vô hiệu hóa token (Token Blacklisting) khi người dùng đăng xuất.
- **Phòng chống Brute-force**:
  - Ghi nhận số lần đăng nhập thất bại theo IP/Tài khoản bằng Caffeine Cache.
  - Bắt buộc nhập Captcha sau 3 lần sai; khóa tạm thời tài khoản sau 5 lần liên tiếp.
- **Phân quyền đa tầng (RBAC)**:
  - Role hệ thống (System Roles): `ADMIN`, `USER`.
  - Role theo dự án (Project Roles): `PROJECT_MANAGER`, `DEVELOPER`, `TESTER`, `REPORTER`, `VIEWER`.

### 2.2. Quản lý Dự án (Project Management)
- Tạo, cập nhật, lưu trữ và xóa dự án.
- Tự động sinh mã tiền tố dự án (Project Key).
- Thêm thành viên vào dự án và phân quyền vai trò tương ứng.
- Cấu hình chính sách tự nhận việc (Self-Assign Policy: cho phép mọi người, chỉ nhận việc chưa có người, hoặc tắt tự nhận).
- Upload và hiển thị ảnh bìa dự án.

### 2.3. Quản lý Công việc (Issue Tracking & Board)
- **Tự động sinh mã công việc**: Định dạng `[PROJECT_KEY]-[ID]` có đồng bộ hóa luồng (thread-safe).
- **Máy trạng thái công việc (State Machine)**:
  - Vòng đời trạng thái: `TODO` -> `IN_PROGRESS` -> `REVIEW` -> `DONE`.
  - Kiểm soát luồng chuyển đổi hợp lệ (ngăn chặn thao tác nhảy cóc không hợp lệ).
  - Phân quyền nghiêm ngặt: Chỉ `PROJECT_MANAGER` hoặc `ADMIN` mới có quyền duyệt sang `DONE` hoặc mở lại từ `DONE`.
  - Ghi nhận thời gian tồn tại qua từng trạng thái và lịch sử chuyển lùi (Backward Transition Tracking).
- **Bộ lọc và phân trang**: Lọc theo trạng thái, mức độ ưu tiên, người phụ trách, nhãn dán, từ khóa tìm kiếm.
- **Giao diện làm việc**: Hỗ trợ cả hai chế độ xem dạng Bảng Kanban (Kanban Board) và Danh sách (List View).
- **Nhập/Xuất Excel**: Hỗ trợ xuất dữ liệu ra file Excel (.xlsx) và nhập hàng loạt công việc từ file mẫu (sử dụng Apache POI).
- **Đính kèm hình ảnh**: Cho phép tải lên nhiều hình ảnh cho một công việc với cơ chế kiểm tra định dạng MIME-type và giới hạn dung lượng.

### 2.4. Bình luận và Nhãn dán (Comments & Labels)
- Bình luận trao đổi theo từng công việc, hỗ trợ đính kèm hình ảnh minh họa.
- Quản lý nhãn dán (Label) phân loại công việc theo màu sắc.

### 2.5. Xử lý Bất đồng bộ và Email (JMS & ActiveMQ Artemis)
- Nhúng sẵn máy chủ tin nhắn ActiveMQ Artemis (Embedded Broker).
- Khi có sự kiện (giao việc, thêm thành viên, nhắc hạn công việc), hệ thống tạo sự kiện và đưa vào hàng đợi (`notification.queue`).
- **Chính sách thử lại và Dead Letter Queue (DLQ)**:
  - Cơ chế thử lại lũy tiến (Exponential Backoff): Thử lại tối đa 3 lần với thời gian chờ giãn cách (10s, 30s, 60s).
  - Khi cạn lượt thử (3/3 thất bại do lỗi mạng/SMTP), tin nhắn được chuyển vào Dead Letter Queue (`DLQ`) phục vụ kiểm tra và phát lại sự cố, không làm thất thoát dữ liệu.
- Định kỳ quét tự động nhắc hạn chót (Deadline Reminder Scheduler) và thử gửi lại thông báo lỗi (Failed Notification Resend Scheduler).

### 2.6. Báo cáo và Thống kê (Reports & Analytics)
- Thống kê tỷ lệ hoàn thành công việc theo từng dự án.
- Biểu đồ phân bổ trạng thái và độ ưu tiên.
- Phân tích thời gian chu kỳ (Cycle Time / Lead Time) và thống kê chuyển lùi công việc.

---

## 3. Công nghệ sử dụng

### Backend
- **Ngôn ngữ**: Java 21 (LTS)
- **Framework**: Spring Boot 3.4.5
  - Spring Data JPA (Hibernate 6)
  - Spring Security 6
  - Spring Validation
  - Spring Mail & Thymeleaf Engine
- **Xử lý bất đồng bộ**: Spring JMS, Apache ActiveMQ Artemis 2.37.0 (Embedded)
- **Xác thực**: JJWT (Java JWT) 0.12.6
- **Bộ nhớ đệm (In-memory Cache)**: Caffeine Cache
- **Xử lý tài liệu**: Apache POI 5.3.0 (Excel)
- **Ánh xạ đối tượng**: MapStruct 1.5.5, Project Lombok
- **Cơ sở dữ liệu**: MySQL 8.x
- **Build Tool**: Gradle 8.x / Gradle Wrapper

### Frontend
- HTML5, CSS3, JavaScript (ES6+ Vanilla)
- Giao diện tùy chỉnh (Custom Dashboard, Kanban Board)
- Hỗ trợ đa ngôn ngữ (i18n): Tiếng Việt và Tiếng Anh

### Kiểm thử
- JUnit 5, Mockito, H2 In-Memory Database (cho test profile)

---

## 4. Yêu cầu hệ thống

Trước khi cài đặt, đảm bảo máy chủ hoặc môi trường phát triển đã cài sẵn:
- **Java Development Kit (JDK)**: Phiên bản 21 trở lên.
- **MySQL Server**: Phiên bản 8.0 trở lên.
- **Git**: Phiên bản mới nhất.

---

## 5. Hướng dẫn cài đặt và khởi chạy

### Bước 1: Sao chép mã nguồn từ repository
```bash
git clone https://github.com/<your-username>/task-management-system.git
cd task-management-system
```

### Bước 2: Khởi tạo Cơ sở dữ liệu MySQL
1. Khởi động dịch vụ MySQL trên máy của bạn.
2. Thực thi script khởi tạo cơ sở dữ liệu `db.sql` nằm ở thư mục gốc:
```bash
mysql -u root -p < db.sql
```
*(Lệnh trên sẽ tự động tạo cơ sở dữ liệu `mini_jira` và toàn bộ cấu trúc bảng).*

3. (Tùy chọn) Nạp dữ liệu mẫu ban đầu để thử nghiệm:
Nếu muốn có sẵn dữ liệu dự án, công việc và các tài khoản mẫu, bạn có thể thực thi file:
```bash
mysql -u root -p mini_jira < src/main/resources/mock-data.sql
```

### Bước 3: Thiết lập biến môi trường
Sao chép file cấu hình mẫu `.env.example` thành `.env`:
```bash
cp .env.example .env
```
Mở file `.env` và điều chỉnh các thông số phù hợp với môi trường của bạn (thông tin gửi mail SMTP Gmail, JWT Secret, cấu hình kết nối DB).

### Bước 4: Biên dịch và chạy kiểm thử
Kiểm tra tính toàn vẹn của mã nguồn và các bài unit test:
- Trên Linux / macOS:
  ```bash
  ./gradlew test
  ```
- Trên Windows:
  ```powershell
  .\gradlew.bat test
  ```

### Bước 5: Khởi chạy ứng dụng
- Trên Linux / macOS:
  ```bash
  ./gradlew bootRun
  ```
- Trên Windows:
  ```powershell
  .\gradlew.bat bootRun
  ```

Khi ứng dụng khởi động thành công, truy cập giao diện qua trình duyệt:
```text
http://localhost:8080
```

---

## 6. Cấu hình biến môi trường

Các biến môi trường được hỗ trợ trong file `.env` hoặc hệ điều hành:

| Tên biến | Mô tả | Giá trị mặc định |
| :--- | :--- | :--- |
| `MAIL_HOST` | Địa chỉ máy chủ SMTP | `smtp.gmail.com` |
| `MAIL_PORT` | Cổng kết nối SMTP | `587` |
| `MAIL_USERNAME` | Tài khoản email gửi thông báo | `your-email@gmail.com` |
| `MAIL_PASSWORD` | Mật khẩu ứng dụng (App Password) của email | `your-app-password` |
| `JWT_SECRET` | Khóa bí mật dùng để ký và giải mã JWT (tối thiểu 256 bit) | Chuỗi khóa mặc định trong cấu hình |
| `JWT_EXPIRATION_MS` | Thời hạn của Access Token (mili giây) | `900000` (15 phút) |
| `DB_HOST` | Địa chỉ máy chủ MySQL | `localhost` |
| `DB_PORT` | Cổng kết nối MySQL | `3306` |
| `DB_NAME` | Tên cơ sở dữ liệu | `mini_jira` |
| `DB_USER` | Tên tài khoản MySQL | `root` |
| `DB_PASSWORD` | Mật khẩu MySQL | `123456` |
| `UPLOAD_DIR` | Thư mục lưu trữ hình ảnh trên đĩa cứng | `uploads` |

---

## 7. Tài khoản thử nghiệm mặc định

Nếu bạn nạp dữ liệu mẫu từ file `src/main/resources/mock-data.sql`, hệ thống có sẵn các tài khoản sau (tất cả dùng chung mật khẩu: `password123`):

| Email | Vai trò hệ thống | Vai trò dự án mẫu | Mô tả |
| :--- | :--- | :--- | :--- |
| `admin@example.com` | `ADMIN` | `PROJECT_MANAGER` | Quản trị viên toàn quyền hệ thống |
| `pm@example.com` | `USER` | `PROJECT_MANAGER` | Quản lý dự án, có quyền duyệt DONE công việc |
| `dev1@example.com` | `USER` | `DEVELOPER` | Lập trình viên chính |
| `dev2@example.com` | `USER` | `DEVELOPER` | Lập trình viên |
| `tester@example.com` | `USER` | `TESTER` | Kiểm thử viên |
| `locked@example.com` | `USER` | - | Tài khoản bị khóa (dùng kiểm tra bảo mật) |

---

## 8. Cấu trúc thư mục dự án

```text
task-management-system/
├── src/
│   ├── main/
│   │   ├── java/com/intern/taskmanagementsystem/
│   │   │   ├── config/          # Cấu hình Security, JMS Artemis, Web MVC, Bean Lifecycle
│   │   │   ├── controller/      # REST API Controllers (Auth, Project, Issue, Comment...)
│   │   │   ├── dto/             # Data Transfer Objects & Request/Response payloads
│   │   │   ├── entity/          # JPA Entities (User, Project, Issue, ChangeLog...)
│   │   │   ├── enums/           # Enum định nghĩa trạng thái, vai trò, loại thông báo
│   │   │   ├── exception/       # Global Exception Handler & Custom Business Exceptions
│   │   │   ├── mapper/          # MapStruct mappers chuyển đổi Entity <-> DTO
│   │   │   ├── messaging/       # JMS Producers, Consumers và Message Schemas
│   │   │   ├── repository/      # Spring Data JPA Repositories
│   │   │   ├── scheduler/       # Scheduled Cron Jobs (nhắc hạn, gửi lại thông báo lỗi)
│   │   │   ├── security/        # JWT Authentication Filter, UserDetails service
│   │   │   ├── service/         # Business Logic Services
│   │   │   └── util/            # Lớp tiện ích (Sinh Issue Key, DateUtils)
│   │   └── resources/
│   │       ├── static/          # Giao diện người dùng HTML/CSS/JS (Board, Dashboard...)
│   │       ├── templates/       # Thymeleaf email templates (kích hoạt, giao việc, đổi mật khẩu)
│   │       ├── application.yaml # Cấu hình ứng dụng chính
│   │       └── application-dev.yaml # Cấu hình môi trường phát triển (Dev profile)
│   └── test/                    # Bộ kiểm thử Unit Test và Integration Test
├── uploads/                     # Thư mục lưu trữ hình ảnh upload cục bộ (được gitignore)
├── .env.example                 # File mẫu khai báo biến môi trường
├── db.sql                       # DDL schema khởi tạo cơ sở dữ liệu MySQL
├── build.gradle                 # Cấu hình Gradle dependencies và build plugins
└── README.md                    # Tài liệu hướng dẫn dự án
```

---

## 9. Kiểm thử (Unit Testing)

Hệ thống bao gồm bộ kiểm thử tự động toàn diện kiểm tra các luồng nghiệp vụ phức tạp:
- Luồng chuyển trạng thái hợp lệ và bất hợp lệ của Issue State Machine.
- Kiểm tra phân quyền chuyển trạng thái sang `DONE` và phân rã bước lùi.
- Luồng gửi tin nhắn JMS và kiểm tra cơ chế Dead Letter Queue (DLQ) khi xảy ra lỗi mạng.
- Cơ chế khóa tài khoản và Captcha khi đăng nhập sai nhiều lần.
- Kiểm tra chính sách tự nhận việc (Self-Assign Policy) và bộ lọc công việc.

Để chạy toàn bộ bài kiểm tra:
```bash
./gradlew test
```
Báo cáo kiểm thử chi tiết ở định dạng HTML sẽ được sinh tại:
`build/reports/tests/test/index.html`
