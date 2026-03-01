# UnderwaterAI — Photo Enhancer

An Android application that runs on-device, offline AI super-resolution to
enhance photos captured underwater (or any photo, really).  
It ships five models from
[OpenModelDB](https://openmodeldb.info) converted to PyTorch Mobile format.

---

## Features

- **On-device inference** — no data leaves the phone, no cloud dependency.
- **Multiple enhancement models** — General, PurePhoto, Rybu, AnimeVideo,
  Nomos8k SPAN.
- **Vulkan GPU acceleration** — on supported Adreno / Mali devices the fastest
  model completes in < 2 s.
- **Interactive before/after slider** — compare original vs enhanced side-by-side.
- **Material Design 3** — full dark-mode support, dynamic colour, Compose UI.

---

## Project Structure

```
uwater_app/
├── app/
│   ├── build.gradle.kts           # App-level Gradle config (SDK, deps, signing)
│   ├── proguard-rules.pro         # R8 / ProGuard keep rules
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── assets/            # .pt model files go here (see models.md)
│           ├── java/com/underwaterai/enhance/
│           │   ├── MainActivity.kt
│           │   ├── UnderwaterAIApp.kt
│           │   ├── model/         # ViewModel, ImageEnhancer, ModelType
│           │   ├── ui/            # Compose screens and components
│           │   └── utils/         # HardwareProfiler, PerformanceLogger
│           └── res/               # Drawables, strings, themes
├── convert_models.py              # Convert OpenModelDB .pth → mobile .pt
├── test_model.py                  # Desktop inference test harness
├── models.md                      # ⬅ Model download + conversion guide
├── build.gradle.kts               # Root Gradle config
├── gradle.properties
├── settings.gradle.kts
└── README.md                      # This file
```

---

## Requirements

### Build environment

| Tool | Minimum version |
|------|----------------|
| Android Studio | Hedgehog (2023.1.1) or newer |
| Android SDK | API 34 (Android 14) |
| Android NDK | 26.x |
| JDK | 17 (bundled with Android Studio) |
| Gradle | 8.4 (wrapper included) |
| Kotlin | 1.9.22 |

The project targets **minSdk 26** (Android 8.0 Oreo) and uses the **arm64-v8a**
and **armeabi-v7a** ABI filters.

### Model files

The `.pt` model files are **not included** in this repository.  
Follow [models.md](models.md) to download, convert, and place them in
`app/src/main/assets/` before building.

---

## Building the App

### 1 — Clone the repository

```bash
git clone git@github.com:Underwater-AI/uwater_app.git
cd uwater_app
```

### 2 — Set up models

Follow the complete instructions in **[models.md](models.md)**.  
TL;DR:

```bash
# Create Python venv and install deps
python -m venv .venv && source .venv/bin/activate
pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu
pip install spandrel

# Download .pth models into models/ (see models.md for exact URLs)

# Convert to mobile format
python convert_models.py

# Copy into assets
cp models/mobile/*.pt app/src/main/assets/
```

### 3 — Open in Android Studio

1. Open Android Studio → **Open** → select the `uwater_app/` directory.
2. Let Gradle sync finish (first sync downloads ~400 MB of dependencies).
3. In **SDK Manager** verify you have Android SDK Platform 34 and NDK 26.x.

### 4 — Build via Android Studio

| Goal | Menu path |
|------|-----------|
| Debug APK | **Build → Build Bundle(s) / APK(s) → Build APK(s)** |
| Release APK | **Build → Generate Signed Bundle / APK → APK → follow wizard** |

### 4 — Build from the command line

```bash
# Debug APK (unsigned, debuggable)
./gradlew assembleDebug

# Release APK (signed, minified, obfuscated)
./gradlew assembleRelease \
  -Pandroid.injected.signing.store.file=/path/to/release.keystore \
  -Pandroid.injected.signing.store.password=YOUR_STORE_PASS \
  -Pandroid.injected.signing.key.alias=underwaterai \
  -Pandroid.injected.signing.key.password=YOUR_KEY_PASS
```

Output locations:

| Build type | Path |
|------------|------|
| Debug | `app/build/outputs/apk/debug/app-debug.apk` |
| Release | `app/build/outputs/apk/release/app-release.apk` |

### 5 — Create a signing keystore (first time)

```bash
keytool -genkeypair \
  -alias underwaterai \
  -keyalg RSA -keysize 2048 \
  -validity 10000 \
  -keystore release.keystore \
  -storepass YOUR_STORE_PASS \
  -keypass YOUR_KEY_PASS \
  -dname "CN=UnderwaterAI, OU=Engineering, O=UnderwaterAI, L=Unknown, S=Unknown, C=US"
```

> **Security:** Never commit `release.keystore` or `keystore.properties` to
> version control.  Use environment variables or a secrets manager in CI.

---

## Release Build Security

The release variant is configured with:

- **R8 full-mode** shrinking (`isMinifyEnabled = true`) — removes all unused
  classes, methods, and fields.
- **Resource shrinking** (`isShrinkResources = true`) — strips unused drawables /
  strings.
- **Obfuscation** — R8 renames all classes, methods, and fields to single letters.
- **Additional ProGuard rules** in `app/proguard-rules.pro`:
  - PyTorch Mobile JNI keeps.
  - Compose reflection keeps.
  - String encryption via `-adaptresourcefilenames` and `-repackageclasses`.
- The resulting APK is resistant to standard Java decompilers — meaningful class
  and method names are irretrievably lost.

To verify obfuscation worked:

```bash
# Install jadx  https://github.com/skylot/jadx
jadx app/build/outputs/apk/release/app-release.apk -d /tmp/decompiled
# You should see classes named a, b, c, d ... not readable class names.
```

---

## Running on a Device

```bash
# Install debug build on a connected device / emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Stream logcat filtered to the app
adb logcat -s "UnderwaterAI" "PyTorch"
```

---

## Development Tips

### Hardware profiler

`HardwareProfiler.kt` detects the GPU vendor at startup and selects the
appropriate inference backend (Vulkan vs CPU).  Logs are tagged `UnderwaterAI`.

### Adding a new model

1. Add a `ModelType` entry in `model/ModelType.kt` with the asset filename and
   display name.
2. Add a conversion entry in `convert_models.py`.
3. Run the conversion, copy the `.pt` to `app/src/main/assets/`.
4. The `HomeScreen` model selector picks it up automatically.

---

## License

© Underwater-AI. All rights reserved.
