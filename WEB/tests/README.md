# Interface tests

**English** | [Español](README.es.md)

From the repository root, with Python 3.10 or later:

```sh
python -m pip install -r WEB/tests/requirements.txt
python -m playwright install chromium
python -m unittest discover -s WEB/tests -v
```

The runner starts and stops its HTTP server on an available port. Each test uses a fresh browser context and mocked Firebase and Open-Meteo responses, without credentials or production access. It also runs the 58 checks in `WEB/pruebas.html`.

Interface tests replace Chart.js with a stub that does not draw. They check controls, data and exports independently of CDN access and animations. Charts are checked visually in the application with the real libraries.

If Chromium is already installed, set `CHROMIUM_PATH` to its executable. The runner also detects a system `chromium` command.
