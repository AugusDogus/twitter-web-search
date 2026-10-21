# Validation

## Final bundle

- File: `newx-web-search-1.0.0.mpp`
- SHA-256: `98acf0aecc0e821be31f6e899c9c33e6b25064b119b748dde7987126595162ae`
- API: Morphe Patcher 1.12.0, matching Morphe Manager 1.29.0.
- Build: `./gradlew :patches:build :extension:test --no-daemon`, successful.
- Tests: three resolver tests and four Robolectric Android tests, all passed.

## Target APKs

| Target | Original input SHA-256 | Result |
| --- | --- | --- |
| Installed X 12.25.2-prod.01, Piko 3.24.2 | `2cdac12a015444c5df430c4f7809fed84d37f9bf07b5dc964b17e415ae482e77` | Applied and saved |
| X 12.35.0-prod.01 APKM | `16607ce5e435eccb23b8b8385cbd173e672ff06f533f159e7acf2412087d6f3b` | Applied and saved |
| X 12.36.1-prod.01 APKM | `0bef573e442f4aa7dd6111467859b97644d6e3bd2c8d6aadff3cffc175e1d6d2` | Applied and saved |

The newer inputs were downloaded from APKMirror's arm64-v8a + armeabi-v7a, Android 9+, 480-640dpi variants. Morphe merged the APKM splits before patching.

Each final run used `./patch-twitter.sh INPUT OUTPUT`, without `--force` or `--continue-on-error`. All reported `Applied: NewX: Web search selected text` and `Saved to`. Final outputs are the unsigned `twitter-12.25.2-web-search-final.apk`, `twitter-12.35.0-web-search-final.apk`, and `twitter-12.36.1-web-search-final.apk` in the workspace's `artifacts` directory.

## Checks

- Resolver fixtures cover stripped Kotlin parameter names, swapped callback argument positions, renamed owners, missing/ambiguous menus, and a missing click-handler switch arm.
- Android tests verify the standard web-search intent, exact Unicode/query preservation, absence of a hardcoded provider, blank-selection handling, menu identity/order/label, and a useful message when no handler is installed.
- `tools/verify-apk.py ORIGINAL.apk PATCHED.apk` passed on all three outputs. It checks the final emitted DEX, preserves every other selection method, validates the factory invoke/result/field-store sequence, and checks the menu call's identity and ordering registers.
- The installed APK comparison confirmed `resources.arsc` and every other non-DEX entry are unchanged. Existing Piko resources and native libraries remain intact.
- The helper implements the existing `Function1` interface and does not reference the obfuscated `Unit` singleton or Android's clipboard manager.
- Read-only package inspection found web-search handlers on the phone, including Chrome and the Google app.

## Device validation, October 21, 2026

- Morphe Manager updated to 1.35.0; custom MPP imported as a local source.
- Original 12.35.0 APKM selected with 45 Piko 3.59.0 patches and NewX Web Search 1.0.0. Legacy Piko 3.10.0 patches excluded.
- Manager reported all 46 patches applied successfully.
- Installed through Android's standard installer as an in-place update. Package inspection confirmed `com.twitter.android` version 12.35.0-prod.01, version code 312350001. Existing sign-in remained intact.
- Long-pressed “PHP” in a post. Menu showed Copy, Web Search, Mute, Select all.
- Web Search opened Android's handler chooser. Selected Google with “Just once”; results showed the exact selected query “PHP”. No default handler changed.

## Limits

Copy, Mute and Select all remained visible, but their actions were not exercised on the device. No account mutations were performed for verification.

The full upstream Piko Gradle build could not resolve its GitHub-packaged settings plugin with the available credentials. This standalone companion bundle avoids that dependency and does not change or republish Piko's bundle.
