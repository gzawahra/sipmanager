# 📲 Baresip Android Integration

## 🛠️ Setup

1. **Clone the project**
   ```bash
   git clone https://git.app-magneta.fr/emerit/sip/emerit-sip-app.git
   cd <project-folder>
   ```

2. **Install the appropriate version of the Android NDK**

   This project uses Android NDK version `27.2.12479018`.  
   Make sure to download and install this version via Android Studio's SDK Manager  
   or manually from the official [NDK archives](https://developer.android.com/ndk/downloads).

3. **Update the NDK path in the Makefile**

   Open the `Makefile` located in the `baresip` module,  
   and replace the values of `NDK_VERSION` and `NDK_PATH` with your local configuration:
   ```make
   NDK_VERSION := <your-ndk-version>
   NDK_PATH := <your-ndk-path>
   ```

---

## ⚙️ Compilation

1. **Navigate to the `baresip` module**
   ```bash
   cd baresip
   ```

2. **Download dependencies**
   ```bash
   make download-dependencies
   ```
   This command fetches and installs all required third-party dependencies for building `baresip`.

3. **Build native libraries**
   ```bash
   make generate
   ```
   This command will:
    - Compile and link the `libbaresip` native library
    - Target the following Android architectures:
        - `armeabi-v7a`
        - `arm64-v8a`

---

## 📦 Build the Android App

Once the native libraries are compiled, the Android application is ready to be built using **Android Studio** or the **Gradle CLI**:
```bash
./gradlew assembleRelease
```

---

## 🧾 Configuration

SIP configuration can be found at:

```
app/src/main/assets/config
```

- SIP server and account behavior
- Audio input/output and codec settings
- Network, media transport, and security protocols
---

## 📦 Libraries & Licenses

This project relies on several third-party open-source libraries. Below is a list of the main dependencies and their associated licenses:

| Library           | Repository URL                                                                 | License        |
|------------------|----------------------------------------------------------------------------------|----------------|
| abseil-cpp        | https://github.com/abseil/abseil-cpp                                            | Apache 2.0     |
| amr               | https://git.code.sf.net/p/opencore-amr/code.git                                 | Apache 2.0     |
| baresip           | https://github.com/baresip/baresip                                              | BSD-3-Clause   |
| bcg729            | https://github.com/BelledonneCommunications/bcg729                              | GPL-3.0        |
| codec2            | https://github.com/drowe67/codec2                                               | LGPL-2.1       |
| g7221             | https://github.com/juha-h/libg7221                                              | BSD-3-Clause   |
| openssl           | https://github.com/openssl/openssl                                              | Apache 2.0     |
| opus              | https://github.com/xiph/opus                                                    | BSD            |
| re                | https://github.com/baresip/re                                                   | BSD-3-Clause   |
| sndfile           | https://github.com/juha-h/libsndfile                                            | LGPL-2.1       |
| spandsp           | https://github.com/juha-h/spandsp                                               | LGPL-2.1       |
| tiff              | https://gitlab.com/libtiff/libtiff                                              | HPND (libtiff) |
| webrtc            | https://github.com/juha-h/libwebrtc                                             | BSD            |
| vo-amrwbenc       | https://git.code.sf.net/p/opencore-amr/vo-amrwbenc.git                          | Apache 2.0     |
| zrtpcpp           | https://github.com/juha-h/ZRTPCPP                                               | GPL-3.0        |

