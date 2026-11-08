# Chuẩn hóa tên và cấu trúc — 08/11/2026

## Phạm vi

Theo yêu cầu chủ dự án: chỉ đổi tên/đường dẫn, giữ nguyên logic, API, package Android và dữ liệu. Không deploy cloud.

| Cũ | Mới |
|---|---|
| android/ | frontend/ |
| ai/ | model ai/ |
| Tv1DataSource.java | MapRoutingDataSource.java |
| Tv1ViewModel.java | MapRoutingViewModel.java |
| Tv2Navigation.java | AppNavigation.java |
| Tv2Screens.java | FeatureScreens.java |
| Tv1UiTest.java | MapRoutingUiTest.java |
| Tv2IntegrationTest.java | AccountReportIntegrationTest.java |
| Tv1DeviceTest.java | MapRoutingDeviceTest.java |
| Tv2DeviceTest.java | AccountReportDeviceTest.java |

Các hàm/thuộc tính liên quan: openTv2Fragment → openFeatureFragment; getTv2Screens/setTv2Screens/tv2Screens → getFeatureScreens/setFeatureScreens/featureScreens.

Giữ AndroidManifest.xml, Gradle wrapper, tên thư viện/platform, package com.comeforfood.saferoute, endpoint /api/v1/ai và module backend/app/modules/ai. Tên tài liệu audit theo thành viên vẫn giữ để không mất ngữ cảnh lịch sử.

## Tham chiếu

- CI working-directory/artifact paths đổi sang frontend.
- Script QA, tài liệu và gitignore cập nhật tên mới.
- Docker COPY dùng JSON array để xử lý dấu cách trong model ai; đường dẫn nội bộ container /srv/ai giữ nguyên.
- Backend chỉ đổi đường dẫn mặc định tìm checkpoint.
- local.properties được chuyển cùng thư mục và vẫn Git-ignored. Không đưa .env, cấu hình IDE hoặc build outputs vào commit.

## Kiểm chứng

- Đối chiếu 173 file frontend với commit trước: chỉ khác đúng các identifier đã liệt kê; không thay thuật toán, điều kiện, tài nguyên hay cấu hình build.
- Backend service.py chỉ khác tên thư mục checkpoint.
- SHA-256 model giữ nguyên: ab42e303396857560995dd0fd4d54262015ef77851709220c2f20ca5fc6a4f4c.
- Android: 93 bài JVM PASS; APK, instrumentation APK và lint PASS. Log naming-final-android.log trong frontend/build/reports/qa-roles/.
- Docker build PASS; checkpoint/inference CPU thật với đường dẫn mặc định mới PASS; OpenAPI/examples PASS. Log folder-rename-docker.log và naming-ai-contract.log.
- Không chạy lại E2E nghiệp vụ điện thoại trong lượt đổi tên; có build instrumentation APK.
