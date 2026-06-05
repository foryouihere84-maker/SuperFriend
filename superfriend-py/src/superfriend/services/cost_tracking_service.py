from __future__ import annotations

import time
import structlog
from dataclasses import dataclass, field
from typing import Any
from datetime import date

logger = structlog.get_logger()


@dataclass
class ModelPricing:
    model_id: str
    input_price_per_1k: float = 0.001
    output_price_per_1k: float = 0.002


@dataclass
class SessionCost:
    total_tokens: int = 0
    input_tokens: int = 0
    output_tokens: int = 0
    total_cost_cents: int = 0
    api_calls: int = 0


@dataclass
class UserCost:
    total_cost_cents: int = 0
    total_tokens: int = 0
    api_calls: int = 0


@dataclass
class DailyCost:
    total_cost_cents: int = 0
    total_tokens: int = 0
    api_calls: int = 0


DEFAULT_PRICING = {
    "gpt-4": (0.03, 0.06),
    "gpt-4-turbo": (0.01, 0.03),
    "gpt-4o": (0.005, 0.015),
    "gpt-4o-mini": (0.00015, 0.0006),
    "gpt-3.5-turbo": (0.0005, 0.0015),
    "claude-3-opus": (0.015, 0.075),
    "claude-3-sonnet": (0.003, 0.015),
    "claude-3-haiku": (0.00025, 0.00125),
    "deepseek-chat": (0.00014, 0.00028),
    "deepseek-reasoner": (0.00055, 0.00219),
    "gemini-pro": (0.00025, 0.0005),
    "gemini-1.5-pro": (0.0035, 0.0105),
    "qwen-max": (0.0004, 0.0012),
    "default": (0.001, 0.002),
}


class CostTrackingService:
    def __init__(self):
        self._model_pricing: dict[str, ModelPricing] = {}
        self._session_costs: dict[str, SessionCost] = {}
        self._user_costs: dict[int, UserCost] = {}
        self._daily_costs: dict[date, DailyCost] = {}
        self._total_api_calls: int = 0
        self._total_tokens: int = 0
        self._total_cost_cents: int = 0

        for model_id, (inp, out) in DEFAULT_PRICING.items():
            self._model_pricing[model_id] = ModelPricing(model_id, inp, out)

    def record_usage(
        self,
        model: str,
        input_tokens: int,
        output_tokens: int,
        session_id: str | None = None,
        user_id: int | None = None,
    ) -> dict[str, Any]:
        pricing = self._model_pricing.get(model, self._model_pricing["default"])
        cost_usd = (input_tokens / 1000 * pricing.input_price_per_1k
                     + output_tokens / 1000 * pricing.output_price_per_1k)
        cost_cents = int(cost_usd * 100)

        self._total_api_calls += 1
        self._total_tokens += input_tokens + output_tokens
        self._total_cost_cents += cost_cents

        if session_id:
            sc = self._session_costs.setdefault(session_id, SessionCost())
            sc.total_tokens += input_tokens + output_tokens
            sc.input_tokens += input_tokens
            sc.output_tokens += output_tokens
            sc.total_cost_cents += cost_cents
            sc.api_calls += 1

        if user_id:
            uc = self._user_costs.setdefault(user_id, UserCost())
            uc.total_cost_cents += cost_cents
            uc.total_tokens += input_tokens + output_tokens
            uc.api_calls += 1

        today = date.today()
        dc = self._daily_costs.setdefault(today, DailyCost())
        dc.total_cost_cents += cost_cents
        dc.total_tokens += input_tokens + output_tokens
        dc.api_calls += 1

        return {
            "model": model,
            "input_tokens": input_tokens,
            "output_tokens": output_tokens,
            "cost_usd": cost_usd,
            "cost_cents": cost_cents,
        }

    def get_session_cost(self, session_id: str) -> dict[str, Any]:
        sc = self._session_costs.get(session_id)
        if not sc:
            return {"session_id": session_id, "total_cost_usd": 0, "total_tokens": 0, "api_calls": 0}
        return {
            "session_id": session_id,
            "total_cost_usd": sc.total_cost_cents / 100,
            "total_tokens": sc.total_tokens,
            "input_tokens": sc.input_tokens,
            "output_tokens": sc.output_tokens,
            "api_calls": sc.api_calls,
        }

    def get_user_cost(self, user_id: int) -> dict[str, Any]:
        uc = self._user_costs.get(user_id)
        if not uc:
            return {"user_id": user_id, "total_cost_usd": 0, "total_tokens": 0, "api_calls": 0}
        return {
            "user_id": user_id,
            "total_cost_usd": uc.total_cost_cents / 100,
            "total_tokens": uc.total_tokens,
            "api_calls": uc.api_calls,
        }

    def get_daily_cost(self, target_date: date | None = None) -> dict[str, Any]:
        target = target_date or date.today()
        dc = self._daily_costs.get(target)
        if not dc:
            return {"date": str(target), "total_cost_usd": 0, "total_tokens": 0, "api_calls": 0}
        return {
            "date": str(target),
            "total_cost_usd": dc.total_cost_cents / 100,
            "total_tokens": dc.total_tokens,
            "api_calls": dc.api_calls,
        }

    def get_summary(self) -> dict[str, Any]:
        return {
            "total_api_calls": self._total_api_calls,
            "total_tokens": self._total_tokens,
            "total_cost_usd": self._total_cost_cents / 100,
            "active_sessions": len(self._session_costs),
            "tracked_users": len(self._user_costs),
        }

    def set_model_pricing(self, model_id: str, input_price: float, output_price: float) -> None:
        self._model_pricing[model_id] = ModelPricing(model_id, input_price, output_price)


cost_tracking_service = CostTrackingService()