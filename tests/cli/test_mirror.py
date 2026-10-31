import hashlib
import http.server
import io
import os
import shutil
import subprocess
import sys
import tempfile
import threading
import unittest
from contextlib import redirect_stderr, redirect_stdout

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tests", "catalog"))
sys.path.insert(0, os.path.join(ROOT, "cli"))

import hkgpm_mirror  # noqa: E402
import make_catalog  # noqa: E402

KEY = os.path.join(make_catalog.KEYS, "test-key.pem")
SAMPLE = os.path.join(ROOT, "tests", "catalog", "sample", "HKGPM", "catalogs", "sample")


def read(path):
    with open(path, "rb") as f:
        return f.read()


def tree(folder):
    out = {}
    for d, _, files in os.walk(folder):
        for n in files:
            p = os.path.join(d, n)
            out[os.path.relpath(p, folder)] = read(p)
    return out


class QuietHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *a):
        pass


class Base(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.tmp, True)
        self.cfg = os.path.join(self.tmp, "cfg")
        old = os.environ.get("HKGPM_CONFIG_DIR")
        os.environ["HKGPM_CONFIG_DIR"] = self.cfg
        self.addCleanup(self._restore, old)
        self.stick = os.path.join(self.tmp, "stick")
        os.makedirs(self.stick)
        self.target = os.path.join(self.stick, "HKGPM", "catalogs", "sample")
        self.fp = hkgpm_mirror.fingerprint(read(os.path.join(SAMPLE, "key.pem")))

    @staticmethod
    def _restore(old):
        if old is None:
            os.environ.pop("HKGPM_CONFIG_DIR", None)
        else:
            os.environ["HKGPM_CONFIG_DIR"] = old

    def run_cli(self, *args):
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            code = hkgpm_mirror.main(list(args))
        return code, out.getvalue(), err.getvalue()

    def build(self, name="src", packages=None, **kw):
        folder = os.path.join(self.tmp, name)
        os.makedirs(folder)
        make_catalog.build_catalog(folder, "sample", "Sample", "tests",
                                   packages or make_catalog.sample_packages(), KEY, **kw)
        return folder

    def trusted_file(self):
        return os.path.join(self.cfg, "trusted-keys.txt")


class CryptoTest(Base):
    def test_verify_matches_openssl_on_sample(self):
        pem = read(os.path.join(SAMPLE, "key.pem"))
        data = read(os.path.join(SAMPLE, "index.json"))
        sig = read(os.path.join(SAMPLE, "index.json.sig"))
        self.assertTrue(hkgpm_mirror.verify(pem, data, sig))
        self.assertEqual(len(self.fp), 64)

    def test_verify_rejects_flipped_byte(self):
        pem = read(os.path.join(SAMPLE, "key.pem"))
        data = read(os.path.join(SAMPLE, "index.json"))
        sig = bytearray(read(os.path.join(SAMPLE, "index.json.sig")))
        self.assertFalse(hkgpm_mirror.verify(pem, data + b" ", bytes(sig)))
        sig[10] ^= 1
        self.assertFalse(hkgpm_mirror.verify(pem, data, bytes(sig)))
        self.assertFalse(hkgpm_mirror.verify(pem, data, bytes(sig)[:-1]))

    def test_spki_rejects_short_key(self):
        p = os.path.join(self.tmp, "k.pem")
        subprocess.run(["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt",
                        "rsa_keygen_bits:1024", "-out", p], check=True,
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        pub = subprocess.run(["openssl", "pkey", "-in", p, "-pubout", "-outform", "DER"],
                             check=True, stdout=subprocess.PIPE,
                             stderr=subprocess.PIPE).stdout
        with self.assertRaises(ValueError):
            hkgpm_mirror.spki_rsa(pub)
        with self.assertRaises(ValueError):
            hkgpm_mirror.spki_rsa(b"garbage")


class MirrorTest(Base):
    def test_mirror_local_catalog(self):
        code, out, err = self.run_cli(SAMPLE, self.stick, "--fingerprint", self.fp)
        self.assertEqual((code, err), (0, ""))
        self.assertEqual(len([l for l in out.splitlines() if l.startswith("fetched")]), 5)
        self.assertEqual(tree(self.target), tree(SAMPLE))
        self.assertTrue(read(self.trusted_file()).decode().startswith(self.fp + " Sample catalog\n"))

    def test_untrusted_key_exits_3_and_writes_nothing(self):
        code, out, err = self.run_cli(SAMPLE, self.stick)
        self.assertEqual(code, 3)
        self.assertIn("untrusted key " + self.fp, err)
        self.assertIn("--fingerprint " + self.fp, err)
        self.assertEqual(os.listdir(self.stick), [])
        self.assertFalse(os.path.exists(self.trusted_file()))
        code, _, _ = self.run_cli(SAMPLE, self.stick, "--fingerprint", "0" * 64)
        self.assertEqual(code, 3)
        self.assertEqual(os.listdir(self.stick), [])

    def test_stored_key_needs_no_flag(self):
        self.assertEqual(self.run_cli(SAMPLE, self.stick, "--fingerprint", self.fp)[0], 0)
        shutil.rmtree(os.path.join(self.stick, "HKGPM"))
        code, out, err = self.run_cli(SAMPLE, self.stick)
        self.assertEqual((code, err), (0, ""))
        self.assertEqual(tree(self.target), tree(SAMPLE))
        self.assertEqual(read(self.trusted_file()).count(b"\n"), 1)

    def test_tampered_index_exits_1_and_writes_nothing(self):
        src = self.build()
        p = os.path.join(src, "index.json")
        with open(p, "ab") as f:
            f.write(b" ")
        code, _, err = self.run_cli(src, self.stick, "--fingerprint", self.fp)
        self.assertEqual(code, 1)
        self.assertIn("signature doesn't match", err)
        self.assertEqual(os.listdir(self.stick), [])

    def test_tampered_zip_exits_1_and_keeps_old_stick(self):
        self.assertEqual(self.run_cli(SAMPLE, self.stick, "--fingerprint", self.fp)[0], 0)
        os.remove(os.path.join(self.target, "zips", "sample.red.zip"))  # so it must be fetched
        before = tree(self.target)
        # a newer index (different generated stamp) so a partial write would show
        src2 = self.build("src2", generated="2026-12-01T00:00:00Z")
        z = os.path.join(src2, "zips", "sample.red.zip")
        data = bytearray(read(z))
        data[-1] ^= 1
        with open(z, "wb") as f:
            f.write(bytes(data))
        code, _, err = self.run_cli(src2, self.stick)
        self.assertEqual(code, 1)
        self.assertIn("sample.red", err)
        after = tree(self.target)
        self.assertEqual(after["index.json"], before["index.json"])
        self.assertEqual(after, before)

    def test_subset_keeps_full_index(self):
        code, out, err = self.run_cli(SAMPLE, self.stick, "--fingerprint", self.fp,
                                      "--only", "sample.green")
        self.assertEqual((code, err), (0, ""))
        t = tree(self.target)
        self.assertEqual(sorted(t), ["index.json", "index.json.sig", "key.pem",
                                     os.path.join("zips", "sample.green.zip")])
        self.assertEqual(t["index.json"], read(os.path.join(SAMPLE, "index.json")))

    def test_rerun_removes_dropped_zip(self):
        self.assertEqual(self.run_cli(SAMPLE, self.stick, "--fingerprint", self.fp)[0], 0)
        stray = os.path.join(self.target, "zips", "x.zip.part")
        with open(stray, "wb") as f:
            f.write(b"x")
        pk = [p for p in make_catalog.sample_packages() if p["id"] != "sample.plain"]
        src = self.build(packages=pk)
        code, out, err = self.run_cli(src, self.stick)
        self.assertEqual((code, err), (0, ""))
        self.assertIn("removed", out)
        self.assertIn("kept", out)
        self.assertEqual(tree(self.target), tree(src))

    def test_unknown_only_id_exits_2(self):
        code, _, err = self.run_cli(SAMPLE, self.stick, "--fingerprint", self.fp,
                                    "--only", "no.such")
        self.assertEqual(code, 2)
        self.assertIn("no.such", err)
        self.assertEqual(os.listdir(self.stick), [])

    def test_usage_exits_2(self):
        self.assertEqual(self.run_cli()[0], 2)


class UrlTest(Base):
    def serve(self, folder, handler=QuietHandler):
        def h(*a, **kw):
            return handler(*a, directory=folder, **kw)
        srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), h)
        t = threading.Thread(target=srv.serve_forever)
        t.daemon = True
        t.start()

        def stop():
            srv.shutdown()
            srv.server_close()
            t.join()
        self.addCleanup(stop)
        return "http://127.0.0.1:%d" % srv.server_address[1]

    def test_url_catalog_and_url_zip(self):
        web = os.path.join(self.tmp, "web")
        os.makedirs(web)
        base = self.serve(web)
        src = os.path.join(web, "cat")
        os.makedirs(src)
        make_catalog.build_catalog(src, "sample", "Sample", "tests",
                                   make_catalog.sample_packages(), KEY,
                                   zip_base_url=base + "/cat/zips")
        code, out, err = self.run_cli(base + "/cat", self.stick, "--fingerprint", self.fp)
        self.assertEqual((code, err), (0, ""))
        t = tree(self.target)
        self.assertIn(os.path.join("remote", "sample.green.zip"), t)
        self.assertEqual(t["remote/sample.green.zip"],
                         read(os.path.join(src, "zips", "sample.green.zip")))
        self.assertEqual(t["index.json"], read(os.path.join(src, "index.json")))
        self.assertEqual(len(t), 8)

    def test_interruptedDownloadLeavesNoFinalFile(self):
        web = os.path.join(self.tmp, "web")
        os.makedirs(web)
        broken = {"on": True}

        class Half(QuietHandler):
            def do_GET(self):
                if broken["on"] and self.path.endswith("sample.red.zip"):
                    data = read(os.path.join(web, "zips", "sample.red.zip"))
                    self.send_response(200)
                    self.send_header("Content-Length", str(len(data)))
                    self.end_headers()
                    self.wfile.write(data[:len(data) // 2])
                    self.wfile.flush()
                    self.close_connection = True
                    return
                super().do_GET()

        base = self.serve(web, Half)
        make_catalog.build_catalog(web, "sample", "Sample", "tests",
                                   make_catalog.sample_packages(), KEY)
        code, _, err = self.run_cli(base, self.stick, "--fingerprint", self.fp)
        self.assertEqual(code, 1)
        self.assertIn("sample.red", err)
        self.assertFalse(os.path.exists(os.path.join(self.target, "zips", "sample.red.zip")))
        self.assertFalse(os.path.exists(os.path.join(self.target, "index.json")))
        broken["on"] = False
        code, _, err = self.run_cli(base, self.stick)
        self.assertEqual((code, err), (0, ""))
        self.assertEqual(tree(self.target), tree(web))


if __name__ == "__main__":
    unittest.main()
