# Pruebas de la interfaz

Desde la raíz del repositorio, con Python 3.10 o posterior:

```sh
python -m pip install -r WEB/tests/requirements.txt
python -m playwright install chromium
python -m unittest discover -s WEB/tests -v
```

El runner arranca y detiene su servidor HTTP en un puerto libre. Cada caso usa un
navegador limpio y respuestas controladas de Firebase y Open-Meteo, sin credenciales
ni acceso a producción. También ejecuta las 58 pruebas de `WEB/pruebas.html`.

Los casos de interfaz sustituyen Chart.js por un objeto que no dibuja: comprueban los
controles, los datos y las exportaciones sin depender del CDN ni de animaciones. La
apariencia de las gráficas se revisa aparte en la aplicación con las bibliotecas reales.

Si ya tienes Chromium instalado, puedes indicar su ejecutable mediante `CHROMIUM_PATH`;
el runner también detecta el comando `chromium` del sistema.
