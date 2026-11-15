import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("release_version", Path(__file__).resolve().parents[1] / "release_version.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ReleaseVersionTest(unittest.TestCase):
    tags = [f"v1.0.{n}" for n in range(16, 21)]

    def choose(self, tags=None, releases=None, event="push", ref="refs/heads/main", name="", code=""):
        return module.choose_version(self.tags if tags is None else tags, self.tags if releases is None else releases, event, ref, name, code)

    def test_auto_after_existing_releases(self):
        self.assertEqual(self.choose(), (21, "1.0.21"))

    def test_reserved_tag_is_not_reused(self):
        self.assertEqual(self.choose(tags=self.tags + ["v1.0.21"]), (22, "1.0.22"))

    def test_manual_version_requires_monotonic_matching_code(self):
        self.assertEqual(self.choose(event="workflow_dispatch", name="1.0.30", code="30"), (30, "1.0.30"))
        for name, code in [("1.0.20", "20"), ("1.0.21", "22"), ("1.1.0", "21"), ("1.0.21", "020")]:
            with self.subTest(name=name, code=code), self.assertRaises(ValueError):
                self.choose(event="workflow_dispatch", name=name, code=code)

    def test_tag_must_be_new_and_latest(self):
        tags = self.tags + ["v1.0.21"]
        self.assertEqual(self.choose(tags=tags, event="push", ref="refs/tags/v1.0.21"), (21, "1.0.21"))
        with self.assertRaises(ValueError):
            self.choose(tags=tags, releases=tags, event="push", ref="refs/tags/v1.0.21")
        with self.assertRaises(ValueError):
            self.choose(tags=tags + ["v1.0.22"], event="push", ref="refs/tags/v1.0.21")

    def test_unknown_tag_or_version_limit_fails_closed(self):
        for tags in [self.tags + ["v2.0.0"], self.tags + ["v1.0.2100000001"]]:
            with self.assertRaises(ValueError):
                self.choose(tags=tags)
        with self.assertRaises(ValueError):
            self.choose(name="1.0.2100000001")


if __name__ == "__main__":
    unittest.main()
