# AnimeXstream Android App

Kotlin + Jetpack Compose Anime Streaming App

## Features
- Real anime data from Cloudflare Worker API
- Firebase Auth (REST) - same project as website
- Firestore-backed Community feed with @mentions
- GitHub image upload for community posts
- ExoPlayer HLS streaming with multi-server support
- Material 3 dark purple theme

## Build via GitHub Actions
Har push par APK automatically build hoti hai.

1. GitHub -> Actions tab -> latest run kholo
2. Neeche **Artifacts** section se **AnimeXstream-debug-apk** download karo
3. APK phone mein install karo

## Manual trigger
Actions tab -> Build APK -> Run workflow

## Local build (slow on low-end laptops)
```
./gradlew assembleDebug
```
APK location: `app/build/outputs/apk/debug/app-debug.apk`

## Tech Stack
- Kotlin 2.0, Compose BOM 2024.09
- Navigation-Compose, Coil, OkHttp, Media3 (ExoPlayer)
- Firebase Auth + Firestore (REST only, no native SDK)
- minSdk 24, targetSdk 34
