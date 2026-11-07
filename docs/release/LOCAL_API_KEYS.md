# Cấu hình key và backend trên máy này

Bản main đã có FastAPI endpoints và Android HTTP client. Key riêng không được tải về từ GitHub. Không dán key vào chat hoặc commit hai file local dưới đây.

## Hai Google API cần dùng

| Dịch vụ | Biến | File | Trạng thái máy này |
|---|---|---|---|
| Maps SDK for Android | MAPS_API_KEY | android/local.properties | Đã có, giữ nguyên |
| Routes API | GOOGLE_ROUTES_API_KEY | backend/.env | Để trống cho người dùng điền |

Tạo hai key riêng trong Google Cloud Console → APIs & Services → Credentials. Bật Maps SDK for Android và Routes API trong project có billing hoạt động.
Maps key giới hạn Android app: package com.comeforfood.saferoute và SHA-1 của keystore thực tế trên máy build; không dùng SHA-1 từ máy người khác.
Routes key giới hạn Routes API và IP egress của server phù hợp; không đặt key server vào APK hoặc dùng Android-restricted key cho backend.
Hướng dẫn chính thức: [Maps SDK](https://developers.google.com/maps/documentation/android-sdk/get-api-key), [Routes API](https://developers.google.com/maps/documentation/routes/get-api-key).

## Điền và chạy local

1. Trong backend/.env, điền GOOGLE_ROUTES_API_KEY= rồi dán key sau dấu bằng. SECRET_KEY và POSTGRES_PASSWORD đã sinh ngẫu nhiên cho môi trường local mới; không cần lấy từ Google. DATABASE_URL dùng cùng mật khẩu cho PostGIS local. Nếu dùng database có sẵn, chỉnh mật khẩu/URL cho khớp; không xóa volume cũ.
2. Trong android/local.properties, giữ nguyên sdk.dir và MAPS_API_KEY. API_BASE_URL đã đặt http://127.0.0.1:8000/ cho điện thoại qua USB. Đây là địa chỉ server, không phải API key. Chỉ dùng origin, không thêm /api/v1.
3. Tại thư mục gốc SafeRoute-AI, sau khi mở Docker Desktop:
   ```powershell
   docker compose --env-file backend/.env -p saferoute-local -f backend/docker-compose.yml up --build -d
   & D:\Android\Sdk\platform-tools\adb.exe reverse tcp:8000 tcp:8000
   .\android\gradlew.bat -p android :app:assembleDebug
   ```
   Đây là lệnh hướng dẫn, chưa chạy khởi tạo database/server trong lượt tạo file.
4. Cài APK vừa build lên điện thoại. Mỗi lần kết nối lại USB có thể cần chạy lại adb reverse. Backend chạy ở http://127.0.0.1:8000; xem /health, /ready và /docs.
5. Nếu dùng Android Emulator, đổi API_BASE_URL thành http://10.0.2.2:8000/ rồi build lại. Nếu dùng staging, điền origin HTTPS thật.

Nếu chạy Python trực tiếp: vào backend, dùng môi trường Python 3.11 đã cài requirements/runtime, chạy migrations rồi `python -m uvicorn app.main:app --env-file .env --host 127.0.0.1 --port 8000`. Cần --env-file vì Routes adapter đọc environment variable; Pydantic Settings riêng không tự đưa Routes key vào os.environ.

Debug build đọc URL theo thứ tự: -PAPI_BASE_URL → biến môi trường API_BASE_URL → android/local.properties → mặc định emulator. Release vẫn yêu cầu -PAPI_BASE_URL=https://server-thật/; không tự lấy địa chỉ USB local.

## Những thứ không cần mua thêm API key

- Đăng nhập/đăng ký, báo cáo, upload ảnh, vote và review: API backend SafeRoute; app lấy JWT sau đăng nhập, không điền token tay.
- AI nhận diện: YOLO chạy trong backend với checkpoint/runtime, không dùng OpenAI/Gemini key.
- Tìm địa chỉ hiện dùng Android Geocoder, chưa yêu cầu Places API key riêng.
- POSTGRES_PASSWORD / DATABASE_URL: cấu hình database.
- SECRET_KEY: khóa ký JWT của backend, đã sinh local.
- RISK_POLICY_JSON: chính sách tính risk, không phải API key; giữ chưa cấu hình nếu chưa được duyệt. Có Routes key không đồng nghĩa đã có điểm risk khả dụng.

File .env và local.properties đều bị Git bỏ qua. Thay cấu hình cần restart backend/rebuild APK tương ứng. Lượt này chưa kiểm tra key Routes với Google vì người dùng chưa điền.
