"""Explicit first-admin bootstrap; never runs during server startup.

Run: python -m app.bootstrap_admin --email registered@example.com
Requires an existing registered USER, no existing ADMIN, and interactive confirmation.
"""

import argparse

from sqlalchemy import select, text

from app.db.session import SessionLocal
from app.models.entities import RoleAudit, User, UserRole


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--email", required=True)
    args = parser.parse_args()
    with SessionLocal() as db:
        db.execute(text("SELECT pg_advisory_xact_lock(73920261108)"))
        if db.scalar(select(User.id).where(User.role == UserRole.ADMIN).limit(1)) is not None:
            raise SystemExit("An ADMIN already exists. Use the administrator app to assign roles.")
        user = db.scalar(select(User).where(User.email == args.email.strip()).with_for_update())
        if user is None:
            raise SystemExit("Register the account in the app first.")
        if input(f"Type the account email again to grant ADMIN to {user.email}: ").strip() != user.email:
            raise SystemExit("Cancelled; no changes.")
        db.add(
            RoleAudit(
                actor_id=user.id,
                user_id=user.id,
                from_role=user.role,
                to_role=UserRole.ADMIN,
                reason="Initial administrator bootstrap by server operator",
            )
        )
        user.role = UserRole.ADMIN
        db.commit()
        print("Initial administrator assigned and audited. Sign in again.")


if __name__ == "__main__":
    main()
