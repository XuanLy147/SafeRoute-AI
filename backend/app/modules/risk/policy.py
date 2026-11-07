"""Explicit versioned configuration; no implicit category-to-severity mapping."""
from pydantic import BaseModel, ConfigDict, Field, model_validator
from app.core.config import settings
from app.modules.risk.scoring import RiskPolicy

class PolicyConfig(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)
    revision: str = Field(min_length=1)
    approved: bool = False
    severity_weights: dict[str, float]
    pending_multiplier: float = Field(ge=0, le=1)
    radius_meters: float = Field(gt=0)
    medium_min: float = Field(gt=0)
    high_min: float = Field(gt=0)

    @model_validator(mode="after")
    def valid(self):
        self.core()
        if set(self.severity_weights) != {"LOW", "MEDIUM", "HIGH"}:
            raise ValueError("Explicit LOW/MEDIUM/HIGH severity weights required")
        if self.high_min <= self.medium_min:
            raise ValueError("Invalid thresholds")
        return self

    def core(self):
        return RiskPolicy(self.revision, self.severity_weights, self.pending_multiplier)

def load_policy():
    if not settings.RISK_POLICY_JSON:
        return None
    policy = PolicyConfig.model_validate_json(settings.RISK_POLICY_JSON)
    if settings.ENVIRONMENT.lower() == "production" and not policy.approved:
        raise ValueError("Production risk policy requires approval")
    return policy
