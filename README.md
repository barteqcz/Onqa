# Loqa

**Loqa** is a location-aware radio streaming application for Android that seamlessly switches between radio stations as you move. It ensures you always have the best local or network-affiliated station playing, no matter where your journey takes you.

## Features

- **Location-Based Discovery**: Automatically finds and lists radio stations near your current location.
- **Smart Station Handoff**: The core "Loqa" feature — the app monitors your location in the background and automatically switches to a nearby station or an affiliated network station if you move out of coverage.
- **Modern UI**: Built entirely with **Jetpack Compose**, featuring a sleek, dark-themed interface with **Material You** dynamic color support.
- **High-Quality Streaming**: Powered by **Android Media3 (ExoPlayer)**, supporting various formats including HLS and DASH.
- **Background Playback**: Full integration with Android Media Session, providing rich notifications and lock screen controls.
- **Smart Metadata**: Displays real-time song and artist information (E-RDS) where available.
- **Favorites**: Mark your favorite stations for quick access and priority in the handoff logic.
- **Seamless Fading**: Audio fades in and out during station transitions and when pausing/resuming for a premium listening experience.


> **Note**: This app requires Location permissions (including Background Location) to perform the automatic station switching feature.

## Tech Stack

- **UI**: [Jetpack Compose](https://developer.android.com/jetpack/compose)
- **Playback**: [Media3 / ExoPlayer](https://developer.android.com/guide/topics/media/media3)
- **Dependency Injection**: [Hilt](https://dagger.dev/hilt/)
- **Networking**: [Retrofit](https://square.github.io/retrofit/) & [Kotlinx Serialization](https://kotlinlang.org/docs/serialization.html)
- **Location**: [Google Play Services Location](https://developers.google.com/android/guides/setup)
- **Image Loading**: [Coil](https://coil-kt.github.io/coil/)
- **Architecture**: MVVM with Clean Architecture principles
- **Local Storage**: [DataStore Preferences](https://developer.android.com/topic/libraries/architecture/datastore)

## Screenshot

<img width="300" alt="Screenshot_20260527_105523_Hopp" src="https://github.com/user-attachments/assets/061c95a0-80e8-4d44-9ecf-eed3fb63cf44" />

## Getting Started with development

### Prerequisites

- Android Studio Ladybug or newer
- Android SDK 37 (Compile SDK)
- Minimum Android version: API 24 (Nougat)

### Set-up

1. Clone the repository:
   ```
   git clone https://github.com/barteqcz/Loqa.git
   ```
2. Open the project in Android Studio.
3. Run the `app` module on your device or emulator.

## Project Structure

- `ui`: Compose screens, ViewModels, and theme.
- `data`: Repositories, API services, and data models.
- `service`: `PlaybackService` handling Media3 integration and background location monitoring.
- `location`: `LocationClient` for managing location updates.
- `util`: Helpers for connectivity, address refining, and more


## Roadmap

- Google Play Store release
- Extending the database
