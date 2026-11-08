"""Deterministic synthetic-only tests for NM7 forensic probe.

No Navitel map files, proprietary content, or decryption material required.
"""
import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "scripts"))
import nm7_probe


class NM7ProbeTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = pathlib.Path(self.temp.name) / "synthetic.nm7"

    def test_trid_signature_and_nidxr(self):
        header = (
            nm7_probe.TRID_PREFIX
            + b"\x00" * (24 - len(nm7_probe.TRID_PREFIX))
            + b"NIDXR"
            + b"\x00" * 128
        )
        self.path.write_bytes(header)
        result = nm7_probe.inspect_nm7(self.path)
        self.assertTrue(result["signature"]["GNM3_at_0"])
        self.assertTrue(result["signature"]["TrID_12_bytes_at_0"])
        self.assertTrue(result["signature"]["NIDXR_at_24"])
        self.assertTrue(result["signature"]["likely_trid_family"])
        self.assertEqual(result["marker_offsets_first_1MiB"]["NIDXR"], [24])
        self.assertFalse(result["limits"]["geodata_exported"])
        self.assertEqual(result["limits"]["speed_limit_records_decoded"], 0)

    def test_trid_prefix_only_is_not_full_match(self):
        self.path.write_bytes(nm7_probe.TRID_PREFIX + b"\x00" * 60)
        result = nm7_probe.inspect_nm7(self.path)
        self.assertTrue(result["signature"]["TrID_12_bytes_at_0"])
        self.assertFalse(result["signature"]["likely_trid_family"])

    def test_marker_without_header_does_not_validate(self):
        self.path.write_bytes(b"x" * 24 + b"NIDXR" + b"\x00" * 30)
        result = nm7_probe.inspect_nm7(self.path)
        self.assertFalse(result["signature"]["likely_trid_family"])
        self.assertEqual(result["marker_offsets_first_1MiB"]["NIDXR"], [24])

    def test_empty_input_fails(self):
        self.path.write_bytes(b"")
        with self.assertRaises(ValueError):
            nm7_probe.inspect_nm7(self.path)

    def test_entropy_bounds(self):
        self.assertEqual(nm7_probe.shannon_entropy(b""), 0.0)
        self.assertEqual(nm7_probe.shannon_entropy(b"\x00" * 100), 0.0)
        self.assertAlmostEqual(nm7_probe.shannon_entropy(b"\x00\x01" * 50), 1.0)

    def test_sha256(self):
        self.path.write_bytes(b"abc")
        self.assertEqual(
            nm7_probe.inspect_nm7(self.path)["sha256"],
            "ba7816bf8f01cfea414140de5dae2223"
            "b00361a396177a9cb410ff61f20015ad",
        )


if __name__ == "__main__":
    unittest.main()
