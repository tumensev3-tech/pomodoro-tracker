# Focuslot release signing

The first public Focuslot Android release uses one locally generated signing key. The key itself and
all passwords are deliberately kept outside this repository.

## Certificate

- Algorithm: RSA 4096
- Alias: `focuslot`
- Created: 2026-10-23
- Valid until: 2054-03-10
- SHA-256 certificate fingerprint:

```
10:36:DE:47:A5:E0:88:88:53:F4:7A:12:D6:FB:9C:9D:4E:44:4E:12:2A:5E:03:DA:C6:78:46:1D:E5:9A:B9:F5
```

This fingerprint is public information and may be used to verify release APKs.

## Local build

Keep `focuslot-release.jks` in secure storage and create `~/.pomodoro/keystore.properties`:

```properties
storeFile=/absolute/path/to/focuslot-release.jks
storePassword=...
keyAlias=focuslot
keyPassword=...
```

Then:

```bash
./gradlew :app:assembleRelease
```

## GitHub Actions

The manual `Build Focuslot release` workflow expects these repository secrets:

- `FOCUSLOT_RELEASE_KEYSTORE_B64`
- `FOCUSLOT_RELEASE_STORE_PASSWORD`
- `FOCUSLOT_RELEASE_KEY_ALIAS`
- `FOCUSLOT_RELEASE_KEY_PASSWORD`

The keystore is decoded only on the temporary Actions runner. It must never be committed.

## Backup rule

Keep at least two independent copies of the keystore and credentials. All public Android releases
should use the same certificate so users can update Focuslot without reinstalling.
