import pathlib
import shutil
import sys
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import sovereignty_gate

class SovereigntyGateTests(unittest.TestCase):
    def setup_project(self, temp):
        dst = pathlib.Path(temp)
        shutil.copytree(ROOT / "policy", dst / "policy")
        shutil.copytree(ROOT / ".github", dst / ".github")
        (dst / "app/src/main/java").mkdir(parents=True)
        (dst / "app/src/main/AndroidManifest.xml").write_text("<manifest/>")
        return dst

    def test_baseline_hud_has_no_map_output(self):
        self.assertEqual(sovereignty_gate.check(ROOT), [])

    def test_detects_unreviewed_map_renderer(self):
        with tempfile.TemporaryDirectory() as tmp:
            dst = self.setup_project(tmp)
            (dst / "app/src/main/java/MapActivity.kt").write_text("val renderer: MapView? = null")
            self.assertTrue(sovereignty_gate.check(dst))

    def test_detects_unreviewed_tile_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            dst = self.setup_project(tmp)
            (dst / "assets").mkdir()
            (dst / "assets/vietnam.pmtiles").write_bytes(b"tile")
            self.assertTrue(sovereignty_gate.check(dst))

    def test_detects_unreviewed_published_image(self):
        with tempfile.TemporaryDirectory() as tmp:
            dst = self.setup_project(tmp)
            (dst / "screenshots").mkdir()
            (dst / "screenshots/map.png").write_bytes(b"png")
            self.assertTrue(sovereignty_gate.check(dst))

    def test_detects_changed_label(self):
        with tempfile.TemporaryDirectory() as tmp:
            dst = self.setup_project(tmp)
            policy = dst / "policy/sovereignty-policy.json"
            policy.write_text(policy.read_text("utf-8").replace("Biển Đông", "East Sea"), encoding="utf-8")
            self.assertTrue(sovereignty_gate.check(dst))

    def test_detects_workflow_bypass(self):
        with tempfile.TemporaryDirectory() as tmp:
            dst = self.setup_project(tmp)
            (dst / ".github/workflows/bypass.yml").write_text("jobs: {build: {runs-on: ubuntu-latest}}")
            self.assertTrue(sovereignty_gate.check(dst))

if __name__ == "__main__":
    unittest.main()
