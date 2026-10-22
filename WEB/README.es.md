# Consulta Ambiental · Web

[English](README.md) | **Español**

Cliente web para consultar temperatura, humedad y punto de rocío. Comparte el esquema
de datos y los criterios de cálculo de la aplicación Android. Está desarrollado con
HTML, CSS y módulos de JavaScript, sin framework ni proceso de compilación.

## Ejecutar en desarrollo

Requiere Python 3. Desde la raíz del repositorio:

```sh
python WEB/servidor-dev.py
```

Abre `http://localhost:8000`. Puedes indicar otro puerto, por ejemplo
`python WEB/servidor-dev.py 8080`. El servidor evita la caché durante el desarrollo.
No abras `index.html` directamente desde el disco: los módulos necesitan HTTP.

La aplicación carga Chart.js, jsPDF y su complemento de tablas desde CDN. Las consultas
a Firebase y Open-Meteo necesitan conexión. El historial meteorológico ya descargado se
conserva en el navegador; esa copia no garantiza que la página completa abra sin red.

## Fuentes y consultas

- **ESP32:** lecturas del DHT22 simulado en Wokwi, usando la configuración de tu
  propia Firebase Realtime Database.
- **Open-Meteo:** condiciones actuales, pronóstico de diez días y consultas del historial
  por zona de Costa Rica. Son estimaciones del modelo. El pronóstico se mantiene separado
  del historial utilizado en los análisis.
- **Por hora y por día:** comparación de una o varias variables, estadísticas del periodo,
  filtros independientes por variable y exportación a CSV o PDF.
- **Extremos:** mínimo, máximo y distribución de las lecturas del historial disponible.
  La interfaz indica qué periodo se analiza.

Las fechas se interpretan en `America/Costa_Rica`. Un periodo sin lecturas conserva su
hueco: no se rellena con cero ni se interpola. Los filtros solo afectan a los cálculos,
nunca modifican los datos almacenados. Un filtro activo inválido bloquea los resultados
y las exportaciones hasta corregir sus límites.

## Organización del código

| Archivo o carpeta | Responsabilidad |
| --- | --- |
| `js/app.js` | Estado general, navegación, fuentes y actualización del pronóstico |
| `js/firebase.js`, `js/openmeteo.js` | Acceso a datos y normalización de respuestas |
| `js/modelo.js`, `js/consultas.js`, `js/filtro.js` | Fechas, cálculos y filtros |
| `js/graficos.js`, `js/exportar.js` | Gráficas y archivos CSV/PDF |
| `js/ui/` | Pantallas, controles y eventos de la interfaz |
| `css/` | Tema claro/oscuro y distribución adaptable |

## Pruebas

Con el servidor abierto, `http://localhost:8000/pruebas.html` ejecuta las 58 pruebas
del núcleo. Para automatizar esas comprobaciones y las 20 regresiones de interfaz:

```sh
python -m pip install -r WEB/tests/requirements.txt
python -m playwright install chromium
python -m unittest discover -s WEB/tests -v
```

El runner inicia su propio servidor, usa respuestas controladas y no accede a la base de
producción. Los detalles están en [tests/README.md](tests/README.es.md).

El seguimiento de las correcciones de la revisión está en la
[issue #4](https://github.com/greivinsmv/Consulta-de-variables-ambientales/issues/4)
y en [REVISION_PR_3.md](REVISION_PR_3.md).

Consulta también la [descripción general](../README.es.md) y la
[documentación de Android](../APP-MOVIL/README.es.md).

## Configuración de Firebase (opcional)

Open-Meteo funciona sin Firebase. Para habilitar la fuente ESP32, copia
`WEB/config.example.json` a `WEB/config.local.json` e indica la URL de tu propia base:

```json
{
  "firebase": {
    "databaseURL": "https://YOUR_PROJECT-default-rtdb.firebaseio.com",
    "readingsPath": "lecturas/equipo01"
  }
}
```

El archivo local está excluido de Git. Contiene configuración del cliente; no añadas
contraseñas ni tokens. La [guía de Firebase](../firebase/README.es.md) explica cómo
preparar tu base. Sin ese archivo, la aplicación abre la vista del clima y muestra un
aviso de configuración al seleccionar ESP32.
