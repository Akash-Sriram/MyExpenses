# MyExpenses Development & Build Instructions

## APK Build & Test Guidelines
- **Use Debug for Testing & Verification**: For any APK build, use the debug variant (`./gradlew assembleInternDebug` -> `com.myexpenses.debug`) to test, reproduce, and fix issues.
- **No Release Builds Without Explicit Request**: NEVER build or assemble release APKs (`assembleInternRelease`) unless the user explicitly requests it (e.g., for a release or version publish).
- **Do Not Uninstall Release Build**: NEVER uninstall the user's release build (`com.myexpenses`) from their device. Keep their production app and data intact. Test and install using the debug package (`com.myexpenses.debug`) only.
