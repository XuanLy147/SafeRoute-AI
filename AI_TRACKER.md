# AI_TRACKER.md — Theo dõi tiến độ phát triển với hỗ trợ AI

**Dự án:** SafeRoute AI · **Nhóm:** COME FOR FOOD · **Hackathon:** SGU 2026–2027
**Kho mã:** `XuanLy147/SafeRoute-AI` · **Khởi tạo khung:** 07/11/2026 · **Nội dung gần nhất:** 08/11/2026
**Quy tắc:** Các bảng khởi tạo ở mục 2–5 được giữ làm lịch sử, không đại diện cho tiến độ hiện tại. Xem nhật ký và báo cáo mới nhất của đúng phạm vi để xác định kết quả. Không suy diễn kết quả biên dịch/kiểm thử từ cấu trúc kho mã.

**Kết quả gần nhất (mục 18):** 90 bài JVM và 208 bài backend + 12 kiểm thử con đạt; E2E điện thoại đạt ở lượt chạy lại. Các giới hạn chưa kiểm chứng vẫn được ghi rõ. Chuẩn hóa tài liệu không phải một lượt kiểm thử mới.

**Cách đọc lịch sử:** “Chưa commit/push” là trạng thái tại đợt được ghi, không phủ nhận commit được tạo ở đợt sau.

## 1. Hướng dẫn cập nhật

- `TODO`: chưa bắt đầu hoặc chưa xác minh đã làm.
- `IN_PROGRESS`: đang triển khai hoặc kiểm chứng thay đổi.
- `BLOCKED`: thiếu đặc tả API, quyền, SDK, khóa API hoặc phụ thuộc; ghi rõ trở ngại.
- `DONE_VERIFIED`: đã kiểm chứng trong phạm vi ghi rõ, có lệnh kiểm thử thực chạy và nhật ký hoặc bằng chứng trình diễn; ghi commit/PR nếu có, phân biệt thay đổi local chưa commit.
- Không điền số liệu mô hình, độ trễ/chi phí, URL đám mây hoặc kết quả PASS không có bằng chứng. Gắn liên kết PR/commit, nhật ký, ảnh kiểm thử **không chứa thông tin bí mật**.
- **AI bắt đầu ca mới:** đọc `AGENTS.md`, kiểm tra nhánh/`git status`, chọn việc đúng người phụ trách; tránh sửa hai nhánh không liên quan.
- Thêm dòng trong Nhật ký triển khai khi bắt đầu, bàn giao hoặc gặp trở ngại.

- Quy ước kết quả: `PASS` = đạt; `FAIL` = không đạt; `NOT_RUN` = chưa chạy; `NOT_VERIFIED`/`UNVERIFIED` = chưa đủ bằng chứng; `UNKNOWN` = chưa có thông tin; `UNCONFIRMED` = chưa xác nhận đặc tả.
- Giữ nguyên mã trạng thái, tên nhánh, tên API, định danh kỹ thuật và đường dẫn để đối chiếu.

## 2. Nhánh và người phụ trách — bản ghi khởi tạo

| Nhánh | Phụ trách | Phạm vi | Tình trạng |
|---|---|---|---|
| `main` | Nhóm/người phụ trách tích hợp | Mã đã rà soát/tích hợp | Bộ khung ban đầu |
| `frontend` | TV1, TV2 | Android Java/XML | Bộ khung ban đầu |
| `backend` | TV3, TV4, TV5 | FastAPI, cơ sở dữ liệu, AI, tìm đường, đám mây | Bộ khung ban đầu |

> Nhánh ở trạng thái khởi tạo không có nghĩa Android/FastAPI đã biên dịch thành công. Không tự sao chép mã từ `SafeRoute-AI_Demo` mà chưa kiểm tra và tạo PR.

## 3. Công việc P0 — trạng thái lúc khởi tạo, ưu tiên luồng chạy thật

| Mã | Phụ trách | Hạng mục / kết quả tối thiểu | Nhánh | Trạng thái | Bằng chứng | Trở ngại / phụ thuộc |
|---|---|---|---|---|---|---|
| TV1-01 | TV1 | Một dự án Android Studio Java/XML, ứng dụng khởi động trên máy thật | frontend | TODO | — | SDK/Gradle/bộ công cụ biên dịch |
| TV1-02 | TV1 | Bản đồ, ghim báo cáo, bộ lọc, xem trước, xử lý khi thiếu quyền vị trí | frontend | TODO | — | Khóa Google Maps; API báo cáo |
| TV1-03 | TV1 | Chọn A/B, xem các tuyến thật, đường vẽ và giải thích/nguy cơ | frontend | TODO | — | Phản hồi tìm đường TV5/TV3 |
| TV2-01 | TV2 | Chọn/chụp ảnh, GPS, xem gợi ý AI, sửa nhãn/nhập thủ công | frontend | TODO | — | Đặc tả AI TV4/TV3 |
| TV2-02 | TV2 | Gửi báo cáo chờ xác minh, báo cáo của tôi, chi tiết/bình chọn | frontend | TODO | — | API báo cáo/xác thực TV3 |
| TV2-03 | TV2 | Hàng đợi kiểm duyệt, duyệt/từ chối kèm lý do, giao diện theo quyền | frontend | TODO | — | API phân quyền/kiểm duyệt TV3 |
| TV3-01 | TV3 | FastAPI chạy được, `/health`, cấu hình không lộ bí mật | backend | TODO | — | Python/môi trường chạy |
| TV3-02 | TV3 | Schema PostGIS, migration, xác thực/RBAC, API báo cáo/bình chọn/duyệt | backend | TODO | — | Cơ sở dữ liệu/đặc tả |
| TV3-03 | TV3 | Xuất OpenAPI thật; kiểm thử lỗi/quyền API | backend | TODO | — | TV2/TV4/TV5 xác nhận đặc tả |
| TV4-01 | TV4 | Chốt trọng số v1.1, ánh xạ lớp, SHA/phiên bản, kiểm thử suy luận cơ bản | backend | TODO | — | Tệp mô hình/môi trường chạy |
| TV4-02 | TV4 | AI API: detections[]/bbox; hết thời gian chờ/rỗng/lỗi; bằng chứng đánh giá | backend | TODO | — | API do TV3 phụ trách; Android TV2 |
| TV5-01 | TV5 | Google Routes thật, giải mã polyline, tính nguy cơ bằng PostGIS | backend | TODO | — | Khóa Routes/thanh toán, cơ sở dữ liệu TV3 |
| TV5-02 | TV5 | Docker/Cloud Run, HTTPS, kiểm tra hoạt động, quản lý bí mật | backend | TODO | — | Quyền đám mây, backend chạy được |
| TV5-03 | TV5 | CI/CD biên dịch/lint/kiểm thử; E2E và bằng chứng độ trễ/chi phí | backend | TODO | — | Tất cả mô-đun |

## 4. Đặc tả API và quyết định liên nhóm — trạng thái khởi tạo

| Đặc tả | Nhóm xác nhận | Trạng thái | Liên kết/bằng chứng | Ghi chú |
|---|---|---|---|---|
| Xác thực/báo cáo/bình chọn/duyệt: endpoint, RBAC, trạng thái | TV2 + TV3 + TV1 | UNCONFIRMED | — | `PENDING_VERIFICATION`, `VERIFIED`, `REJECTED` |
| AI: multipart, 5 lớp, hệ tọa độ bbox, lỗi | TV2 + TV3 + TV4 | UNCONFIRMED | — | Không phát hiện → nhập thủ công |
| Tìm đường: điểm đầu/cuối, polyline, routes[], recommended_id, nguy cơ | TV1 + TV3 + TV5 | UNCONFIRMED | — | Chỉ dùng tuyến Google trả về, không tự dựng tuyến thay thế |
| Cơ sở dữ liệu: geography/geometry, vĩ độ/kinh độ, nhật ký duyệt, chỉ mục | TV3 + TV5 | UNCONFIRMED | — | Migration PostGIS là nguồn chuẩn |
| Android: package, MainActivity, điều hướng, Gradle, DTO dùng chung | TV1 + TV2 | UNCONFIRMED | — | Không tạo app/client trùng lặp |

## 5. Tiêu chí kiểm thử và minh chứng 100 điểm — mục tiêu khởi tạo

| Tiêu chí BTC | Điểm | Bằng chứng mục tiêu | Tình trạng |
|---|---:|---|---|
| Sáng tạo & đổi mới | 10 | So sánh giải pháp, lợi thế cụ thể, không tự tuyên bố tính mới | TODO |
| Tính thực tiễn | 10 | Chân dung người dùng, tình huống giao thông, thử nghiệm trên máy/địa bàn cụ thể | TODO |
| Dữ liệu & mô hình | 20 | Nguồn gốc/giấy phép, mAP/precision/recall thực, kiểm thử trường hợp biên, thiên lệch/an toàn | TODO |
| Kiến trúc & hoàn thiện | 20 | Tạo APK, kiểm thử API/cơ sở dữ liệu, UI/UX, CI/CD, bảo mật, E2E | TODO |
| Đóng góp cộng đồng | 10 | Hành trình người dùng, khả năng tiếp cận, quy trình kiểm duyệt, kế hoạch nhân rộng | TODO |
| AI tiết kiệm chi phí | 10 | Độ trễ, bộ nhớ và chi phí đo được trên môi trường đám mây | TODO |
| Đám mây AWS/Google | 10 | Triển khai HTTPS, URL kiểm tra hoạt động thật, nhật ký/cấu hình an toàn | TODO |
| Làm việc nhóm, trình diễn và phản biện | 10 | Phân công, PR, kịch bản trình diễn, hỏi đáp, bằng chứng phối hợp | TODO |

## 6. Nhật ký triển khai và bàn giao (cập nhật liên tục)

| Thời điểm (Asia/Ho_Chi_Minh) | Thành viên / trợ lý | Nhánh | Đã làm / tệp thay đổi | Kiểm thử (PASS/FAIL/NOT_RUN) | PR/Commit | Trở ngại và việc tiếp theo |
|---|---|---|---|---|---|---|
| 2026-11-07 | Nhóm khởi tạo | main | Tạo bộ khung `AGENTS.md`, `AI_TRACKER.md` | NOT_RUN (chỉ sửa tài liệu) | Xem lịch sử Git | TV1–TV5 bắt đầu từ đúng nhánh |

## 7. Nguyên tắc trình diễn

- **P0-A:** Ảnh/GPS → YOLO gợi ý hoặc [] → người dùng sửa → gửi báo cáo chờ xác minh → người kiểm duyệt quyết định → làm mới bản đồ.
- **P0-B:** Điểm A/B → Google Routes trả tuyến → PostGIS tính nguy cơ → hiển thị giải thích theo nguy cơ ghi nhận.
- Chỉ đánh dấu hoàn thành khi có bằng chứng trình diễn/kiểm thử. Dữ liệu giả lập phải ghi rõ; không trình bày dữ liệu giả lập như backend thật.
- Mọi thông tin còn chưa biết phải được ghi rõ `UNKNOWN` hoặc `UNVERIFIED`, không điền ước lượng như số đo thật.


## 8. Tích hợp toàn bộ bản phát hành — 07/11/2026

Kết quả ở mục này cập nhật trạng thái bộ khung phía trên.

| Hạng mục | Phụ trách | Trạng thái | Bằng chứng / điều kiện tiếp theo |
|---|---|---|---|
| Đối chiếu bản tích hợp | TV1–TV5 | DONE_VERIFIED | `3b70333`; khôi phục mã backend, giữ frontend; `docs/release/MERGE_PROVENANCE.md` |
| Tích hợp HTTP Android | TV1/TV2 | DONE_VERIFIED | Client dùng chung; 63 bài JVM, biên dịch/lint; 8 bài hồi quy thiết bị; E2E báo cáo/duyệt HTTP đạt; kiểm tra nhà cung cấp tách riêng |
| Kiểm chứng lại backend thật | TV3–TV5 | DONE_VERIFIED | 179 bài + 12 kiểm thử con đạt; migration/PostGIS, khởi động lại Docker, AI trên CPU, đặc tả; bằng chứng trong `docs/release` |
| E2E Android báo cáo/duyệt | TV1–TV5 | DONE_VERIFIED | Android/API/PostGIS/AI thật, bình chọn/duyệt/nhật ký/dữ liệu bản đồ và lưu dữ liệu sau khởi động lại đạt; `docs/release/ANDROID_BACKEND_E2E.md` |
| Kiểm tra dịch vụ Google | TV1/TV5 | DONE_VERIFIED | Khóa mới có giới hạn; Maps hiển thị trên API 35; Android/Docker/Google Routes thật đạt; `evidence/android-live-providers.txt` |
| CI tích hợp | TV1–TV5 | DONE_VERIFIED | `eacb0b9`: lượt Android `42373508154` và backend `42373508117` đạt |
| Phát hành lên main | Nhóm | BLOCKED | Kiểm tra nhà cung cấp đạt; còn yêu cầu rà soát PR/merge cuối cùng; main chưa đổi tại thời điểm ghi nhận |

Giữ thay đổi chưa commit trong worktree TV4 gốc và nhật ký lịch sử. Bằng chứng tích hợp mới nằm trong `docs/release`, tách khỏi các lượt chạy trước của từng thành viên.

## 9. Bổ sung xác thực/phân quyền tại local — 08/11/2026

Triển khai xác thực/phiên Android và quản lý quyền ADMIN trên `local/main-latest-20261107`.

- 72 bài JVM, biên dịch/lint đạt; 196 bài backend + 12 kiểm thử con, Ruff, migration hai chiều và OpenAPI đạt.
- Kiểm thử thiết bị: `NOT_VERIFIED` — đã sửa đối chiếu nhãn; lượt chạy lại bị treo và đã dừng. Cài APK đạt; cấp ADMIN đầu tiên đã được cho phép và ghi nhật ký.
- Chưa push. Xem [phạm vi, bằng chứng và giới hạn](docs/integration/auth-rbac-flow.md).

## 10. Rà soát nghiệp vụ theo quyền trên điện thoại — 08/11/2026

Trạng thái lúc rà soát: `IN_PROGRESS`; chưa đạt điều kiện chấp nhận phát hành (`FAIL`).

- Rà soát UI Xiaomi và backend local phát hiện 10 lỗi, gồm GET gặp EOF sau thời gian chờ và ảnh riêng tư trả 403 khiến USER rời chi tiết báo cáo công khai.
- Đã thực hiện tạo/bình chọn/duyệt/từ chối bằng dữ liệu QA và ADMIN cấp/thu hồi quyền/xem lịch sử; 27/27 kiểm tra HTTP bổ sung về quyền/trạng thái đạt.
- Camera/chọn ảnh bị chặn thao tác chạm bởi `INJECT_EVENTS`. Chưa sửa mã sản phẩm hoặc push trong đợt rà soát này.
- APK kiểm thử biên dịch đạt; bộ điều khiển QA chạy được 1 bài, không đồng nghĩa mọi nghiệp vụ đều đạt.
- Xem [lỗi tái hiện, giới hạn và dọn dữ liệu](docs/qa/DEVICE_ROLE_AUDIT_2026-11-08.md).

## 11. Khắc phục F01–F10 — 08/11/2026

Kết quả sửa các phát hiện ở mục 10: triển khai trên `local/main-latest-20261107`, giữ thay đổi chưa commit hiện có. Hoàn tất kiểm chứng local; chưa commit/push hoặc triển khai đám mây tại thời điểm này.

- Android: 77 bài JVM đạt; APK app/kiểm thử và lint đạt, 0 lỗi. Đã cài APK cuối lên Xiaomi.
- Backend: 206 bài + 12 kiểm thử con, Ruff, migration hai chiều và OpenAPI đạt.
- Thiết bị: kiểm chứng khôi phục GET sau thời gian chờ, ảnh riêng tư 403, lưu bình chọn của người dùng, lịch sử/gợi ý trùng cho người duyệt; ADMIN xem mọi trạng thái/bộ lọc/số liệu/cấu hình/lịch sử; cập nhật quyền cũ và giải thích/thời gian dự kiến tuyến Google thật.
- Xóa báo cáo QA có kiểm tra chủ sở hữu/tiền tố; dữ liệu và quyền gốc không đổi. Đã dọn phiên QA và bộ điều khiển kiểm thử.
- Giới hạn: cấu hình chỉ đọc; báo cáo trùng là gợi ý, không tự gộp; nhật ký hệ thống bao phủ quyết định duyệt và đổi quyền. E2E thao tác camera/chọn ảnh hệ thống chưa kiểm chứng; không phải xác nhận sẵn sàng production.

[Báo cáo sửa lỗi](docs/qa/DEVICE_ROLE_FIXES_2026-11-08.md).

## 12. Kiểm thử hồi quy tổng thể — 08/11/2026

**Lúc bắt đầu — IN_PROGRESS:** tái hiện lỗi vòng đời/đồng thời trước khi sửa; giữ thay đổi hiện có, cơ sở dữ liệu và quyền. Không nâng thư viện, đặt lại schema, tự động thử lại thao tác ghi hoặc push. Yêu cầu kiểm thử hồi quy và chạy lại bộ kiểm thử/thiết bị; ghi rõ phần chưa xác minh.

**Kết quả:** hoàn tất kiểm chứng local, không phải chứng nhận production; chưa commit/push trong đợt này.

- R01: ADMIN kẹt trạng thái tải sau khi tạo lại view.
- R02: mất chế độ xem lịch sử.
- R03: `ApiCalls` bắt đầu thao tác ghi khi đối tượng quản lý vòng đời đã bị hủy.
- R04: người duyệt đã bị thu hồi quyền vẫn ghi quyết định sau khi chờ khóa báo cáo.
- Thêm 3 bài JVM và 2 bài đồng thời trên PostGIS thật; đã ghi nhận thất bại trước khi sửa.
- 80 bài JVM; 208 bài backend + 12 kiểm thử con; biên dịch/lint/Ruff/migration/OpenAPI đạt.
- E2E máy thật trong môi trường riêng đạt: USER đăng ký, ảnh tổng hợp → AI/tải ảnh/báo cáo/bình chọn thật; REVIEWER đăng nhập bằng mật khẩu/duyệt/bản đồ; Maps/Routes thật; ADMIN đăng nhập/xem màn hình/tạo lại Activity/đăng xuất; lưu dữ liệu sau khởi động lại backend.
- Lượt `ActivityScenario` đầu bị treo và đã dừng; bộ kiểm thử vòng đời `physicalDevice` chỉ dùng cho QA đạt ở lượt chạy lại.
- Đã dọn tài nguyên, khôi phục ánh xạ USB backend local; không sửa dữ liệu/quyền thật. Camera/chọn ảnh hệ thống vẫn `NOT_VERIFIED`.

[Giới hạn, phạm vi và bằng chứng](docs/qa/COMPREHENSIVE_REGRESSION_2026-11-08.md).

## 13. Kiểm tra và sửa bản nháp báo cáo — 08/11/2026

**Lúc bắt đầu — IN_PROGRESS:** tái hiện dữ liệu biểu mẫu/ảnh/AI cũ sau khi bỏ nháp hoặc phân tích lại thất bại. Giữ cơ chế chặn gửi trùng khi chưa rõ kết quả, không sửa dữ liệu thật hoặc push.

**Kết quả:** hoàn tất kiểm chứng local; chưa commit/push.

- R05: mô tả/loại/mức độ cũ còn lại sau khi bỏ nháp.
- R06: ảnh cùng URI không tải lại sau khi bỏ nháp.
- R07: trình sửa AI/phiên bản mô hình/xác nhận cũ còn lại khi phân tích lại thất bại.
- R08: ảnh trước vẫn hiển thị sau khi thay bằng ảnh không đọc được.
- Phạm vi mã sản phẩm: chỉ `ReportWizardFragment`. Bốn trường hợp tái hiện lỗi đã đạt; 84 bài JVM, tạo APK và lint đạt.
- E2E Xiaomi riêng cho USER/REVIEWER/ADMIN, Maps/Routes thật và lưu dữ liệu sau khởi động lại đạt.
- Đã dọn dịch vụ tạm; không sửa cơ sở dữ liệu/tài khoản thật. Bộ kiểm thử backend không đổi và không chạy lại trong đợt này.
- Camera/chọn ảnh hệ thống và EXIF chưa kiểm chứng đầy đủ.

[Báo cáo bản nháp](docs/qa/REPORT_DRAFT_REGRESSION_2026-11-08.md).

## 14. Ghép giao diện frontend — 08/11/2026

- Merge local `origin/frontend` tại `9f49601` vào `integration/frontend-ui-preserve-fixes-20261108`, sau mốc phục hồi `5830b26`.
- Giữ giao diện nhận về và các sửa lỗi xác thực/RBAC/báo cáo đã kiểm chứng; xử lý 3 xung đột, chuyển đổi chỉ số loại nguy cơ của nháp cũ, hủy tìm địa chỉ cũ khi bỏ nháp.
- 88 bài JVM, APK/lint; 208 bài backend + 12 kiểm thử con, Ruff/migration/OpenAPI; E2E Xiaomi riêng cho USER/REVIEWER/ADMIN, Maps/Routes thật và lưu dữ liệu sau khởi động lại đều đạt. Đã xem ảnh bản đồ mới.
- Chưa push; không sửa dữ liệu/quyền thật hoặc triển khai lại dịch vụ backend. Camera/chọn ảnh và EXIF chưa kiểm chứng.

[Báo cáo merge](docs/integration/FRONTEND_UI_MERGE_2026-11-08.md).

## 15. Việt hóa tên nguy cơ — 08/11/2026

- `DONE_VERIFIED`: chỉ đổi hiển thị Android TV1/TV2. `HazardLabel` ánh xạ D00/D10/D20/D40/Repair cho tiêu đề bản đồ, danh sách/chi tiết báo cáo, kiểm duyệt và nhãn/trình sửa AI; nhãn mẫu XML cũng bỏ mã kỹ thuật.
- Giữ trường API, mã lựa chọn, xác thực và backend.
- 88 bài JVM đạt, 0 thất bại/lỗi; APK/lint đạt, `adb install -r` thành công.
- Bằng chứng local được Git bỏ qua: `android/build/reports/qa-roles/hazard-labels.log` và `android/build/reports/qa-roles/hazard-labels-final.log`.
- Không chạy E2E nghiệp vụ máy thật mới cho riêng thay đổi câu chữ này; chưa commit/push.

## 16. Khung cảnh báo thu gọn trên bản đồ — 08/11/2026

- `DONE_VERIFIED` trong phạm vi Android tự động: giữ kiểu thẻ, thêm vùng mũi tên 48dp có mô tả hỗ trợ tiếp cận; chạm hoặc vuốt lên để mở, vuốt xuống để thu gọn.
- Số cảnh báo/nút làm mới vẫn hiện; bộ lọc/danh sách ẩn cùng nhau. Giữ trạng thái qua tạo lại Activity và làm mới; danh sách cao tối đa 30% màn hình, cuộn riêng.
- 90 bài JVM đạt, gồm 2 bài mới về tương tác/khôi phục; APK và lint đạt. Cài app thành công.
- Ảnh `panel-check.png` xác nhận trạng thái thu gọn và bản đồ thoáng hơn. Xiaomi chặn vuốt tự động bằng `INJECT_EVENTS`; thao tác vuốt mở trên máy thật còn `NOT_VERIFIED`, kiểm thử vuốt JVM đạt.
- Không đổi xác thực/API, chưa commit/push. Bằng chứng local được Git bỏ qua: `android/build/reports/qa-roles/hazard-panel.log` và `panel-check.png` cùng thư mục.

## 17. Tinh gọn bố cục bản đồ — 08/11/2026

- `DONE_VERIFIED`: bỏ ba nút lọc loại trùng lặp; màn bộ lọc hiện có vẫn giữ lựa chọn, kể cả khi bật chỉ xem cảnh báo đã xác minh.
- Ẩn dòng chú thích khi bản đồ sẵn sàng; giữ thông báo dự phòng vị trí. Chuyển hàng nút vị trí/tìm đường lên trên khung cảnh báo thu gọn.
- 90 bài JVM, APK/lint đạt; cài APK mới lên Xiaomi và xem ảnh khung mở với báo cáo thật.
- Bằng chứng local được Git bỏ qua: `android/build/reports/qa-roles/map-layout-cleanup.log` và `map-layout-cleanup.png` cùng thư mục.
- Không đổi xác thực/backend, chưa commit/push.

## 18. Kiểm thử hồi quy và câu chữ UI/UX — 08/11/2026

- `DONE_VERIFIED` trong phạm vi báo cáo: 90 bài JVM, APK/lint (0 lỗi), 208 bài backend + 12 kiểm thử con, Ruff/migration/OpenAPI đạt.
- E2E Xiaomi lần đầu thất bại ở điều kiện app phải ở phía trước; lượt chạy lại USER/REVIEWER/ADMIN, Maps/Routes thật và lưu dữ liệu sau khởi động lại đạt.
- Siết kiểm thử ADMIN để yêu cầu đúng hàng báo cáo. Ghi trước/sau cho 11 cải thiện hiển thị/câu chữ, gồm mức độ bằng tiếng Việt, bỏ suy diễn đường thông thoáng và thông báo dịch vụ lỗi thời.
- Đã xem ảnh, dọn tài nguyên tạm; không sửa dữ liệu/quyền thật hoặc push.
- Giới hạn: thao tác camera/chọn ảnh, EXIF, cỡ chữ lớn, TalkBack, máy tính bảng và giao diện tối chưa được bao phủ đầy đủ.

[Báo cáo UI/UX](docs/qa/UI_UX_REGRESSION_2026-11-08.md).
