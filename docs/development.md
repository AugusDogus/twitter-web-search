# Development

## Build

Requires JDK 21, Android SDK platform 36, `curl`, and `sha256sum`. Set `ANDROID_HOME` if your SDK is outside `~/Android/Sdk`.

```sh
./prepare-deps.sh
./gradlew :patches:build :extension:test --no-daemon
./patch-twitter.sh /absolute/path/to/twitter.apk /absolute/path/to/output.apk
```

The patch script also accepts APKM bundles. It produces an **unsigned APK for inspection**, never installs it, and refuses to overwrite an existing output. Use Morphe Manager for signing and installation.

Build dependencies come from public release assets and Maven repositories. No GitHub credentials are required. The pinned Morphe Desktop 1.15.0 dependency supplies the Patcher 1.12.0 API used to build this bundle. The bundle also imported and applied successfully in Morphe Manager 1.35.0. The MPP includes desktop classes, Android DEX, and a separate app extension DEX.

## Releases

Push a tag such as `v1.0.0` matching the version in `build.gradle.kts`. The workflow builds and tests the patch, verifies the version, then publishes a GitHub Release containing the `.mpp` and `SHA256SUMS`. Only the release job has repository write access.

```sh
git tag v1.0.0
git push origin v1.0.0
```

Pushes to `main` and pull requests produce development artifacts in Actions without publishing a release. For future versions, also update the bundle filename in `patch-twitter.sh`.

## How it matches

The resolver finds the `text_selection_action_ask_grok` resource and the corresponding `Menu.add` call in an `ActionMode.Callback2`. It derives the menu ID, follows that ID's switch arm to the selected callback field, then finds the constructor assignment to that field. Both targets must resolve uniquely before mutation.

The patch replaces one menu call and inserts one paired invoke/result before the callback assignment. It uses typed dexlib instructions and existing registers. It does not rely on obfuscated names, fixed menu IDs, version-dependent routing, or retained Kotlin parameter names. Resource IDs are read directly from `resources.arsc`; the resource table is not edited.

The extension uses `Intent.ACTION_WEB_SEARCH` with `SearchManager.QUERY`. Android supplies the localized menu label where available, with an English fallback. If Android cannot launch a search handler, a message explains how to recover. The extension avoids `kotlin.Unit.INSTANCE`, which X's R8 build renames.

See [VALIDATION.md](../VALIDATION.md) for exact artifacts, checks and limits.

