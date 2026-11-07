"""Audited administrator role assignments. Existing users and roles are preserved."""

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "0003_role_audit"
down_revision = "0002_image_owner_profile"
branch_labels = None
depends_on = None


def upgrade():
    role = postgresql.ENUM("USER", "REVIEWER", "ADMIN", name="user_role_enum", create_type=False)
    op.create_table(
        "role_audits",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("actor_id", sa.Integer(), sa.ForeignKey("users.id", ondelete="RESTRICT"), nullable=False),
        sa.Column("user_id", sa.Integer(), sa.ForeignKey("users.id", ondelete="RESTRICT"), nullable=False),
        sa.Column("from_role", role, nullable=False),
        sa.Column("to_role", role, nullable=False),
        sa.Column("reason", sa.String(500), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_role_audits_user_id", "role_audits", ["user_id"])


def downgrade():
    op.drop_index("ix_role_audits_user_id", table_name="role_audits")
    op.drop_table("role_audits")
