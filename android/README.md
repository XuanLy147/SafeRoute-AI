# Android — TV1 và TV2

Một Android Studio project **Java + XML Views**. TV1 sở hữu app shell, Maps và Routing UI; TV2 sở hữu Auth, Report, Review và Retrofit client/DTO dùng chung.

Tạo project duy nhất **trong thư mục này**, không lồng repository Git và không tự sinh placeholder Java/XML không biên dịch được.

Package dự kiến: `com.comeforfood.saferoute` (chốt với nhóm). Min SDK dự kiến: 26. Phiên bản Gradle/AGP/SDK phải khóa sau khi Android Studio tạo project thật.

Thư mục mã nguồn dự kiến: `app/src/main/java/com/comeforfood/saferoute/{core,feature,data}`.
Feature: `map/`, `routing/`, `auth/`, `report/`, `reports/`, `review/`, `profile/`.
XML trong `app/src/main/res/layout/`, `navigation/`, `values/`.

**Không copy nguyên xi bộ skeleton rỗng từ demo để rồi coi như đã có ứng dụng chạy.** Nếu muốn sử dụng code demo hiện hữu, kiểm tra build rồi mang từng module sang theo PR.
