#!/usr/bin/env python3
"""Copy a signed HKG Package Manager catalog onto a USB stick, verified.

Stdlib only. The signature check is RSASSA-PKCS1-v1_5 with SHA-256, done by hand.
Exit codes: 0 done, 1 error, 2 usage, 3 untrusted key.
"""
import argparse
import base64
import hashlib
import hmac
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

FORMAT = 1
RSA_ALG = bytes.fromhex("06092a864886f70d0101010500")  # OID rsaEncryption + NULL
DIGEST_INFO = bytes.fromhex("3031300d060960864801650304020105000420")
ID_RE = re.compile(r"^[A-Za-z0-9._-]{1,64}$")
HEX64 = re.compile(r"^[0-9a-fA-F]{64}$")
CATALOG_FILES = ("key.pem", "index.json.sig", "index.json")
CHUNK = 65536
MAX_INDEX = 2 * 1024 * 1024
MAX_SMALL = 16 * 1024
RESERVED = ("index.json", "index.json.sig", "key.pem")


class Fail(Exception):
    def __init__(self, msg, code=1):
        Exception.__init__(self, msg)
        self.code = code


# ---- DER and signatures ----

def _tlv(buf, pos, tag):
    if pos >= len(buf) or buf[pos] != tag or pos + 1 >= len(buf):
        raise ValueError("bad DER")
    n = buf[pos + 1]
    pos += 2
    if n & 0x80:
        cnt = n & 0x7f
        if cnt == 0 or cnt > 4 or pos + cnt > len(buf):
            raise ValueError("bad DER")
        n = int.from_bytes(buf[pos:pos + cnt], "big")
        pos += cnt
    if pos + n > len(buf):
        raise ValueError("bad DER")
    return buf[pos:pos + n], pos + n


def spki_rsa(der):
    """Return (n, e) of an RSA SPKI; ValueError unless rsaEncryption and n >= 2048 bits."""
    outer, end = _tlv(der, 0, 0x30)
    if end != len(der):
        raise ValueError("trailing data")
    alg, pos = _tlv(outer, 0, 0x30)
    if alg != RSA_ALG:
        raise ValueError("not an rsaEncryption key")
    bits, pos = _tlv(outer, pos, 0x03)
    if pos != len(outer) or not bits or bits[0] != 0:
        raise ValueError("bad bit string")
    seq, end = _tlv(bits[1:], 0, 0x30)
    if end != len(bits) - 1:
        raise ValueError("trailing data")
    n_raw, pos = _tlv(seq, 0, 0x02)
    e_raw, pos = _tlv(seq, pos, 0x02)
    if pos != len(seq):
        raise ValueError("trailing data")
    n = int.from_bytes(n_raw, "big")
    e = int.from_bytes(e_raw, "big")
    if n.bit_length() < 2048:
        raise ValueError("key shorter than 2048 bits")
    if e < 3:
        raise ValueError("bad exponent")
    return n, e


def pem_to_der(pem):
    text = pem.decode("ascii", "replace")
    m = re.search(r"-----BEGIN PUBLIC KEY-----(.*?)-----END PUBLIC KEY-----", text, re.S)
    if not m:
        raise ValueError("not a PUBLIC KEY PEM")
    try:
        return base64.b64decode("".join(m.group(1).split()), validate=True)
    except ValueError:
        raise ValueError("bad base64 in key.pem")


def fingerprint(key_pem):
    return hashlib.sha256(pem_to_der(key_pem)).hexdigest()


def verify(key_pem, data, sig):
    try:
        n, e = spki_rsa(pem_to_der(key_pem))
    except ValueError:
        return False
    k = (n.bit_length() + 7) // 8
    if len(sig) != k:
        return False
    s = int.from_bytes(sig, "big")
    if s >= n:
        return False
    m = pow(s, e, n).to_bytes(k, "big")
    t = DIGEST_INFO + hashlib.sha256(data).digest()
    expected = b"\x00\x01" + b"\xff" * (k - 3 - len(t)) + b"\x00" + t
    return hmac.compare_digest(m, expected)


# ---- trusted keys ----

def config_dir():
    d = os.environ.get("HKGPM_CONFIG_DIR")
    if d:
        return d
    x = os.environ.get("XDG_CONFIG_HOME")
    if x:
        return os.path.join(x, "hkg-package-manager")
    return os.path.join(os.path.expanduser("~"), ".config", "hkg-package-manager")


def trusted_path():
    return os.path.join(config_dir(), "trusted-keys.txt")


def load_trusted():
    keys = set()
    try:
        with open(trusted_path(), encoding="utf-8") as f:
            for line in f:
                head = line.split(" ", 1)[0].strip()
                if re.fullmatch(r"[0-9a-f]{64}", head):
                    keys.add(head)
    except OSError:
        pass
    return keys


def store_trusted(fp, label):
    label = " ".join(label.split())[:80]
    path = trusted_path()
    try:
        with open(path, encoding="utf-8") as f:
            text = f.read()
    except OSError:
        text = ""
    if text and not text.endswith("\n"):
        text += "\n"
    text += "%s %s\n" % (fp, label)
    write_atomic(path, text.encode("utf-8"))


# ---- ids and locations ----

def valid_id(s):
    return (isinstance(s, str) and ID_RE.fullmatch(s) is not None
            and not s.startswith(".") and ".." not in s)


def is_url(s):
    return s.startswith("http://") or s.startswith("https://")


def local_path_for(entry_id, zip_loc):
    """Relative path under the catalog folder, or None if the location is invalid."""
    if is_url(zip_loc):
        return "remote/%s.zip" % entry_id
    if (not zip_loc or zip_loc.startswith("/") or "\\" in zip_loc
            or zip_loc.startswith("remote/") or "://" in zip_loc):
        return None
    low = zip_loc.lower()  # sticks are FAT/exFAT: names compare case-insensitively
    if low in RESERVED or low.endswith(".part"):
        return None
    for seg in zip_loc.split("/"):
        if seg in ("", ".", ".."):
            return None
    return zip_loc


# ---- IO ----

def write_atomic(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    part = path + ".part"
    with open(part, "wb") as f:
        f.write(data)
    os.replace(part, path)


def join_source(src, rel):
    if is_url(src):
        return src.rstrip("/") + "/" + urllib.parse.quote(rel)
    return os.path.join(src, *rel.split("/"))


class _HttpOnlyRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if not is_url(newurl):
            raise urllib.error.URLError("redirect to a non-http(s) address refused")
        return urllib.request.HTTPRedirectHandler.redirect_request(
            self, req, fp, code, msg, headers, newurl)


def open_stream(loc):
    if is_url(loc):
        opener = urllib.request.build_opener(_HttpOnlyRedirect)
        return opener.open(loc, timeout=30)
    return open(loc, "rb")


def read_all(loc, cap):
    f = open_stream(loc)
    try:
        data = f.read(cap + 1)
    finally:
        f.close()
    if len(data) > cap:
        raise Fail("%s is larger than %d bytes" % (os.path.basename(loc), cap))
    return data


def file_ok(path, size, sha):
    try:
        if os.path.getsize(path) != size:
            return False
        h = hashlib.sha256()
        with open(path, "rb") as f:
            for chunk in iter(lambda: f.read(CHUNK), b""):
                h.update(chunk)
        return h.hexdigest() == sha
    except OSError:
        return False


def _rm(path):
    try:
        os.remove(path)
    except OSError:
        pass


def download(loc, dest, size, sha, entry_id):
    part = dest + ".part"
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    try:
        h = hashlib.sha256()
        total = 0
        src = open_stream(loc)
        try:
            with open(part, "wb") as out:
                for chunk in iter(lambda: src.read(CHUNK), b""):
                    total += len(chunk)
                    if total > size:
                        break
                    h.update(chunk)
                    out.write(chunk)
        finally:
            src.close()
        if total != size or h.hexdigest() != sha:
            raise Fail("%s: size or SHA-256 doesn't match the index" % entry_id)
        os.replace(part, dest)
    except Fail:
        _rm(part)
        raise
    except Exception as e:
        _rm(part)
        raise Fail("%s: download failed (%s)" % (entry_id, e))


# ---- main flow ----

def parse_entries(index):
    """Return a list of (id, local_rel, zip_loc, size, sha) for valid entries."""
    pkgs = index.get("packages")
    if not isinstance(pkgs, list):
        raise Fail("index has no packages list")
    entries = []
    seen = set()
    for p in pkgs:
        p = p if isinstance(p, dict) else {}
        pid, zloc, size, sha = p.get("id"), p.get("zip"), p.get("size"), p.get("sha256")
        local = None
        if valid_id(pid) and isinstance(zloc, str):
            local = local_path_for(pid, zloc)
        if (local is None or pid in seen or isinstance(size, bool)
                or not isinstance(size, int) or size < 0
                or not isinstance(sha, str) or not HEX64.fullmatch(sha)):
            sys.stderr.write("warning: skipping invalid entry %r\n" % (pid,))
            continue
        seen.add(pid)
        entries.append((pid, local, zloc, size, sha.lower()))
    return entries


def mirror(src, stick, only, fp_arg):
    try:
        index_raw = read_all(join_source(src, "index.json"), MAX_INDEX)
        sig = read_all(join_source(src, "index.json.sig"), MAX_SMALL)
        key_pem = read_all(join_source(src, "key.pem"), MAX_SMALL)
    except Fail:
        raise
    except Exception as e:
        raise Fail("can't read the catalog: %s" % e)
    try:
        index = json.loads(index_raw.decode("utf-8"))
        if not isinstance(index, dict):
            raise ValueError("not an object")
    except ValueError as e:
        raise Fail("index.json isn't valid JSON: %s" % e)
    try:
        spki_rsa(pem_to_der(key_pem))
        key_fp = fingerprint(key_pem)
    except ValueError as e:
        raise Fail("key.pem is not usable: %s" % e)
    if index.get("key") != key_fp:
        raise Fail("the index's key doesn't match key.pem")

    cat_id = index.get("id")
    name = index.get("name") if isinstance(index.get("name"), str) else str(cat_id)
    newly = False
    if key_fp not in load_trusted():
        if fp_arg is not None and fp_arg.lower() == key_fp:
            newly = True
        else:
            raise Fail("untrusted key %s: check it with the publisher, then re-run "
                       "with --fingerprint %s" % (key_fp, key_fp), 3)
    if not verify(key_pem, index_raw, sig):
        raise Fail("signature doesn't match")
    fmt = index.get("format")
    if isinstance(fmt, bool) or not isinstance(fmt, int):
        raise Fail("index has no integer format")
    if fmt > FORMAT:
        raise Fail("catalog format %d is newer than this tool understands" % fmt)
    if not valid_id(cat_id):
        raise Fail("the catalog id is not valid")

    entries = parse_entries(index)
    wanted = entries
    if only:
        known = set(e[0] for e in entries)
        for o in sorted(only):
            if o not in known:
                raise Fail("no such package in the catalog: %s" % o, 2)
        wanted = [e for e in entries if e[0] in only]
    if newly:
        store_trusted(key_fp, name)

    target = os.path.join(stick, "HKGPM", "catalogs", cat_id)
    fetched = kept = removed = 0
    keep = set(CATALOG_FILES)
    for pid, local, zloc, size, sha in wanted:
        dest = os.path.join(target, *local.split("/"))
        keep.add(local)
        if file_ok(dest, size, sha):
            kept += 1
            print("kept     %s" % pid)
            continue
        download(zloc if is_url(zloc) else join_source(src, zloc), dest, size, sha, pid)
        fetched += 1
        print("fetched  %s" % pid)

    write_atomic(os.path.join(target, "key.pem"), key_pem)
    write_atomic(os.path.join(target, "index.json.sig"), sig)
    write_atomic(os.path.join(target, "index.json"), index_raw)

    for d, _dirs, files in os.walk(target, topdown=False):
        for n in files:
            p = os.path.join(d, n)
            rel = os.path.relpath(p, target).replace(os.sep, "/")
            if rel not in keep:
                _rm(p)
                removed += 1
                print("removed  %s" % rel)
        if d != target:
            try:
                os.rmdir(d)  # only succeeds when empty
            except OSError:
                pass
    print("%s: %d fetched, %d kept, %d removed" % (cat_id, fetched, kept, removed))


def main(argv):
    ap = argparse.ArgumentParser(
        prog="hkgpm_mirror.py",
        description="Copy a signed catalog (folder or http(s) URL) onto a USB stick.")
    ap.add_argument("source", metavar="SOURCE")
    ap.add_argument("stick", metavar="STICK")
    ap.add_argument("--only", metavar="ID", nargs="+", help="mirror only these package ids")
    ap.add_argument("--fingerprint", metavar="HEX",
                    help="trust the catalog key with this fingerprint and remember it")
    try:
        a = ap.parse_args(argv)
    except SystemExit as e:
        return e.code if isinstance(e.code, int) else 2
    try:
        mirror(a.source, a.stick, set(a.only) if a.only else None, a.fingerprint)
    except Fail as e:
        sys.stderr.write("%s\n" % e)
        return e.code
    except OSError as e:
        sys.stderr.write("error: %s\n" % e)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
