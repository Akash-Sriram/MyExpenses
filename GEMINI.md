# MyExpenses Development & Build Instructions

## APK Build & Test Guidelines
- **Strict Prohibition of Local Compilation**: Host hardware resources (CPU, RAM, battery) are limited. NEVER run local Gradle builds, assemble, or test commands (`./gradlew assemble...`, `./gradlew build`, `./gradlew test...`) on the user's local machine.
- **Cloud-First Testing Pipeline**: All compilation, unit tests, and debug APK assembly MUST run in the cloud via the GitHub Actions debug pipeline (`debug-pipeline.yml`). Commit and push changes to trigger cloud builds or use `gh workflow run debug-pipeline.yml`.
- **Device Installation via Cloud Artifacts**: Once the cloud workflow succeeds, download the generated debug APK using GitHub CLI (`gh run download <run-id> -n MyExpenses-debug -D /tmp/debug-apk`) and install it onto connected devices via ADB (`adb install -r`).
- **No Release Builds Without Explicit Request**: NEVER trigger or assemble release APKs (`assembleInternRelease` or `release.yml` with `v*` tags) unless the user explicitly requests a release or version publish.
- **Do Not Uninstall Release Build**: NEVER uninstall the user's release build (`com.myexpenses`) from their device. Keep their production app and data intact. Test and install using the debug package (`com.myexpenses.debug`) only.
