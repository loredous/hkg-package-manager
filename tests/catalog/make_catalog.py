#!/usr/bin/env python3
"""TEST-ONLY catalog builder: package zips, index.json and an openssl signature.

Not a publishing tool. Stdlib plus the openssl command line.
"""
import hashlib
import json
import os
import subprocess
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
KEYS = os.path.join(HERE, "keys")
FIXED_TIME = (2026, 1, 1, 0, 0, 0)


def _add(zf, name, data, mode):
    info = zipfile.ZipInfo(name, date_time=FIXED_TIME)
    info.compress_type = zipfile.ZIP_STORED
    info.external_attr = mode << 16
    zf.writestr(info, data)


def package_zip(path, pkg_id, name, version, script_body, hook="postinstall"):
    """Write a NEMESIS package zip (stored, fixed timestamps) to path."""
    data = "[nemesis]\nid=%s\nname=%s\nversion=%s\n" % (pkg_id, name, version)
    script = "#!/system/bin/sh\n" + script_body
    if not script.endswith("\n"):
        script += "\n"
    with zipfile.ZipFile(path, "w", zipfile.ZIP_STORED) as zf:
        _add(zf, "NEMESIS.data", data, 0o100644)
        _add(zf, "scripts/%s.sh" % hook, script, 0o100755)


def _openssl(*args):
    return subprocess.run(["openssl"] + list(args), check=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout


def _sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        h.update(f.read())
    return h.hexdigest()


def build_catalog(folder, cat_id, name, publisher, packages, key_pem_path,
                  generated="2026-11-01T00:00:00Z", zip_base_url=None):
    """Write a catalog folder; return the key fingerprint."""
    zips = os.path.join(folder, "zips")
    os.makedirs(zips, exist_ok=True)
    entries = []
    for pkg in packages:
        entry = {k: v for k, v in pkg.items() if k != "script"}
        zpath = os.path.join(zips, pkg["id"] + ".zip")
        package_zip(zpath, pkg["id"], pkg["name"], pkg["version"], pkg["script"])
        entry["size"] = os.path.getsize(zpath)
        entry["sha256"] = _sha256_file(zpath)
        if zip_base_url:
            entry["zip"] = "%s/%s.zip" % (zip_base_url.rstrip("/"), pkg["id"])
        else:
            entry["zip"] = "zips/%s.zip" % pkg["id"]
        entries.append(entry)

    pub_path = os.path.join(folder, "key.pem")
    pub = _openssl("pkey", "-in", key_pem_path, "-pubout")
    with open(pub_path, "wb") as f:
        f.write(pub)
    der = _openssl("pkey", "-pubin", "-in", pub_path, "-outform", "DER")
    fingerprint = hashlib.sha256(der).hexdigest()

    index = {"format": 1, "id": cat_id, "name": name, "publisher": publisher,
             "generated": generated, "key": fingerprint, "packages": entries}
    index_path = os.path.join(folder, "index.json")
    with open(index_path, "wb") as f:
        f.write((json.dumps(index, indent=2, sort_keys=True) + "\n").encode("utf-8"))
    _openssl("dgst", "-sha256", "-sign", key_pem_path,
             "-out", os.path.join(folder, "index.json.sig"), index_path)
    return fingerprint


def sample_packages():
    def pkg(pid, name, desc, **compat):
        p = {"id": pid, "name": name, "version": "1.0.0", "description": desc,
             "author": "hkg-package-manager tests", "script": "exit 0"}
        if compat:
            p["compatibility"] = compat
        return p
    return [
        pkg("sample.green", "Sample green", "Works on matching builds.",
            works_on=[{"build": "NEEV23.*"}]),
        pkg("sample.wifi", "Sample wifi", "Works on cars with Wi-Fi.",
            works_on=[{"hardware": ["wifi"]}]),
        pkg("sample.red", "Sample red", "Known not to work anywhere.",
            not_on=[{"build": "*"}]),
        pkg("sample.conflict", "Sample conflict", "Conflicts with sample.green.",
            conflicts=["sample.green"]),
        pkg("sample.plain", "Sample plain", "No compatibility data."),
    ]


def build_sample(stick_root):
    folder = os.path.join(stick_root, "HKGPM", "catalogs", "sample")
    os.makedirs(folder, exist_ok=True)
    return build_catalog(folder, "sample", "Sample catalog",
                         "hkg-package-manager tests", sample_packages(),
                         os.path.join(KEYS, "test-key.pem"))


def main(argv):
    if len(argv) == 3 and argv[1] == "sample":
        print(build_sample(argv[2]))
        return 0
    if len(argv) == 3 and argv[1] == "car":
        sys.stderr.write("car: not yet\n")
        return 2
    sys.stderr.write("usage: make_catalog.py sample <stick-root> | car <out-dir>\n")
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
