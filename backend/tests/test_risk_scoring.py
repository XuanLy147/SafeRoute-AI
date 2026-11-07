"""MOCK_FOR_DEV: synthetic reports/weights, no real routes or measured risk."""

import unittest

from app.modules.risk.scoring import NearbyReport, RiskPolicy, RouteReports, score_routes


class RiskScoringTests(unittest.TestCase):
    def setUp(self):
        self.policy = RiskPolicy("MOCK_FOR_DEV-v1", {"low": 1, "high": 4}, 0)

    def score(self, *reports, policy=None):
        return score_routes((RouteReports("google-candidate-fixture", reports),), policy or self.policy)[0]

    def test_no_routes(self):
        self.assertEqual(score_routes((), self.policy), ())

    def test_no_reports_single_route(self):
        result = self.score()
        self.assertEqual(result.score, 0)
        self.assertEqual(result.contributions, ())
        self.assertEqual(result.route_id, "google-candidate-fixture")

    def test_verified_and_explanations(self):
        result = self.score(NearbyReport("a", "VERIFIED", "high"))
        self.assertEqual(result.score, 4)
        self.assertEqual(result.policy_revision, "MOCK_FOR_DEV-v1")
        self.assertEqual(result.contributions[0].report_id, "a")
        self.assertEqual(result.contributions[0].multiplier, 1)

    def test_pending_and_rejected_excluded(self):
        result = self.score(NearbyReport("a", "PENDING_VERIFICATION", "high"), NearbyReport("b", "REJECTED", "high"))
        self.assertEqual(result.score, 0)
        self.assertEqual(result.excluded_pending_count, 1)
        self.assertEqual(result.excluded_rejected_count, 1)

    def test_explicit_pending_weight_preserves_unverified_status(self):
        policy = RiskPolicy("MOCK_FOR_DEV-pending", {"high": 4}, 0.25)
        result = self.score(NearbyReport("a", "PENDING_VERIFICATION", "high"), policy=policy)
        self.assertEqual(result.score, 1)
        self.assertEqual(result.contributions[0].status, "PENDING_VERIFICATION")

    def test_duplicates_count_once(self):
        report = NearbyReport("a", "VERIFIED", "high")
        self.assertEqual(self.score(report, report).score, 4)
        rejected = NearbyReport("b", "REJECTED", "high")
        self.assertEqual(self.score(rejected, rejected).excluded_rejected_count, 1)

    def test_conflicting_duplicate_rejected(self):
        with self.assertRaises(ValueError):
            self.score(NearbyReport("a", "VERIFIED", "high"), NearbyReport("a", "REJECTED", "high"))

    def test_many_routes_preserve_order_and_shared_report(self):
        report = NearbyReport("shared", "VERIFIED", "low")
        routes = (RouteReports("b", (report,)), RouteReports("a", (report,)), RouteReports("c", ()))
        results = score_routes(routes, self.policy)
        self.assertEqual([r.route_id for r in results], ["b", "a", "c"])
        self.assertEqual([r.score for r in results], [1, 1, 0])

    def test_conflicting_snapshot_across_routes(self):
        with self.assertRaises(ValueError):
            score_routes(
                (
                    RouteReports("a", (NearbyReport("x", "VERIFIED", "low"),)),
                    RouteReports("b", (NearbyReport("x", "VERIFIED", "high"),)),
                ),
                self.policy,
            )

    def test_duplicate_route_rejected(self):
        with self.assertRaises(ValueError):
            score_routes((RouteReports("a", ()), RouteReports("a", ())), self.policy)

    def test_unknown_severity_not_silently_zero(self):
        with self.assertRaises(ValueError):
            self.score(NearbyReport("a", "VERIFIED", "unknown"))

    def test_invalid_status_and_identifiers(self):
        for args in (("a", "PENDING", "low"), ("", "VERIFIED", "low"), ("a", "VERIFIED", "")):
            with self.subTest(args=args), self.assertRaises(ValueError):
                NearbyReport(*args)
        with self.assertRaises(ValueError):
            score_routes((RouteReports(" ", ()),), self.policy)

    def test_invalid_policy(self):
        for weight in (-1, float("nan"), float("inf"), True, "4"):
            with self.subTest(weight=weight), self.assertRaises(ValueError):
                RiskPolicy("test", {"low": weight}, 0)
        for multiplier in (-1, 1.1, float("nan"), True):
            with self.subTest(multiplier=multiplier), self.assertRaises(ValueError):
                RiskPolicy("test", {"low": 1}, multiplier)
        with self.assertRaises(ValueError):
            RiskPolicy("test", {}, 0)

    def test_policy_defensively_copies_weights(self):
        weights = {"low": 1}
        policy = RiskPolicy("test", weights, 0)
        weights["low"] = 999
        self.assertEqual(policy.severity_weights["low"], 1)
        with self.assertRaises(TypeError):
            policy.severity_weights["low"] = 2

    def test_input_order_does_not_change_reasons(self):
        a, b = NearbyReport("a", "VERIFIED", "low"), NearbyReport("b", "VERIFIED", "high")
        self.assertEqual(self.score(a, b), self.score(b, a))

    def test_overflow_rejected(self):
        policy = RiskPolicy("test", {"high": 1e308}, 0)
        with self.assertRaises(ValueError):
            self.score(NearbyReport("a", "VERIFIED", "high"), NearbyReport("b", "VERIFIED", "high"), policy=policy)


if __name__ == "__main__":
    unittest.main()
