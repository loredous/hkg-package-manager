# Mirror CLI

Copies a signed catalog onto a USB stick so the car's manager can use it offline.
Python 3.8+, standard library only.

    python3 cli/hkgpm_mirror.py SOURCE STICK [--only ID ...] [--fingerprint HEX]

- `SOURCE`: a catalog folder, or the `http(s)://` URL of one (it serves `index.json`,
  `index.json.sig`, `key.pem`).
- `STICK`: the stick's mount point. Files go to `STICK/HKGPM/catalogs/<id>/`, nothing else.
- `--only ID ...`: fetch just these packages. The full signed index is still written.
- Re-running updates in place: changed zips are fetched, matching ones kept, and files the
  index no longer lists (or that you left out with `--only`) are removed.

## Trust

The catalog's `key.pem` must match the index's `key` and the signature must verify. The key
must be trusted: listed in `trusted-keys.txt` in `$HKGPM_CONFIG_DIR`, else
`$XDG_CONFIG_HOME/hkg-package-manager`, else `~/.config/hkg-package-manager`
(one `<64 hex> <label>` per line). For a new key the tool prints the fingerprint and stops;
check it with the publisher, then re-run with `--fingerprint <fp>`, which stores it. The key is stored at once, even if the run then fails
(for example on a bad zip); remove the line from `trusted-keys.txt` to undo.
Nothing is written to the stick before the trust check passes.

## Exit codes

0 done, 1 error (bad signature, size/SHA-256 mismatch, download failure), 2 usage
(including an unknown `--only` id), 3 untrusted key.

## Stick layout

    HKGPM/catalogs/<id>/index.json
                        index.json.sig
                        key.pem
                        <relative zip paths>      e.g. zips/<package>.zip
                        remote/<package>.zip      zips the index links by URL

Zips are downloaded to `.part` files, checked, then renamed. The three catalog files are
written last, so a failed run leaves the previous index in place.
