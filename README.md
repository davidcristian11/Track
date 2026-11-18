# Track

An Android app for tracking nutrition, workouts and body progress in one place, built with Kotlin and Jetpack Compose.

## Features

- **Food logging:** search foods or scan a product barcode with the camera. Nutrition data comes from [Open Food Facts](https://world.openfoodfacts.org), and you can also create custom foods and see your recent ones.
- **Daily overview:** calories and macros against your targets, water intake and step count, with a day selector to look back at earlier days.
- **Goals:** suggested calorie and macro targets based on your weight and whether you want to lose, keep or gain weight, which you can adjust.
- **Workouts:** log training sessions and see them per day.
- **Body progress:** weight history, body measurements (waist, chest and more) and progress photos stored on the device.

## How it is built

- **UI:** Jetpack Compose with Material 3 and Navigation Compose, with a shared ViewModel.
- **Storage:** Room database (schema version 6, with tested migrations) and DataStore for settings. All data stays on the device.
- **Barcode scanning:** CameraX and ML Kit. A small stability check only accepts a barcode once it has been read consistently for a short time, which avoids wrong matches from blurry frames.
- **Network:** OkHttp client for the Open Food Facts API, with retries and handling of invalid responses.

## Tests

- **Unit tests** (`app/src/test`): calculations, goal suggestions, barcode stability, food search retries, Open Food Facts parsing and input validation.
- **Instrumented tests** (`app/src/androidTest`): Room DAO and migrations, photo storage and the main screens (nutrition, workouts, measurements, progress).

```bash
./gradlew test                 # unit tests
./gradlew connectedAndroidTest # instrumented tests, needs a device or emulator
```

## Running the app

Open the project in Android Studio and run the `app` configuration. Requires Android 9 (API 28) or newer.
