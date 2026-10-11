# Consulta Ambiental

Aplicación Android para consultar y analizar datos ambientales procedentes de Firebase Realtime Database y de Open-Meteo. Integra lecturas de un ESP32 con sensor DHT22 simulado en Wokwi y permite explorar temperatura, humedad relativa y punto de rocío mediante consultas temporales, gráficos y exportación de resultados.

Proyecto académico del curso IC-3101. La aplicación móvil constituye el componente de este repositorio desarrollado para el portafolio.

## Funcionalidades

- **Clima actual de Santa Clara:** condiciones meteorológicas con consulta automática cada 15 minutos mientras Inicio está visible y actualización manual.
- **Historial meteorológico:** datos horarios de los diez días completos anteriores para Santa Clara, San Carlos, con una copia local para consultar sin conexión.
- **Lectura actual:** temperatura, humedad, punto de rocío y fecha del último registro, con actualización en tiempo real.
- **Promedios por hora:** consulta de un día y selección de cualquiera de sus 24 franjas horarias.
- **Promedios por día:** análisis de un rango de fechas, con cantidad de lecturas y gráfico de barras.
- **Extremos históricos:** mínimos y máximos por variable, fechas de registro y repeticiones.
- **Filtro de valores atípicos:** límites mínimo y máximo editables por variable, aplicados a los cálculos, gráficos y exportaciones.
- **Exportación CSV:** resultados con unidades, fechas, zona horaria y límites del filtro cuando está activo.
- **Apariencia:** navegación inferior, tarjetas de medición y temas claro y oscuro con preferencia guardada.

## Flujo de datos

```mermaid
flowchart LR
    A[ESP32 + DHT22 en Wokwi] -->|Lecturas por HTTP POST| B[Firebase Realtime Database]
    B -->|Eventos en tiempo real| C[Aplicación Android]
    F[Open-Meteo · Santa Clara] -->|Descarga de diez días| C
    C --> G[Copia local del historial meteorológico]
    C --> D[Consultas y gráficos]
    C --> E[Exportación CSV]
```

El firmware envía una lectura por minuto y solicita a Firebase la fecha del servidor. La app escucha los cambios y realiza los cálculos de consulta a partir de los registros recibidos.

El punto de rocío se calcula en el ESP32 mediante la aproximación de Magnus, usando temperatura y humedad relativa.

## Tecnologías

| Componente | Tecnologías |
| --- | --- |
| Aplicación móvil | Kotlin, Jetpack Compose, Material 3 |
| Datos | Firebase Realtime Database y API REST de Open-Meteo |
| Dispositivo simulado | ESP32, DHT22, C++ y Wokwi |
| Compilación | Gradle con Kotlin DSL y catálogo de versiones |
| Pruebas | JUnit para cálculos, filtros, escalas y formato CSV |

## Estructura

```text
APP-MOVIL/
├── android/       Aplicación Android y pruebas
└── wokwi/         Firmware y circuito simulado
WEB/               Espacio del componente web
```

- [Configuración y ejecución de la app](APP-MOVIL/README.md)
- [Comportamiento de las consultas y comprobaciones](APP-MOVIL/android/CONSULTAS.md)
- [Estado del componente web](WEB/README.md)

## Ejecutar el proyecto

```bash
git clone https://github.com/greivinsmv/Consulta-de-variables-ambientales.git
```

Abre `APP-MOVIL/android` en Android Studio, configura el SDK y Firebase según la [guía de la aplicación móvil](APP-MOVIL/README.md), sincroniza Gradle y ejecuta el módulo `app` en un dispositivo con Android 8.0 o superior.

La simulación está disponible en [Wokwi](https://wokwi.com/projects/477474234161260545). Al modificar los valores del DHT22 y ejecutar la simulación, las nuevas lecturas se reflejan en la app conectada al mismo proyecto Firebase.

## Historial meteorológico de Santa Clara

En la app, selecciona **Santa Clara** como fuente. Inicio consulta automáticamente las condiciones actuales y ofrece **Actualizar ahora**. El clima actual se presenta con la fecha del dato y la hora de consulta; si falla una actualización, se avisa y se conserva el último dato recibido durante la sesión.

Para las consultas históricas, pulsa **Cargar últimos 10 días**. La consulta solicita temperatura, humedad relativa y punto de rocío horarios para las coordenadas aproximadas **10.36, −84.51**. El periodo incluye los diez días completos anteriores al día de descarga, en horario de Costa Rica.

Los valores de Open-Meteo son datos modelados. La app conserva su origen en la interfaz y en los CSV, y guarda la última descarga válida en el teléfono. Las consultas utilizan la fuente seleccionada; la descarga meteorológica se almacena localmente.

Datos proporcionados por [Open-Meteo](https://open-meteo.com/), bajo licencia [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). La aplicación calcula promedios, extremos y filtros sobre esos datos.

## Criterios de análisis

Las fechas de consulta se interpretan en `America/Costa_Rica`. Cada promedio utiliza las lecturas numéricas válidas de la variable seleccionada; los periodos vacíos se muestran como **Sin datos**. El filtro excluye valores de los resultados sin modificar el historial de Firebase.

Los gráficos y CSV comparten los mismos cálculos que los resultados en pantalla. Las pruebas unitarias cubren promedios, extremos, límites del filtro, datos ausentes y exportación.

## Estado y alcance

La aplicación Android, la integración con Firebase y el envío desde Wokwi están implementados y se han probado con lecturas simuladas. El componente web está pendiente de implementación y corresponde a otro integrante del equipo. La app incorpora la descarga de un historial meteorológico de diez días desde Open-Meteo; las respuestas actuales e históricas de la API se han comprobado. La verificación visual se realiza en el dispositivo Android.

La configuración de conexión pertenece a un entorno de demostración. Para una instalación propia, adapta el proyecto Firebase, sus permisos de acceso y la validación TLS del firmware; los detalles están en la guía móvil.
