# Focuslot release signing

## Public release identity

- Application ID: `space.xproject.focuslot`
- First public version: `1.0.0`
- First public versionCode: `1`
- Key alias: `focuslot`
- Certificate algorithm: RSA 4096 / SHA-384 self-signed certificate
- Certificate validity: 26 Oct 2026 – 13 Mar 2054
- Certificate SHA-256:

```
8C:D3:C6:7C:52:17:99:A3:73:36:E2:92:87:67:B1:00:93:DC:FB:3B:35:F8:73:59:1E:0C:E7:79:EC:D1:9E:EE
```

This fingerprint is safe to keep in the repository. The private keystore and passwords are not.

## Required GitHub Actions secrets

The workflow `.github/workflows/focuslot-release.yml` expects:

- `FOCUSLOT_RELEASE_KEYSTORE_B64`
- `FOCUSLOT_RELEASE_STORE_PASSWORD`
- `FOCUSLOT_RELEASE_KEY_ALIAS`
- `FOCUSLOT_RELEASE_KEY_PASSWORD`

The keystore and its recovery bundle are deliberately stored outside the repository.

## Rule for every future Android release

Do not create a new signing key for an ordinary update. Reuse the Focuslot release key above.
Android and RuStore use the signing certificate to decide whether an APK is a legitimate update to
an installed application.

If Focuslot is later published to another Android store, prefer the same signing certificate there
as well, so installs from different stores remain update-compatible.
