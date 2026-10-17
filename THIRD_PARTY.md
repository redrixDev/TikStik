# Third-party code and assets

TikStik is GPL-3.0-or-later (see `LICENSE`), with an additional permission to link Google Play services (see `README.md`). It includes or depends on the following third-party work, each under its own license. The complete, generated list of every library inside the app, with license texts, is in `app/src/main/res/raw/aboutlibraries.json` and in the app under Settings → Licenses.

## Bundled in the source tree

| What | Where | License |
|---|---|---|
| libwebp 1.6.0 | `app/src/main/cpp/vendor/libwebp-1.6.0` | BSD 3-Clause (Google) |
| giflib 6.1.3 | `app/src/main/cpp/vendor/giflib-6.1.3` | MIT |
| Plus Jakarta Sans | `app/src/main/res/font/plus_jakarta_sans.ttf` | SIL Open Font License 1.1 |

## Libraries (Gradle dependencies)

| Library | License | Notes |
|---|---|---|
| libadb-android | Apache-2.0 (dual-licensed with GPL-3.0-or-later) | Parts derived from Cameron Gutman's work are BSD-3-Clause. |
| spake2-android | LGPL-3.0 | Kept unmodified and unmerged by R8 (`app/src/main/keepRules/spake2.keep`) so it can be replaced, as the LGPL requires. |
| Conscrypt | Apache-2.0 | |
| Bouncy Castle (bcprov) | MIT | |
| Coil | Apache-2.0 | |
| AboutLibraries | Apache-2.0 | Generates the in-app license list. |
| AndroidX, Jetpack Compose, Hilt, Room, WorkManager, Kotlin and kotlinx | Apache-2.0 | |
| Google Play services (auth) | Android Software Development Kit License | Used only for the optional Google Drive backup, under the additional permission in `README.md`. |

## Brand

The name TikStik, its logo and icons are not covered by the GPL. All rights reserved.
