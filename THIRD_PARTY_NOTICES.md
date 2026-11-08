# Third-party notices

Wakely is released under the MIT License (see `LICENSE`). It ships the following
third-party components, each under its own license. The full license texts are
bundled in the app (`app/src/main/assets/licenses/`) and shown in **Réglages → À propos**.

| Component | Copyright | License | Used for |
|---|---|---|---|
| Android alarm sounds | The Android Open Source Project | Apache 2.0 | `app/src/main/res/raw/aosp_*.ogg` |
| Material Design icons ("settings", "arrow_back") | Google | Apache 2.0 | `app/src/main/res/drawable/ic_settings.xml`, `ic_arrow_back.xml` |
| AndroidX / Jetpack Compose | The Android Open Source Project | Apache 2.0 | UI, activities, lifecycle, ViewModel |
| Kotlin standard library, kotlinx.coroutines | JetBrains s.r.o. and contributors | Apache 2.0 | Language runtime, coroutines |

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
