# Consulta Ambiental · Android

**English** | [Español](README.es.md)

An environmental data client built with Kotlin and Jetpack Compose. It reads Firebase Realtime Database records and downloads Open-Meteo weather data for locations in Costa Rica, with hourly, daily and historical extreme-value analysis, configurable filters and CSV export.

## Screens

The interface uses Spanish labels:

| Screen | Purpose |
| --- | --- |
| Inicio (Home) | Latest ESP32 reading or current weather, hourly forecast and ten-day forecast for the selected location |
| Por hora (Hourly) | Daily hourly averages, variable comparison and an hour selector |
| Por día (Daily) | Daily average comparison and details for a selected date |
| Extremos (Extremes) | Minimum and maximum readings, timestamps and repeated occurrences across the available history |

Hourly and daily queries can plot one or more variables together. Extremes uses a single-variable selector. All three support limit-based filtering and CSV export. Light/dark theme preferences are saved between sessions.

## Requirements

- Android Studio compatible with Android Gradle Plugin **9.4.1**.
- Android SDK Platform **37** and SDK Build Tools **36.0.0**.
- JDK **25** for the Gradle daemon, as specified in `gradle/gradle-daemon-jvm.properties`.
- An emulator or device running Android **8.0 (API 26)** or later.
- Internet access for weather requests. Firebase is optional and requires your own project.

The repository includes the Gradle **9.6.0** wrapper. Use it for builds and tests.

## Setup

### 1. Open the project

Clone the repository and open `APP-MOVIL/android` with **File > Open** in Android Studio. This directory contains `settings.gradle.kts` and the `app` module.

Install the required SDK components in **SDK Manager** and wait for Gradle sync to finish. Android Studio generates `local.properties` with your machine's SDK path.

### 2. Configure Firebase (optional)

Weather queries work without Firebase. To enable the **ESP32** source, register an Android app in your own Firebase project with this application ID:

```text
com.ic3101.consultaambiental
```

Create a Realtime Database, then download the Android app's `google-services.json` and place it in `APP-MOVIL/android/app/`. Download it after creating the database so that `project_info.firebase_url` is included. This local file is ignored by Git; no shared project configuration is bundled.

The app reads the database URL from that file, so no Kotlin files need editing. The expected data path is `lecturas/equipo01`; the [Firebase setup guide](../firebase/README.md) covers the supplied rules template.

Sync Gradle and rebuild after adding or changing the file. Without it, the Google Services plugin stays disabled and the app opens **Clima de Costa Rica** by default. Selecting **ESP32** shows a setup notice rather than starting a database connection.

### 3. Run the app

Select the **app** module, choose an emulator or device and press **Run**. With Firebase configured, the **ESP32** source shows the latest available record and listens for new readings.

### 4. Choose a weather location

Select **Clima de Costa Rica** under **Fuente de datos**, then press **Cambiar** to search for a place or province. Locations are defined in `ZonasClimaticas.kt`; the app does not request location permission. Santa Clara uses its original approximate coordinates, **10.36, −84.51**.

Home requests current conditions, the next 24 hours and ten days including today. Data refreshes every **15 minutes** while Home is visible. Switching screens, opening Information or putting the app in the background pauses scheduled refreshes. **Actualizar clima** starts a manual request. Forecasts are cached per location during the session. If a request fails, the last result for that location remains visible with a notice.

The `https://api.open-meteo.com/v1/forecast` endpoint receives coordinates, `timeformat=unixtime`, `timezone=America/Costa_Rica` and `forecast_days=10`. Requests include temperature, humidity, dew point, WMO codes, day/night status and precipitation probability. Daily minimums and maximums belong to the forecast, rather than the extremes of Firebase records.

For historical analysis, select **Cargar historial**:

- The period covers the previous ten complete days, excluding today.
- A complete response contains **240 hourly records** for temperature, relative humidity and dew point.
- Each location's copy is saved atomically as `clima_<id_zona>.json` in private app storage. The earlier Santa Clara file keeps its existing name.
- Saved history can be analyzed offline. **Actualizar historial** replaces only that location's copy; a failed download preserves the previous one.
- Changing location resets the visible query and uses only that location's history. The selection is saved between sessions.

Forecasts stay in memory and are fetched again in a later session. They are kept separate from the ten historical days and are never written to Firebase. Weather values are model estimates for approximate coordinates; the model's grid cell may be near the requested location. The public API does not require a key for noncommercial academic use.

Data: [Open-Meteo](https://open-meteo.com/) · [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Weather CSV files identify the location, coordinates, provider and license.

## Information and help

The **ⓘ** button in the header opens **Información** from any screen. It collects data sources, location, refresh intervals, download details, calculation rules, filters, exports and Open-Meteo attribution with a link to CC BY 4.0.

The panel opens over the current query. **Volver** or Android's Back button closes it while preserving dates, variables, filters and results. Main screens focus on controls, results and notices about errors or stale data. The history download/update panel appears on Home and also in queries when no history has been downloaded.

## Data model

Each record has a Firebase-generated ID under `lecturas/equipo01`:

```text
lecturas/
└── equipo01/
    └── <id_lectura>/
        ├── temperatura
        ├── humedad
        ├── puntoRocio
        └── ts
```

| Field | Type | Description |
| --- | --- | --- |
| `temperatura` | Number | Temperature in °C |
| `humedad` | Number | Relative humidity in % |
| `puntoRocio` | Number | Calculated dew point in °C |
| `ts` | Number | Server timestamp in Unix milliseconds |

Dates use `America/Costa_Rica`. Daily averages are calculated directly from that day's readings. Missing or non-finite values are omitted independently for each variable; empty periods display **Sin datos** (no data).

Filters use editable inclusive limits. Initial limits are demonstration values that users can adapt to their dataset. Enabling the filter changes calculated results and exports while preserving the original Firebase records.

## Export

**Exportar CSV** opens Android's file picker to choose a filename and location. No broad storage permission is required.

Files use UTF-8 with a BOM, semicolons as delimiters and decimal commas with three decimal places. They include dates, units, time zone, reading counts and empty-period status. Active filter limits are included. Weather CSV files also add the source, location, requested coordinates, provider, license and data processing details. If a spreadsheet does not detect the columns, import the CSV with `;` as the delimiter.

## Build and test

From `APP-MOVIL/android` on Windows:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

On Linux or macOS:

```sh
bash gradlew :app:assembleDebug :app:testDebugUnitTest
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Unit tests cover averages, extremes, independent variable filters, shared scales by unit, CSV files with multiple series, and forecast date/unit/array validation. For interface checks, navigate through the four screens, change the theme and compare exported results against the Firebase readings.

See [CONSULTAS.md (Spanish)](android/CONSULTAS.md) for calculation rules and verification scenarios.

## ESP32 integration

The Wokwi firmware and circuit are maintained outside this repository. The device simulates a DHT22 on GPIO 15, calculates dew point with the Magnus approximation and sends one reading per minute to `lecturas/equipo01`.

The app does not need write credentials. Configure a dedicated Firebase Authentication account for the device, authorize its UID in the rules and provide a temporary ID token when Wokwi starts. Keep the password out of the app and any Wokwi project accessible through a shared link.

The procedure, PowerShell script and rule tests are described in [firebase/README.md](../firebase/README.md). Publishing code to GitHub does not deploy rules or enable Authentication. Public reads are limited to the team's path; the device account can only create new readings.

## Code layout

Application source is under `android/app/src/main/java/com/ic3101/consultaambiental/`:

| Files | Responsibility |
| --- | --- |
| `MainActivity.kt`, `InterfazAmbiental.kt` | Home, navigation and reading display |
| `ConsultaPorHora.kt`, `ConsultaPorDia.kt`, `ConsultaHistorica.kt` | Firebase queries and results screens |
| `PromediosDiarios.kt`, `ExtremosHistoricos.kt` | Daily averages and extreme-value calculations |
| `GraficosAmbientales.kt`, `ComparacionVariables.kt` | Multiple chart series, scales by unit, independent selection and filtering |
| `FiltroAtipicos.kt` | Limit validation and filtering per variable |
| `ExportacionCsv.kt`, `BotonExportarCsv.kt` | CSV formatting and saving through Android's file picker |
| `ClimaSantaClara.kt` | Downloading and privately storing weather history per location |
| `ClimaActual.kt`, `PronosticoClima.kt` | Current-condition validation, forecast requests and parsing |
| `EstadoClima.kt` | Request state and results per location |
| `ZonasClimaticas.kt` | Location catalog, coordinates and provenance |
| `VistaPronostico.kt`, `SelectorFuenteDatos.kt` | Location selection, weather cards, icons and data source |
| `InformacionApp.kt` | Help, sources, refresh details and attribution |
| `SelectorTema.kt`, `ui/theme/` | Theme selection and colors |

### Common changes

- **Add a location:** add a `zonasClimaticas` entry with a unique ID and approximate coordinates. Preserve existing IDs because preferences and saved files depend on them.
- **Change the weather display:** edit `VistaPronostico.kt`. `CabeceraPronostico` presents current conditions; `PronosticoPorDia` draws the ten daily rows. Backgrounds and icons are local vectors.
- **Change scales or colors:** review `escalasComparacion` in `GraficosAmbientales.kt` and `colorVariable` in `ComparacionVariables.kt`. Keep units explicit when comparing °C and %.
- **Request another Open-Meteo field:** update the URL and parser in `PronosticoClima.kt`, including unit and array-length validation. Add a missing-data test case.

Local SDK configuration, build directories and signing keys are excluded from version control.

## Current scope

The app has been checked with simulated readings and hourly, daily and extreme-value exports. The ten-day history comes from Open-Meteo; current and historical API responses have been checked live. Forecast presentation and series comparison also require an interface walkthrough on an Android device. The extremes query downloads the full history and targets the academic project's data volume; larger datasets would need aggregation or pagination.
