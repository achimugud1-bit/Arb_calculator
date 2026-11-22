# ARBMASTER PRO — Native Android App (Kotlin + Jetpack Compose)

This is a complete native Android application rebuilt from `ARBMASTER-PRO-source (1).zip` with:
- Exact 24-decimal `BigInteger` fixed-point arbitrage math engine (`ArbMath.kt`)
- Live Upcoming Arbitrage Search connected to the live scanner backend
- Private on-device Bet Tracker & Money Tracker (`SharedPreferences` JSON storage)
- Built-in Static Analysis Code Audit viewer
- Dark Precision Terminal Material 3 UI with 5-tab bottom navigation

## Option 1: Build Your Installable `.apk` Automatically on GitHub (No Android Studio Required)

1. Create a new repository on GitHub and upload the contents of this folder (including `.github/workflows/build-android-apk.yml`).
2. Open the **Actions** tab on GitHub and run **Build ARBMASTER PRO Android APK**.
3. After ~2 minutes, download the **`ARBMASTER-PRO-Android-APK`** artifact (`ARBMASTER-PRO-debug.apk`), transfer it to your Android phone, and tap to install.

## Option 2: Build Locally in Android Studio

1. Open this folder in **Android Studio** (Ladybug or newer, JDK 17).
2. Let Gradle sync, then choose **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
3. Install `app/build/outputs/apk/debug/app-debug.apk` onto your Android phone via USB or direct file transfer.
