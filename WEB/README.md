# Consulta Ambiental · Web

**English** | [Español](README.es.md)

A web client for temperature, humidity and dew point queries. It shares the Android app's data schema and calculation rules, using HTML, CSS and JavaScript modules without a framework or build step.

## Run locally

Install Python 3, then run from the repository root:

```sh
python WEB/servidor-dev.py
```

Open `http://localhost:8000`. To use another port, run `python WEB/servidor-dev.py 8080`. The development server disables caching. Open the app over HTTP rather than opening `index.html` directly from disk, since JavaScript modules require it.

Chart.js, jsPDF and its table plugin load from a CDN. Firebase and Open-Meteo requests need an internet connection. Downloaded weather history is cached in the browser; this cache alone does not make the entire site available offline.

## Data sources and queries

- **ESP32:** readings from a DHT22 simulated in Wokwi, stored under `lecturas/equipo01` in Firebase Realtime Database. The web client only reads data and does not need the device account or its write token.
- **Open-Meteo:** current conditions, a ten-day forecast and historical weather queries for locations in Costa Rica. Values are model estimates. Forecasts remain separate from the history used for analysis.
- **Por hora / Por día:** hourly and daily comparisons of one or more variables, period statistics, independent filters and CSV/PDF exports.
- **Extremos:** minimum, maximum and distribution of the available readings. The interface identifies the period being analyzed.

Dates use `America/Costa_Rica`. Missing periods remain gaps: values are neither interpolated nor replaced with zero. Filters affect calculations only and never modify stored data. An invalid active filter blocks results and exports until its limits are corrected.

## Code layout

| File or directory | Responsibility |
| --- | --- |
| `js/app.js` | Application state, navigation, data sources and forecast refresh |
| `js/firebase.js`, `js/openmeteo.js` | Data access and response normalization |
| `js/modelo.js`, `js/consultas.js`, `js/filtro.js` | Dates, calculations and filters |
| `js/graficos.js`, `js/exportar.js` | Charts and CSV/PDF files |
| `js/ui/` | Screens, controls and interface events |
| `css/` | Light/dark theme and responsive layout |

## Tests

With the server running, open `http://localhost:8000/pruebas.html` for the 58 core checks. To automate them and the 17 interface regressions:

```sh
python -m pip install -r WEB/tests/requirements.txt
python -m playwright install chromium
python -m unittest discover -s WEB/tests -v
```

The runner starts its own server, uses mocked responses and does not access the production database. See [tests/README.md](tests/README.md).

The earlier web fixes are tracked in [issue #4](https://github.com/greivinsmv/Consulta-de-variables-ambientales/issues/4) and [REVISION_PR_3.md (Spanish)](REVISION_PR_3.md).

See also the [project overview](../README.md) and [Android guide](../APP-MOVIL/README.md).

## Database access

The web client queries only `lecturas/equipo01`, without reading the database root. The rules allow public reads of that path and restrict record creation to the ESP32 account's UID. Write and administrative credentials must stay out of the frontend. Follow the [access guide](../firebase/README.md) to prepare the project's rules.

The Firebase URL is public configuration, not a credential. The review's scope and limitations are documented in [SEGURIDAD.md (Spanish)](../SEGURIDAD.md).
