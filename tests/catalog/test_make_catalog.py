import filecmp
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


if __name__ == "__main__":
    unittest.main()
