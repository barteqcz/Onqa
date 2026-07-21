<img width="150" alt="dfggdfgdfdg" src="https://github.com/user-attachments/assets/4b919397-4e25-400f-9b5e-356125befe10" />
<br>

# Onqa

There’s definitely something magical about discovering the local radio ether, that is different in every place, what makes each place unique. However, FM radio is often unavailable in new phones. 

As an IT enthusiast, radio enthusiast and a DXer, I’ve always loved exploring the airwaves wherever I go. I feel like life would be much sadder without radio. Yet terrestrial broadcasting is the most magical way of enjoying it, sometimes you just want something more portable - straight on your phone, with an amazing UI, polished [maps ecosystem](https://onqa.barteq.cz/) and many features.

That’s where **Onqa** comes from. It's not meant to be a replacement for terriestrial broadcasting. It's meant to do a similar thing - allow the users to discover their local radio ether.

The name is inspired by the Spanish words *onda* (“wave”) and *cerca* (“nearby”), reflecting the idea of discovering the radio stations around you.

## Screenshots

<img width="300" alt="Screenshot_20260721_165240_Onqa" src="https://github.com/user-attachments/assets/ac3c6cd5-0edd-40e5-b5ee-8f3fcd22eece" />
&nbsp;
<img width="300" alt="Screenshot_20260721_165248_Onqa" src="https://github.com/user-attachments/assets/2b2278e5-3551-4675-ad34-ab925c44ecca" />

## Features

- **Location-Based Discovery**: Automatically finds and prioritizes radio stations near your current location using background location services.
- **High-Quality Streaming**: Support for HQ audio streams to provide a crystal-clear listening experience.
- **Material UI**: A beautiful UI that can also adapt to your device's wallpaper and theme colors.
- **Adaptive Themes**: Full support for Light and Dark modes, with customizable accent colors.
- **Seamless Media Experience**: Built with Android Media3 for robust background playback, lock screen controls, and system integration.
- **Favorites**: Save your most-listened-to stations for instant access.
- **Modern Mini Player**: Control your music effortlessly while navigating the app.
- **Intelligent Connectivity**: Monitoring network status to ensure smooth playback transitions between Wi-Fi and mobile data.

## Tech Stack

- **UI**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with [Material 3](https://m3.material.io/)
- **Architecture**: MVVM with Clean Architecture principles
- **Dependency Injection**: [Hilt](https://developer.android.com/training/dependency-injection/hilt-android)
- **Media**: [Media3 (ExoPlayer)](https://developer.android.com/guide/topics/media/media3)
- **Networking**: [Retrofit](https://square.github.io/retrofit/) & [OkHttp](https://square.github.io/okhttp/)
- **Serialization**: [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization)
- **Concurrency**: [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) & [Flow](https://kotlinlang.org/docs/flow.html)
- **Storage**: [Jetpack DataStore](https://developer.android.com/topic/libraries/architecture/datastore)
- **Image Loading**: [Coil](https://coil-kt.github.io/coil/)
- **Location**: [Google Play Services Location](https://developers.google.com/android/guides/setup)
- **Logging**: [Timber](https://github.com/JakeWharton/timber)
