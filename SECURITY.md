# Security policy

Daftar keeps personal financial records, so security reports are welcome and taken seriously.

## Reporting a vulnerability

Please report privately. Do not open a public issue for a security problem.

- Use GitHub's **Report a vulnerability** button on the repository's Security tab, or
- email **mahmoudibrahimabdulfattah@gmail.com** with "Daftar security" in the subject.

Include the platform and build (release or debug), the steps to reproduce, and what an attacker gains. Expect an
acknowledgement within a few days. Please give a reasonable time to fix the issue before disclosing it.

Only the latest code on `master` is supported.

## What Daftar protects against

- **A lost or stolen locked device, or copied app files.** The database is encrypted with a random key. That key is
  wrapped by a key derived from the PIN (Argon2id) and the result is sealed by the platform's hardware protector
  (Android Keystore, iOS Keychain and Secure Enclave, macOS Secure Enclave). The files alone are not enough to open
  the vault on another device.
- **PIN guessing.** Wrong PINs are backed off persistently, and the delay survives restarts.
- **Shoulder surfing and screen capture.** Amounts can be masked in one tap. Android uses `FLAG_SECURE`, desktop
  windows are excluded from capture, and iOS hosts the app in a capture-excluded layer (not yet verified on a real
  iPhone). The app is covered in the app switcher and locks when it leaves the foreground.
- **Exposed backups.** `.dftar` exports are encrypted with a separate password (Argon2id and XChaCha20-Poly1305) and
  authenticated; cloud backups store the same ciphertext. Signing in never unlocks the vault.

## Out of scope

- A device that is unlocked and already compromised (malware, root or jailbreak, a debugger attached to the app).
- Someone who knows the PIN, or the backup password, or holds both the recovery key and the device.
- Debug builds, which may use software keys and show a banner saying so. Release builds refuse to run without
  hardware key protection; Windows release builds refuse until TPM protection is implemented.
- Price data shown by the app. Prices come from public sources, are labelled with their source and time, and never
  leave the device with any personal data.
