# Android CI/CD

`.github/workflows/android.yml` builds Debug APKs on `main` pushes, pull requests
against `main`, and manual dispatch. Only a version-tag **push** can build and
publish a signed Release APK. Manual dispatch never publishes, even on a tag.
Operational instructions: `docs/ci-cd.md`.

- Toolchain: committed Gradle 9.5.0 wrapper, AGP 9.3.2, Temurin Java 25 to match
  `gradle/gradle-daemon-jvm.properties`, `platforms;android-37.0`, Build Tools 36.0.0.
  Java 11 bytecode remains unchanged. Use `bash gradlew`, not an executable wrapper.
- Both build jobs run `testDebugUnitTest` and `lint`. Debug uploads
  `app/build/outputs/apk/debug/app-debug.apk`; Release requires the signed
  `app/build/outputs/apk/release/app-release.apk`, verifies it with `apksigner`, and
  publishes `Eta-<tag>.apk` with GitHub-generated release notes. Missing APKs fail.
- Debug signing: the optional `ANDROID_DEBUG_KEYSTORE_BASE64` secret holds Daniel's
  `~/.android/debug.keystore` (SHA-1 `9B:01:…:5D:9B`, password `android`). The debug
  job restores it to the Android user home before `assembleDebug` and fails if the
  APK's signer differs. Without it (foreign-fork PRs) the runner generates a throwaway
  key per run: such APKs update no other install and cannot use Google Calendar.
- Canonical tags: `vMAJOR.MINOR.PATCH`, no leading zeroes or suffixes; major
  0..2099, minor/patch 0..999. `ETA_RELEASE_VERSION` supplies the version without
  `v`. Gradle validates it and maps `versionCode = major*1_000_000 + minor*1_000 +
  patch + 1`, maximum 2_100_000_000. Versions must be released in increasing order.
  Without the variable, local builds retain `versionName=1.0`, `versionCode=1`.
- Signing: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`,
  `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` are Actions secrets. The workflow
  reconstructs the keystore under `RUNNER_TEMP`, exports `ANDROID_KEYSTORE_PATH`,
  and removes it on step exit, including build failure. No secrets in logs,
  arguments, artifacts, repository files, or writeable Release Gradle caches.
  Release uses `--no-configuration-cache` and read-only dependency caching.
- Gradle only signs Release when all four path/password/alias/password variables
  are provided. Partial signing environment or a missing file fails clearly;
  absence of all values preserves unsigned local releases. Local signed builds
  must also disable the configuration cache.
- Build jobs have `contents: read`; a separate artifact-only publisher has
  `contents: write`. It neither checks out source nor receives keystore secrets.
  Checkout credentials are not persisted. Actions are pinned to full SHAs.
- Do not generate a new release key per build or version. The permanent private
  key and credentials are stored outside the repository with restrictive file
  permissions; make encrypted backups. Existing installations require their old
  certificate even though `applicationId` remains `com.example.erik_iteration_2`.
  Never uninstall to work around a signature mismatch. Calendar authorization
  requires this certificate's SHA-1 in an Android OAuth client.
- The configured repository is the `origin` fork `Roden69/ETA`; upstream has its
  own secrets. Never create production version tags merely to test the pipeline.
  Existing releases are not overwritten by re-runs. Release runs are not cancelled
  when other versions are pushed.
- The debug job also runs `assembleFork` (build type `fork`: application-id suffix
  `.fork`, `app_name` "Eta Fork", debug signing) and uploads `eta-fork-apk`
  (`app/build/outputs/apk/fork/app-fork.apk`). Its signer is verified against the
  shared debug key like the debug APK. The default debug and release identity is
  unchanged. Testers: `docs/ci-cd.md` ("Für Tester").
- Issue-Bot: `issue-triage.yml` (opened/edited/reopened) only reads the issue through
  the API: duplicate search on title words (two shared words needed), repro checklist
  for `bug` issues, one bot comment, labels `possible-duplicate` and `needs-repro`
  (created on demand). `issue-fix.yml` runs only on the label `fix-freigegeben`, and
  only for actors with admin, maintain or write permission. Without the secret
  `ANTHROPIC_API_KEY` it comments the setup and stops. Otherwise
  `anthropics/claude-code-action` (pinned to v1.0.252, commit f27be6b6…) works on
  `fix/issue-<N>` and opens a PR against `main`. Issue text is untrusted data; it never
  enters a shell step. The model gets only title and body from the event payload (the
  text a maintainer approved), no comments or later edits, and the tool allow-list
  names exact commands: the push only to `fix/issue-<N>` of this issue, Gradle only as
  the CI gate, no `cat`.
  Nothing is pushed to `main` by the workflow; that protection is a branch ruleset.
  Pull requests created with `GITHUB_TOKEN` start no `pull_request` workflows, so the
  Android run for such a PR is started by hand on its branch.
