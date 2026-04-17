# MusicApp

Ứng dụng nghe nhạc desktop viết bằng **JavaFX + Maven**, hỗ trợ phát nhạc local và YouTube, quản lý playlist, queue, lời bài hát, dark/light mode và chế độ đắm chìm.

## Tính năng chính

- Phát nhạc local và nhạc từ YouTube
- Tìm kiếm bài hát trong thư viện
- Tìm kiếm bài hát trên Internet (YouTube)
- Tạo, đổi tên, xóa playlist
- Thêm / xóa bài hát khỏi playlist
- Queue phát nhạc
- Hiển thị và chỉnh sửa lời bài hát
- Giao diện Light Mode / Dark Mode
- Mini Player
- Immersive Mode
- Lưu trạng thái ứng dụng khi thoát

## Công nghệ sử dụng

- Java
- JavaFX
- Maven
- MySQL
- CSS
- FXML

## Yêu cầu môi trường

- **JDK 21**
- Maven Wrapper (`mvnw`, `mvnw.cmd`) đã có sẵn trong project
- MySQL Server
- Internet nếu muốn tìm và phát nhạc từ YouTube

## Cách chạy project

### 1. Chuyển sang JDK 21 trong PowerShell

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-21.0.10"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

### 2. Clean và compile

```powershell
.\mvnw.cmd clean compile
```

### 3. Chạy ứng dụng

```powershell
.\mvnw.cmd javafx:run
```

## Cấu trúc thư mục chính

```text
src/
 └── main/
     ├── java/
     │   └── com/musicapp/
     │       ├── dao/
     │       ├── database/
     │       ├── model/
     │       ├── service/
     │       ├── ui/
     │       └── util/
     └── resources/
         └── com/musicapp/ui/
             ├── main-view.fxml
             ├── musicapp.css
             ├── musicapp-dark.css
             ├── focus-mode.css
             └── immersive-mode.css
```

## Chức năng giao diện

- **Trang chính**: hiển thị danh sách bài hát, trình phát nhạc, queue, đề xuất
- **Sidebar**: quản lý bài hát và playlist
- **Khung bên phải**: hiển thị lời bài hát / thông tin bài hát
- **Header**: tìm kiếm local, tìm kiếm Internet, mở mini player, thống kê, bật chế độ đắm chìm

## Ghi chú

- Một số link YouTube có thể không phát được do lỗi trích xuất âm thanh hoặc giới hạn từ nguồn.
- Thư mục `cache/` dùng để lưu file tạm khi xử lý audio, không nên đưa lên GitHub.
- Project đã cấu hình `.gitignore` để bỏ qua các file cache và file âm thanh dung lượng lớn.

## Hướng phát triển

- Cải thiện độ ổn định khi phát nhạc từ YouTube
- Tối ưu hiệu năng ở chế độ đắm chìm
- Hoàn thiện phần metadata / lyrics
- Tối ưu UI/UX và xử lý lỗi tốt hơn
- Có thể mở rộng thêm chức năng đăng nhập hoặc đồng bộ dữ liệu

## Tác giả

- Sinh viên thực hiện project môn học Java / lập trình ứng dụng desktop
