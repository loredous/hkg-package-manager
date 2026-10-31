#!/usr/bin/env python3
"""TEST-ONLY catalog builder: package zips, index.json and an openssl signature.

Not a publishing tool. Stdlib plus the openssl command line.
"""
import hashlib
import json
import os
import shutil
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


def package_zip(path, pkg_id, name, version, script_body, hook="postinstall",
                declare_hook=False):
    """Write a NEMESIS package zip (stored, fixed timestamps) to path.

    declare_hook adds the `<hook>=scripts/<hook>.sh` line to NEMESIS.data, so NEMESIS runs the
    script (the sample catalog's zips leave it out; they are never installed).
    """
    data = "[nemesis]\nid=%s\nname=%s\nversion=%s\n" % (pkg_id, name, version)
    if declare_hook:
        data += "%s=scripts/%s.sh\n" % (hook, hook)
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
                  generated="2026-11-01T00:00:00Z", zip_base_url=None, declare_hook=False):
    """Write a catalog folder; return the key fingerprint."""
    zips = os.path.join(folder, "zips")
    os.makedirs(zips, exist_ok=True)
    entries = []
    for pkg in packages:
        entry = {k: v for k, v in pkg.items() if k != "script"}
        zpath = os.path.join(zips, pkg["id"] + ".zip")
        package_zip(zpath, pkg["id"], pkg["name"], pkg["version"], pkg["script"],
                    declare_hook=declare_hook)
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


def car_packages():
    def pkg(suffix, name, desc, script=None, **compat):
        pid = "cartest." + suffix
        p = {"id": pid, "name": name, "version": "1.0.0", "description": desc,
             "author": "hkg-package-manager tests",
             "script": script or 'echo "%s" > /data/nemesis/%s.marker\nexit 0' % (pid, pid)}
        if compat:
            p["compatibility"] = compat
        return p
    return [
        pkg("green", "Car test green", "Works on builds NEEV23.*.",
            works_on=[{"build": "NEEV23.*"}]),
        pkg("wifi", "Car test wifi", "Works on cars with Wi-Fi.",
            works_on=[{"hardware": ["wifi"]}]),
        pkg("yellow", "Car test yellow", "No compatibility data."),
        pkg("red", "Car test red", "Marked as not working anywhere.",
            not_on=[{"build": "*"}]),
        pkg("conflict", "Car test conflict", "Conflicts with test.a.",
            conflicts=["test.a"]),
        pkg("restart", "Car test restart", "Restarts the head unit.",
            script="echo \"cartest.restart: restarting\"\nexec /system/bin/reboot",
            works_on=[{"build": "*"}]),
    ]


def _flip(path, marker):
    """Change one byte of the file, at the first occurrence of marker."""
    with open(path, "rb") as f:
        data = bytearray(f.read())
    at = bytes(data).index(marker)
    data[at] ^= 0x01
    with open(path, "wb") as f:
        f.write(bytes(data))


def car_out_problem(out_dir):
    """Why out_dir can't be emptied and rebuilt, or None. Only a folder this tool built before qualifies."""
    path = os.path.realpath(out_dir)
    repo = os.path.dirname(os.path.dirname(HERE))
    protected = [os.path.realpath(os.sep), os.path.realpath(os.path.expanduser("~")),
                 os.path.realpath(repo)]
    if any(path == x or path in _parents(x) for x in protected):
        return "refusing to use %s: it holds the repo, the home folder or the root" % out_dir
    if os.path.exists(path) and (not os.path.isdir(path) or (
            os.listdir(path) and not os.path.isdir(os.path.join(path, "trusted")))):
        return "refusing to empty %s: it isn't empty and has no trusted/ folder from an earlier run" % out_dir
    return None


def _parents(path):
    out = []
    while True:
        up = os.path.dirname(path)
        if up == path:
            return out
        out.append(up)
        path = up


def build_car(out_dir):
    """Write the stick-ready scenario trees under out_dir (emptied first)."""
    shutil.rmtree(out_dir, ignore_errors=True)
    cat = os.path.join("HKGPM", "catalogs", "cartest")
    trusted = os.path.join(out_dir, "trusted")
    folder = os.path.join(trusted, cat)
    os.makedirs(folder)
    key = os.path.join(KEYS, "test-key.pem")
    build_catalog(folder, "cartest", "Car test catalog", "hkg-package-manager tests",
                  car_packages(), key, declare_hook=True)
    keys = os.path.join(trusted, "HKGPM", "keys")
    os.makedirs(keys)
    shutil.copyfile(os.path.join(folder, "key.pem"), os.path.join(keys, "test-key.pem"))
    package_zip(os.path.join(trusted, "test.a.zip"), "test.a", "Test A", "1.0.0",
                'echo "test.a" > /data/nemesis/test.a.marker\nexit 0', declare_hook=True)

    bad_index = os.path.join(out_dir, "tampered-index", cat)
    shutil.copytree(folder, bad_index)
    _flip(os.path.join(bad_index, "index.json"), b"Car test green")

    bad_zip = os.path.join(out_dir, "tampered-zip", cat)
    shutil.copytree(folder, bad_zip)
    _flip(os.path.join(bad_zip, "zips", "cartest.yellow.zip"), b"exit 0")

    other = os.path.join(out_dir, "unknown-key", "HKGPM", "catalogs", "othertest")
    os.makedirs(other)
    build_catalog(other, "othertest", "Car test catalog (other key)",
                  "hkg-package-manager tests", car_packages(),
                  os.path.join(KEYS, "other-test-key.pem"), declare_hook=True)


def main(argv):
    if len(argv) == 3 and argv[1] == "sample":
        print(build_sample(argv[2]))
        return 0
    if len(argv) == 3 and argv[1] == "car":
        problem = car_out_problem(argv[2])
        if problem:
            sys.stderr.write(problem + "\n")
            return 2
        build_car(argv[2])
        return 0
    sys.stderr.write("usage: make_catalog.py sample <stick-root> | car <out-dir>\n")
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
