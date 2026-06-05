# Keep_alive Local Security Design

## Goal

Protect the app UI so only the phone owner can open it, and harden release APKs against casual modification.

## Local Unlock

The app uses local-only authentication. It does not add online login, cloud identity, or any external account.

On first launch, the user must set a numeric PIN with at least 4 digits. The PIN is never stored in plain text. `AuthRepository` stores a random salt and a SHA-256 hash of `salt + pin` in SharedPreferences.

On later launches, the app first offers Android biometric unlock when biometric hardware and enrollment are available. The biometric prompt has a fallback button that opens the local PIN dialog. If biometric is unavailable, the app opens the PIN dialog directly.

## Re-lock Rule

The activity records when it goes to background. If the user returns after 15 seconds or more, the UI locks again and requires biometric or PIN unlock. Short interruptions, such as notification permission prompts or biometric system UI, do not intentionally expose the app.

## Tamper Hardening

Release builds enable R8 minification and resource shrinking. Release signing is supported through GitHub Actions secrets. When release signing secrets are configured, the workflow computes the signing certificate SHA-256 digest and injects it into `BuildConfig.EXPECTED_SIGNING_CERT_SHA256`.

At startup, `SignatureVerifier` compares the installed APK signing certificate against that expected digest. If it does not match, the app exits. Debug builds and release builds without the expected digest skip this check.

This does not make modification mathematically impossible, but it raises the effort required and protects normal installed builds from simple re-signing.

## GitHub Secrets For Signed Release APK

To generate a signed release artifact, configure these repository secrets:

- `RELEASE_KEYSTORE_BASE64`: base64 content of the `.jks` or `.keystore` file
- `RELEASE_KEYSTORE_PASSWORD`: keystore password
- `RELEASE_KEY_ALIAS`: key alias
- `RELEASE_KEY_PASSWORD`: key password

When these secrets are missing, GitHub Actions still builds the debug APK. The signed release APK is skipped.