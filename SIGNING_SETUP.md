# Release Signing Setup Guide

Follow this one-time guide to generate your own permanent Android release keystore and configure GitHub Actions so every release APK is signed consistently and can update directly over previous installations.

## 1. Generate a Permanent Keystore (One Time Only)

Run `keytool` (included with JDK 17+) on your own computer—**outside** the repository folder so you never commit it by accident:

```bash
keytool -genkeypair -v \
  -keystore release-upload-key.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias release-key
```

When prompted:
- Enter a strong **keystore password** (remember it for `SIGNING_STORE_PASSWORD`).
- Enter a strong **key password** (remember it for `SIGNING_KEY_PASSWORD`).
- Fill in your certificate details (Name, Organization, Country, etc.).

## 2. Back Up Your Keystore Safely

Store `release-upload-key.jks`, your alias, and both passwords in a secure password manager or encrypted offline backup.

> **Important:** Android verifies app signatures on every update. If you lose this keystore or change keys later, existing users will **never** be able to install new updates over the older version without uninstalling and losing app data.

## 3. Base64-Encode the Keystore and Add GitHub Secrets

Encode `release-upload-key.jks` into a single-line Base64 string:

- **Linux:**
  ```bash
  base64 -w 0 release-upload-key.jks > keystore.b64
  ```
- **macOS:**
  ```bash
  base64 -i release-upload-key.jks | tr -d '\n' > keystore.b64
  ```
- **Windows (PowerShell):**
  ```powershell
  [Convert]::ToBase64String([IO.File]::ReadAllBytes("release-upload-key.jks")) | Set-Content keystore.b64
  ```

Next, open your repository on GitHub and go to:

**Settings → Secrets and variables → Actions → New repository secret**

Add these **exact four secrets**:

| Secret Name | Value |
| :--- | :--- |
| `SIGNING_KEYSTORE_BASE64` | Full contents of `keystore.b64` (single-line Base64 string) |
| `SIGNING_STORE_PASSWORD` | Your keystore password |
| `SIGNING_KEY_ALIAS` | Your key alias (e.g. `release-key`) |
| `SIGNING_KEY_PASSWORD` | Your key password |

Delete `keystore.b64` from your local computer once you have pasted it into GitHub Secrets.

## 4. Security Rules

- **Never commit** `release-upload-key.jks`, `keystore.b64`, `.env`, `local.properties`, or any passwords to Git.
- Ensure `app/build.gradle.kts` reads `SIGNING_KEYSTORE_PATH`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, and `SIGNING_KEY_PASSWORD` from environment variables in `signingConfigs.create("release")`.
