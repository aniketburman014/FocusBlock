import unittest

from audit_dependencies import scan


class DependencyAuditTest(unittest.TestCase):
    def inventory(self):
        return {"artifacts": [
            {"scope": "runtime", "name": "example:library", "version": "1.0"},
            {"scope": "unit-test", "name": "example:library", "version": "1.0"},
        ]}

    def test_queries_resolved_versions_once_and_labels_scopes(self):
        def request(endpoint, payload=None):
            if endpoint == "querybatch":
                self.assertEqual(payload, {"queries": [{
                    "package": {"ecosystem": "Maven", "name": "example:library"},
                    "version": "1.0"}]})
                return {"results": [{"vulns": [{"id": "GHSA-example"}]}]}
            return {"id": "GHSA-example", "summary": "Example advisory"}
        report = scan(self.inventory(), request)
        self.assertEqual(report["queried_packages"], 1)
        self.assertEqual(report["status"], "findings")
        self.assertEqual(report["findings"][0]["scopes"], ["runtime", "unit-test"])

    def test_empty_or_missing_responses_do_not_pass(self):
        for response in [{}, {"results": []}, {"results": [None]},
                         {"results": [{"error": "Invalid query"}]}]:
            with self.assertRaises(ValueError):
                scan(self.inventory(), lambda *args: response)
        with self.assertRaises(ValueError):
            scan({"artifacts": []}, lambda *args: {})

    def test_no_matches_are_reported_without_a_safety_claim(self):
        report = scan(self.inventory(), lambda *args: {"results": [{}]})
        self.assertEqual(report["status"], "no-known-advisories-returned")
        self.assertEqual(report["findings"], [])

    def test_follows_pagination(self):
        calls = []
        def request(endpoint, payload=None):
            calls.append(payload)
            if len(calls) == 1:
                return {"results": [{"next_page_token": "next"}]}
            self.assertEqual(payload["queries"][0]["page_token"], "next")
            return {"results": [{}]}
        scan(self.inventory(), request)
        self.assertEqual(len(calls), 2)

    def test_repeated_pagination_token_is_rejected(self):
        with self.assertRaises(ValueError):
            scan(self.inventory(), lambda *args: {"results": [{"next_page_token": "same"}]})

    def test_api_failure_is_not_a_clean_result(self):
        def request(*args):
            raise OSError("API unavailable")
        with self.assertRaises(OSError):
            scan(self.inventory(), request)
