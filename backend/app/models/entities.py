import enum
from datetime import datetime, timezone
from sqlalchemy import (
    Column, Integer, String, Float, Text, DateTime,
    ForeignKey, Enum, UniqueConstraint, Index
)
from sqlalchemy.orm import relationship
from geoalchemy2 import Geography
from app.db.session import Base

def utc_now():
    return datetime.now(timezone.utc)

# --- ENUMS CHUẨN HỢP ĐỒNG ---
class UserRole(str, enum.Enum):
    USER = "USER"
    REVIEWER = "REVIEWER"
    ADMIN = "ADMIN"

class ReportStatus(str, enum.Enum):
    PENDING_VERIFICATION = "PENDING_VERIFICATION"
    VERIFIED = "VERIFIED"
    REJECTED = "REJECTED"

class VoteType(str, enum.Enum):
    SUPPORT = "SUPPORT"
    OPPOSE = "OPPOSE"
    UNSURE = "UNSURE"

# 1. Bảng users
class User(Base):
    __tablename__ = "users"
    id = Column(Integer, primary_key=True, index=True)
    email = Column(String(255), unique=True, nullable=False, index=True)
    password_hash = Column(String(255), nullable=False)
    role = Column(Enum(UserRole, name="user_role_enum"), nullable=False, default=UserRole.USER)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)

# 2. Bảng reports (Có PostGIS Geography Point 4326 + GiST Index)
class Report(Base):
    __tablename__ = "reports"
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    category_user = Column(String(50), nullable=False)  # D00, D10, D20, D40, Repair hoặc thủ công
    status = Column(
        Enum(ReportStatus, name="report_status_enum"),
        nullable=False,
        default=ReportStatus.PENDING_VERIFICATION,
        index=True
    )
    severity = Column(String(20), nullable=False, default="MEDIUM")  # Tách biệt với confidence AI
    # Chú ý: Khi insert dùng WKTElement('POINT(lon lat)', srid=4326)
    location = Column(Geography(geometry_type="POINT", srid=4326, spatial_index=True), nullable=False)
    observed_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)
    description = Column(Text, nullable=True)

    images = relationship("ReportImage", back_populates="report", cascade="all, delete-orphan")
    predictions = relationship("AiPrediction", back_populates="report", cascade="all, delete-orphan")
    votes = relationship("CommunityVote", back_populates="report", cascade="all, delete-orphan")
    review_logs = relationship("ReviewLog", back_populates="report", cascade="all, delete-orphan")

# 3. Bảng report_images (Lưu metadata + object_key, không lưu blob)
class ReportImage(Base):
    __tablename__ = "report_images"
    id = Column(Integer, primary_key=True, index=True)
    report_id = Column(Integer, ForeignKey("reports.id", ondelete="CASCADE"), nullable=True)
    object_key = Column(String(512), nullable=False, unique=True)
    mime = Column(String(100), nullable=False)
    width = Column(Integer, nullable=True)
    height = Column(Integer, nullable=True)

    report = relationship("Report", back_populates="images")

# 4. Bảng ai_predictions (Lưu cả khi detections=[])
class AiPrediction(Base):
    __tablename__ = "ai_predictions"
    id = Column(Integer, primary_key=True, index=True)
    report_id = Column(Integer, ForeignKey("reports.id", ondelete="CASCADE"), nullable=True)
    model_version = Column(String(100), nullable=False)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)

    report = relationship("Report", back_populates="predictions")
    detections = relationship("AiDetection", back_populates="prediction", cascade="all, delete-orphan")

# 5. Bảng ai_detections (Lưu từng bounding box gốc của YOLO)
class AiDetection(Base):
    __tablename__ = "ai_detections"
    id = Column(Integer, primary_key=True, index=True)
    prediction_id = Column(Integer, ForeignKey("ai_predictions.id", ondelete="CASCADE"), nullable=False)
    class_code = Column(String(50), nullable=False)  # D00, D10, D20, D40, Repair
    confidence = Column(Float, nullable=False)
    bbox_xyxy = Column(String(100), nullable=False)  # Lưu chuỗi JSON "[x1, y1, x2, y2]"

    prediction = relationship("AiPrediction", back_populates="detections")

# 6. Bảng community_votes (Giới hạn 1 vote / report / user)
class CommunityVote(Base):
    __tablename__ = "community_votes"
    id = Column(Integer, primary_key=True, index=True)
    report_id = Column(Integer, ForeignKey("reports.id", ondelete="CASCADE"), nullable=False)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    vote = Column(Enum(VoteType, name="vote_type_enum"), nullable=False)
    note = Column(Text, nullable=True)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)

    __table_args__ = (
        UniqueConstraint("report_id", "user_id", name="uq_vote_report_user"),
    )
    report = relationship("Report", back_populates="votes")

# 7. Bảng review_logs (Append-only audit log cho REVIEWER / ADMIN)
class ReviewLog(Base):
    __tablename__ = "review_logs"
    id = Column(Integer, primary_key=True, index=True)
    report_id = Column(Integer, ForeignKey("reports.id", ondelete="CASCADE"), nullable=False)
    actor_id = Column(Integer, ForeignKey("users.id", ondelete="RESTRICT"), nullable=False)
    from_status = Column(Enum(ReportStatus, name="report_status_enum"), nullable=False)
    to_status = Column(Enum(ReportStatus, name="report_status_enum"), nullable=False)
    reason = Column(Text, nullable=False)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)

    report = relationship("Report", back_populates="review_logs")

# 8. Bảng risk_weights (Cấu hình trọng số cho TV5 tính Risk Score)
class RiskWeight(Base):
    __tablename__ = "risk_weights"
    id = Column(Integer, primary_key=True, index=True)
    category = Column(String(50), nullable=False)
    weight = Column(Float, nullable=False)
    severity = Column(String(20), nullable=False)
    verification_weight = Column(Float, nullable=False)
