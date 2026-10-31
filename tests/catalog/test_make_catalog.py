import contextlib
import filecmp
import io
import json
import os
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import make_catalog  # noqa: E402

REPO = os.path.dirname(os.path.dirname(HERE))
SAMPLE = os.path.join(HERE, "sample", "HKGPM", "catalogs", "sample")
SCHEMA = os.path.join(REPO, "docs", "catalog", "catalog.schema.json")


def files_under(root):
    out = []
    for d, _, names in os.walk(root):
        for n in names:
            out.append(os.path.relpath(os.path.join(d, n), root))
    return sorted(out)


class MakeCatalogTest(unittest.TestCase):
    def test_sample_rebuilds_byte_for_byte(self):
        with tempfile.TemporaryDirectory() as tmp:
            make_catalog.build_sample(tmp)
            built = os.path.join(tmp, "HKGPM", "catalogs", "sample")
            self.assertEqual(files_under(SAMPLE), files_under(built))
            for rel in files_under(SAMPLE):
                self.assertTrue(
                    filecmp.cmp(os.path.join(SAMPLE, rel), os.path.join(built, rel),
                                shallow=False), rel)

    def test_openssl_verifies_sample(self):
        r = subprocess.run(
            ["openssl", "dgst", "-sha256", "-verify", "key.pem",
             "-signature", "index.json.sig", "index.json"],
            cwd=SAMPLE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.assertEqual(0, r.returncode, r.stdout + r.stderr)

    def test_schema_names_every_field_the_sample_uses(self):
        with open(SCHEMA) as f:
            schema = json.load(f)
        with open(os.path.join(SAMPLE, "index.json")) as f:
            index = json.load(f)
        top = schema["properties"]
        entry = schema["$defs"]["entry"]["properties"]
        compat = schema["$defs"]["compatibility"]["properties"]
        rule = schema["$defs"]["rule"]["properties"]
        self.assertLessEqual(set(index), set(top))
        for e in index["packages"]:
            self.assertLessEqual(set(e), set(entry))
            c = e.get("compatibility", {})
            self.assertLessEqual(set(c), set(compat))
            for side in ("works_on", "not_on"):
                for r in c.get(side, []):
                    self.assertLessEqual(set(r), set(rule))

    def test_fingerprint_matches_index_key(self):
        with tempfile.TemporaryDirectory() as tmp:
            fp = make_catalog.build_sample(tmp)
        with open(os.path.join(SAMPLE, "index.json")) as f:
            self.assertEqual(fp, json.load(f)["key"])

    def test_car_catalogs_have_expected_states(self):
        def verify(folder):
            return subprocess.run(
                ["openssl", "dgst", "-sha256", "-verify", "key.pem",
                 "-signature", "index.json.sig", "index.json"],
                cwd=folder, stdout=subprocess.PIPE, stderr=subprocess.PIPE).returncode

        with tempfile.TemporaryDirectory() as tmp:
            out = os.path.join(tmp, "test-catalogs")
            self.assertEqual(0, make_catalog.main(["make_catalog.py", "car", out]))
            with open(os.path.join(out, "stale"), "w") as f:
                f.write("x")
            self.assertEqual(0, make_catalog.main(["make_catalog.py", "car", out]))
            self.assertFalse(os.path.exists(os.path.join(out, "stale")))
            cat = os.path.join("HKGPM", "catalogs", "cartest")
            trusted = os.path.join(out, "trusted", cat)
            self.assertEqual(0, verify(trusted))
            self.assertEqual(0, verify(os.path.join(
                out, "unknown-key", "HKGPM", "catalogs", "othertest")))
            self.assertNotEqual(0, verify(os.path.join(out, "tampered-index", cat)))
            self.assertEqual(0, verify(os.path.join(out, "tampered-zip", cat)))
            with open(os.path.join(trusted, "index.json")) as f:
                index = json.load(f)
            self.assertEqual(
                ["cartest.conflict", "cartest.green", "cartest.red", "cartest.restart",
                 "cartest.wifi", "cartest.yellow"],
                sorted(e["id"] for e in index["packages"]))
            self.assertTrue(os.path.isfile(os.path.join(
                out, "trusted", "HKGPM", "keys", "test-key.pem")))
            self.assertTrue(os.path.isfile(os.path.join(out, "trusted", "test.a.zip")))
            yellow = [e for e in index["packages"] if e["id"] == "cartest.yellow"][0]
            bad = os.path.join(out, "tampered-zip", cat, "zips", "cartest.yellow.zip")
            self.assertNotEqual(yellow["sha256"], make_catalog._sha256_file(bad))
            self.assertEqual(yellow["size"], os.path.getsize(bad))
            with open(os.path.join(out, "tampered-index", cat, "index.json"), "rb") as f:
                changed = f.read()
            with open(os.path.join(trusted, "index.json"), "rb") as f:
                self.assertEqual(len(f.read()), len(changed))

    def test_car_refuses_a_folder_that_is_not_its_own_output(self):
        with contextlib.redirect_stderr(io.StringIO()):
            self._refuses()

    def _refuses(self):
        with tempfile.TemporaryDirectory() as tmp:
            keep = os.path.join(tmp, "precious.txt")
            with open(keep, "w") as f:
                f.write("x")
            self.assertEqual(2, make_catalog.main(["make_catalog.py", "car", tmp]))
            self.assertTrue(os.path.exists(keep))
            cwd = os.getcwd()
            os.chdir(tmp)
            try:
                self.assertEqual(2, make_catalog.main(["make_catalog.py", "car", "."]))
            finally:
                os.chdir(cwd)
            self.assertTrue(os.path.exists(keep))
        for bad in (os.sep, os.path.expanduser("~"), REPO):
            self.assertEqual(2, make_catalog.main(["make_catalog.py", "car", bad]))


if __name__ == "__main__":
    unittest.main()
