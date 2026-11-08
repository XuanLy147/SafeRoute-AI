# AI model & evaluation — TV4

Baseline: YOLOv12s, fine-tune v1.1; nhãn D00, D10, D20, D40, Repair. Không đánh đồng class Repair với tình trạng đã sửa chữa.

Cần ghi model provenance, class map, bản quyền/giấy phép dữ liệu, version + SHA256 của weights, môi trường chạy, ảnh test, false positives/negatives, latency và chi phí inference.
Không commit weights cá nhân chưa duyệt. Các số liệu trên bộ test 10 ảnh chỉ mang tính thử nghiệm nội bộ, không đại diện hiệu quả ngoài thực địa.
