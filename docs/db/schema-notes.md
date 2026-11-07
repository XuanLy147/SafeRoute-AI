# DB schema notes — TV3

Dự kiến: users, reports, report_images, ai_detections, votes, review_logs. Dùng PostgreSQL/PostGIS geography/geometry và index GIST theo nhu cầu thực tế. Source of truth cuối cùng là Alembic migration thực chạy.
