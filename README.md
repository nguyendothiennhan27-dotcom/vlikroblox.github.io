# nor-Bench – vỏ Android

Vỏ Android mỏng: WebView chạy giao diện nor-Bench (`app/src/main/assets/index.html`) cộng cầu nối `NorNative` (Java)
cho tên chip, nhiệt độ, xung nhịp, thông tin máy, sao chép và lưu tệp.

## Cập nhật giao diện (từ điện thoại cũng làm được)
1. Vào kho GitHub, **Add file → Upload files**, tải `norbench-android.html` mới lên **thư mục gốc** của kho
   (ngang hàng với `build.gradle`, đúng tên `norbench-android.html`, ghi đè bản cũ), bấm **Commit changes**.
2. Tab **Actions** tự build (2 đến 3 phút). Xong, vào mục **Releases** (cột phải trang chính của kho) tải
   `nor-bench-<số>.apk` và cài đè lên bản cũ. Dữ liệu và lịch sử được giữ.

## Đổi biểu tượng
Thay `app/src/main/res/drawable-nodpi/ic_launcher_foreground.png` (PNG 432×432, nền trong suốt, hình đặt trong
vùng giữa khoảng 288×288) và/hoặc màu nền trong `app/src/main/res/values/colors.xml`.

## Khóa ký
`app/nor-bench.keystore` (mật khẩu `norbench`) giữ chữ ký cố định để cài đè được. Đây là khóa cho bản thử; khi phát hành
lên Google Play phải dùng khóa riêng, giữ bí mật, KHÔNG để trong kho công khai.
