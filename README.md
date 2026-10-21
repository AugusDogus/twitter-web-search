# NewX Web Search

A standalone Morphe patch that replaces **Ask Grok** in X's post text-selection menu with Android's **Web Search** action. Copy, Mute and Select all keep their existing behavior. The search uses the selected text and Android's search-app routing, without reading or changing the system clipboard or choosing a search provider.

## Compatibility

| X version | Validation |
| --- | --- |
| 12.25.2-prod.01 | Applied to an existing Piko 3.24.2 installation APK; output DEX verified |
| 12.35.0-prod.01 | Built and installed with Piko 3.59.0 in Morphe Manager 1.35.0; Web Search tested on Android 17 |
| 12.36.1-prod.01 | Standalone patch applied; output DEX verified |

For use with **Piko 3.59.0**, choose **X 12.35.0-prod.01**. Piko supports 12.34.0 and 12.35.0; this companion's support for 12.36.1 does not establish compatibility for Piko's other patches. These results were checked on October 21, 2026. Future X versions need fresh validation.

Piko releases: [crimera/piko-newx](https://github.com/crimera/piko-newx). Source: [crimera/piko, x-lite branch](https://github.com/crimera/piko/tree/x-lite). This is an independent companion patch.

## Download

Open this repository's **Actions → Build patch**, choose a successful run on `main`, and download the **newx-web-search** artifact. Extract the ZIP to get the `.mpp` file and `SHA256SUMS`. GitHub requires sign-in to download workflow artifacts.

The workflow runs on pushes to `main`, pull requests, and manual dispatch. It builds the patch and runs its tests without requiring an X APK or signing key. App compatibility still requires testing against an actual APK and on a device.

## Use in Morphe Manager

Copy the downloaded `.mpp` file to the phone. For a local build, the file is `patches/build/libs/newx-web-search-1.0.0.mpp`.

1. Open **Sources → Add → Local** and select the `.mpp` file.
2. Select X and enable **NewX: Web search selected text**. Use the manager's patch selection view to select this source, alongside any Piko patches appropriate for your input APK.
3. Patch, then use Morphe's install flow. Reusing your existing Morphe signing key is necessary for an in-place update of an app signed with that key.

Supported versions: **12.25.2-prod.01**, **12.35.0-prod.01**, **12.36.1-prod.01**. The patch can be applied by itself to the currently installed Piko-patched 12.25.2 APK; this was explicitly checked. Do not reapply Piko's complete patch set to an already patched APK.

Installed and verified on a Pixel 8 Pro running Android 17 using Morphe Manager 1.35.0: 45 Piko 3.59.0 patches plus this patch applied successfully to the original 12.35.0 APKM. The update preserved the existing signed-in session. Selecting “PHP” from a post displayed Web Search in place of Ask Grok and opened Google results for exactly “PHP” through Android’s app chooser. Copy, Mute and Select all remained visible; their actions were not exercised.

## Build

Requires JDK 21, Android SDK platform 36, `curl`, and `sha256sum`. Set `ANDROID_HOME` if your SDK is outside `~/Android/Sdk`.

```sh
./prepare-deps.sh
./gradlew :patches:build :extension:test --no-daemon
./patch-twitter.sh /absolute/path/to/twitter.apk /absolute/path/to/output.apk
```

The patch script also accepts APKM bundles. It produces an **unsigned APK for inspection**, never installs it, and refuses to overwrite an existing output. Use Morphe Manager for signing and installation.

Build dependencies come from public release assets and Maven repositories. No GitHub credentials are required. The pinned Morphe Desktop 1.15.0 dependency supplies the Patcher 1.12.0 API used to build this bundle. The bundle also imported and applied successfully in Morphe Manager 1.35.0. The MPP includes desktop classes, Android DEX, and a separate app extension DEX.

## How it matches

The resolver finds the `text_selection_action_ask_grok` resource and the corresponding `Menu.add` call in an `ActionMode.Callback2`. It derives the menu ID, follows that ID's switch arm to the selected callback field, then finds the constructor assignment to that field. Both targets must resolve uniquely before mutation.

The patch replaces one menu call and inserts one paired invoke/result before the callback assignment. It uses typed dexlib instructions and existing registers. It does not rely on obfuscated names, fixed menu IDs, version-dependent routing, or retained Kotlin parameter names. Resource IDs are read directly from `resources.arsc`; the resource table is not edited.

The extension uses `Intent.ACTION_WEB_SEARCH` with `SearchManager.QUERY`. Android supplies the localized menu label where available, with an English fallback. If Android cannot launch a search handler, a message explains how to recover. The extension avoids `kotlin.Unit.INSTANCE`, which X's R8 build renames.

See [VALIDATION.md](VALIDATION.md) for exact artifacts, checks and limits.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
