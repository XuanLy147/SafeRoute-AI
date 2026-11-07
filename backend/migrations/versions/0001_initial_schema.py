"""initial schema with postgis and 8 core tables

Revision ID: 0001_initial_schema
Revises: 
Create Date: 2026-11-07 14:00:00.000000

"""
from typing import Sequence, Union
from alembic import op
import sqlalchemy as sa
from geoalchemy2 import Geography

revision: str = '0001_initial_schema'
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

def upgrade() -> None:
    # 1. Kích hoạt PostGIS extension
    op.execute("CREATE EXTENSION IF NOT EXISTS postgis;")

    # 2. Tạo bảng users
    op.create_table(
        'users',
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('email', sa.String(length=255), nullable=False),
        sa.Column('password_hash', sa.String(length=255), nullable=False),
        sa.Column('role', sa.Enum('USER', 'REVIEWER', 'ADMIN', name='user_role_enum'), nullable=False),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_users_id'), 'users', ['id'], unique=False)
    op.create_index(op.f('ix_users_email'), 'users', ['email'], unique=True)

    # 3. Tạo bảng reports
    op.create_table(
        'reports',
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('user_id', sa.Integer(), nullable=False),
        sa.Column('category_user', sa.String(length=50), nullable=False),
        sa.Column('status', sa.Enum('PENDING_VERIFICATION', 'VERIFIED', 'REJECTED', name='report_status_enum'), nullable=False),
        sa.Column('severity', sa.String(length=20), nullable=False),
        sa.Column('location', Geography(geometry_type='POINT', srid=4326, spatial_index=False), nullable=False),
        sa.Column('observed_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('description', sa.Text(), nullable=True),
        sa.ForeignKeyConstraint(['user_id'], ['users.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_reports_id'), 'reports', ['id'], unique=False)
    op.create_index(op.f('ix_reports_status'), 'reports', ['status'], unique=False)
    op.create_index('idx_reports_location', 'reports', ['location'], postgresql_using='gist')

    # 4. Tạo bảng report_images
    op.create_table(
        'report_images',
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('report_id', sa.Integer(), nullable=True),
        sa.Column('object_key', sa.String(length=512), nullable=False),
        sa.Column('mime', sa.String(length=100), nullable=False),
        sa.Column('width', sa.Integer(), nullable=True),
        sa.Column('height', sa.Integer(), nullable=True),
        sa.ForeignKeyConstraint(['report_id'], ['reports.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id'),
        sa.UniqueConstraint('object_key')
    )
    op.create_index(op.f('ix_report_images_id'), 'report_images', ['id'], unique=False)

    # 5. Tạo bảng ai_predictions
    op.create_table(
        'ai_predictions',
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('report_id', sa.Integer(), nullable=True),
        sa.Column('model_version', sa.String(length=100), nullable=False),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(['report_id'], ['reports.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_ai_predictions_id'), 'ai_predictions', ['id'], unique=False)

    # 6. Tạo bảng ai_detections
    op.create_table(
        'ai_detections',
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('prediction_id', sa.Integer(), nullable=False),
        sa.Column('class_code', sa.String(length=50), nullable=False),
        sa.Column('confidence', sa.Float(), nullable=False),
        sa.Column('bbox_xyxy', sa.String(length=100), nullable=False),
        sa.ForeignKeyConstraint(['prediction_id'], ['ai_predictions.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_ai_detections_id'), 'ai_detections', ['id'], unique=False)

    # 7. Tạo bảng community_votes
    op.create_table(
        'community_votes',
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('report_id', sa.Integer(), nullable=False),
        sa.Column('user_id', sa.Integer(), nullable=False),
        sa.Column('vote', sa.Enum('SUPPORT', 'OPPOSE', 'UNSURE', name='vote_type_enum'), nullable=False),
        sa.Column('note', sa.Text(), nullable=True),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(['report_id'], ['reports.id'], ondelete='CASCADE'),
        sa.ForeignKeyConstraint(['user_id'], ['users.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id'),
        sa.UniqueConstraint('report_id', 'user_id', name='uq_vote_report_user')
    )
    op.create_index(op.f('ix_community_votes_id'), 'community_votes', ['id'], unique=False)

    # 8. Tạo bảng review_logs
    op.create_table(
        'review_logs',
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('report_id', sa.Integer(), nullable=False),
        sa.Column('actor_id', sa.Integer(), nullable=False),
        sa.Column('from_status', sa.Enum('PENDING_VERIFICATION', 'VERIFIED', 'REJECTED', name='report_status_enum'), nullable=False),
        sa.Column('to_status', sa.Enum('PENDING_VERIFICATION', 'VERIFIED', 'REJECTED', name='report_status_enum'), nullable=False),
        sa.Column('reason', sa.Text(), nullable=False),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(['actor_id'], ['users.id'], ondelete='RESTRICT'),
        sa.ForeignKeyConstraint(['report_id'], ['reports.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_review_logs_id'), 'review_logs', ['id'], unique=False)

    # 9. Tạo bảng risk_weights
    op.create_table(
        'risk_weights',
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('category', sa.String(length=50), nullable=False),
        sa.Column('weight', sa.Float(), nullable=False),
        sa.Column('severity', sa.String(length=20), nullable=False),
        sa.Column('verification_weight', sa.Float(), nullable=False),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_risk_weights_id'), 'risk_weights', ['id'], unique=False)

def downgrade() -> None:
    op.drop_table('risk_weights')
    op.drop_table('review_logs')
    op.drop_table('community_votes')
    op.drop_table('ai_detections')
    op.drop_table('ai_predictions')
    op.drop_table('report_images')
    op.drop_table('reports')
    op.drop_table('users')
    op.execute("DROP TYPE IF EXISTS vote_type_enum;")
    op.execute("DROP TYPE IF EXISTS report_status_enum;")
    op.execute("DROP TYPE IF EXISTS user_role_enum;")
