# HKG Package Manager catalog format, version 1

A catalog is a static folder: a JSON index, a signature, the signer's public key and the
package zips. It can be served from any plain web server, GitHub Pages or a GitHub repo,
and it can be copied to a USB stick byte for byte. Nothing here ties a catalog to one host.
`catalog.schema.json` (JSON Schema, draft 2020-12) describes the index.

## Folder layout

On a web server the folder is whatever URL you choose. On a USB stick it is
`HKGPM/catalogs/<id>/`, and the folder name must equal the catalog's `id`.

```
HKGPM/catalogs/<id>/
  index.json         the index (UTF-8 JSON)
  index.json.sig     the signature, raw bytes
  key.pem            the signing public key (SPKI "PUBLIC KEY" PEM)
  zips/<package>.zip package zips stored beside the index (any relative path works)
  remote/<package>.zip   only on a stick: the copy of a zip the index links by URL
```

A package zip is either beside the index (a relative `zip`) or at an `http://` or `https://`
URL. When a catalog with URL zips is copied to a stick, each such zip is stored as
`remote/<package id>.zip` and the manager reads it from there. For that reason a relative
`zip` must not start with `remote/`.

## index.json

The top level is an object. Unknown fields are ignored everywhere, so the format can grow.
A field of a known name with the wrong type makes that package entry invalid.

| Field | Type | Required | Meaning |
|---|---|---|---|
| `format` | integer | yes | Format version. This document is `1`. A reader that only knows version N refuses a higher one. |
| `id` | string | yes | Catalog id: 1 to 64 characters of `A-Z a-z 0-9 . _ -`, not starting with `.` and with no `..`. |
| `name` | string | yes | Catalog name shown to people. |
| `publisher` | string | yes | Who publishes the catalog. |
| `generated` | string | yes | When the index was generated, ISO 8601 UTC, such as `2026-11-01T00:00:00Z`. |
| `key` | string | yes | Fingerprint of the signing key (see below): 64 lowercase hex characters. |
| `packages` | array | yes | The package entries. One current version per package id. |

### Package entry

| Field | Type | Required | Meaning |
|---|---|---|---|
| `id` | string | yes | Package id: 1 to 64 characters of `A-Z a-z 0-9 . _ -`, not starting with `.` and with no `..` (the same rule as the catalog id). |
| `name` | string | yes | Display name. |
| `version` | string | yes | Version text. |
| `description` | string | yes | Short description. |
| `author` | string | yes | Author. |
| `homepage` | string | no | Project page. |
| `zip` | string | yes | Where the zip is: a path relative to the catalog folder, or an `http://` or `https://` URL. |
| `size` | integer | yes | Size of the zip in bytes, 0 or more. |
| `sha256` | string | yes | SHA-256 of the zip, 64 hex characters (either case). |
| `compatibility` | object | no | See below. |

A package id may appear once in a catalog. A later entry with an id already used earlier in
the same catalog is invalid (shown, not installable).

A relative `zip` uses `/` separators, does not start with `/` or `remote/`, contains no `\`,
and has no empty, `.` or `..` segment. Any other URL scheme makes the entry invalid.
Readers check the size and hash of a zip when listing and again just before installing; a
mismatch refuses the zip.

### Compatibility

All parts are optional. `compatibility` holds:

| Field | Type | Meaning |
|---|---|---|
| `works_on` | list of rules | Positive side. |
| `not_on` | list of rules | Negative side. |
| `conflicts` | list of package ids | Negative side: having any of these installed counts as a negative match. |

A rule is an object with one or both conditions:

| Field | Type | Meaning |
|---|---|---|
| `build` | string | A glob matched against the car's build id (`ro.build.product`). `*` matches any run of characters (including none) and `?` exactly one. The whole string must match, case-sensitive. |
| `hardware` | list of strings | Each name is required present (`wifi`) or required absent (`!wifi`). Only `wifi` is defined. |

How rules combine:

- A side matches if any of its rules matches (OR across rules).
- A rule matches only if every condition it has matches (AND within a rule).
- A rule with no conditions is ignored.
- A rule that names hardware the reader doesn't know never matches, so it can neither make
  a package green nor red. Likewise a rule never matches a fact the reader can't determine.
- A hardware-only rule is valid. "Works on any car with Wi-Fi" is a `works_on` rule with
  `["wifi"]`; "doesn't work without Wi-Fi" is a `not_on` rule with `["!wifi"]`.
- Red wins over green when both match.

| Colour | When | Install |
|---|---|---|
| Green | a `works_on` rule matches | normal |
| Yellow | no compatibility data, or no match either way | after a confirmation |
| Red | a `not_on` rule matches, or a `conflicts` package is installed | only after an explicit extra confirmation |

## Signature and key

- `index.json.sig` is an RSA PKCS#1 v1.5 signature with SHA-256 over the exact bytes of
  `index.json`, stored as raw bytes (what `openssl dgst -sha256 -sign` writes). Use a key of
  2048 bits or more. Do not reformat the index after signing.
- `key.pem` is the matching public key in SPKI `PUBLIC KEY` PEM form.
- The fingerprint is the lowercase hex SHA-256 of the key's SPKI DER encoding. The index's
  `key` field must be that fingerprint.
- Readers keep a list of trusted fingerprints. A catalog is trusted when its `key.pem` hashes
  to a trusted fingerprint and the signature verifies against that key. A catalog that fails
  verification is not shown. The zips are covered by the `sha256` values in the signed index.

## Signing a catalog

```
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out catalog-key.pem
openssl pkey -in catalog-key.pem -pubout -out key.pem
openssl pkey -pubin -in key.pem -outform DER | sha256sum      # the fingerprint, for "key"
openssl dgst -sha256 -sign catalog-key.pem -out index.json.sig index.json
```

Keep `catalog-key.pem` private. Put the fingerprint in `index.json` as `key` before the
last command, since the signature covers the final file.
