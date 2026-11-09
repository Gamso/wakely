# Third-party notices

Wakely is released under the MIT License (see `LICENSE`). It ships the following
third-party components, each under its own license. The full license texts are
bundled in the app (`app/src/main/assets/licenses/`) and shown in **Réglages → À propos**.

| Component | Copyright | License | Used for |
|---|---|---|---|
| Android alarm sounds | The Android Open Source Project | Apache 2.0 | `app/src/main/res/raw/aosp_*.ogg` |
| Material Design icons | Google | Apache 2.0 | Interface icons |
| AndroidX / Jetpack Compose | The Android Open Source Project | Apache 2.0 | UI, activities, lifecycle, ViewModel |
| Kotlin standard library, kotlinx.coroutines | JetBrains s.r.o. and contributors | Apache 2.0 | Language runtime, coroutines |
| Jollyday holiday data | The Jollyday project (focus-shift) and contributors | Apache 2.0 | `app/src/main/assets/holidays/` |

## Android Open Source Project alarm sounds

Files: `app/src/main/res/raw/aosp_*.ogg`

Source: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/data/sounds/
(`alarms/ogg/*.ogg` and `Alarm_*.ogg`), downloaded unmodified and renamed to lowercase
`aosp_<name>.ogg` to satisfy Android resource naming rules.

Copyright (C) The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use these files except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

## Jollyday holiday data

Files: `app/src/main/assets/holidays/` (`Holidays_<country>.xml`, `holiday_descriptions*.properties`)

Source: https://github.com/focus-shift/jollyday (see `SOURCE.txt` in that folder for the exact commit),
copied unmodified by `tools/update_holidays.py`. The holiday dates are computed by the app from these rules.

Copyright (C) The Jollyday project and contributors

Licensed under the Apache License, Version 2.0, the same text as above.
