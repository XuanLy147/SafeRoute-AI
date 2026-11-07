"""Persistent upload ownership and Android profile; legacy rows remain private."""
from alembic import op
import sqlalchemy as sa
revision = "0002_image_owner_profile"
down_revision = "0001_initial_schema"
branch_labels = None
depends_on = None

def upgrade():
    op.add_column("users", sa.Column("full_name", sa.String(200), nullable=True))
    op.add_column("report_images", sa.Column("owner_id", sa.Integer(), nullable=True))
    op.create_foreign_key("fk_image_owner", "report_images", "users", ["owner_id"], ["id"], ondelete="RESTRICT")
    op.execute("UPDATE report_images i SET owner_id=r.user_id FROM reports r WHERE i.report_id=r.id")

def downgrade():
    op.drop_constraint("fk_image_owner", "report_images", type_="foreignkey")
    op.drop_column("report_images", "owner_id")
    op.drop_column("users", "full_name")
