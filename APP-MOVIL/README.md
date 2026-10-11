# Aplicación Android · Consulta Ambiental

Cliente de consulta ambiental desarrollado en Kotlin y Jetpack Compose. Recibe lecturas de Firebase Realtime Database y descarga datos meteorológicos de Open-Meteo para Santa Clara, San Carlos. Ofrece análisis por hora, por día y de extremos históricos, junto con filtros configurables y exportación CSV.

## Pantallas

| Pantalla | Función |
| --- | --- |
| Inicio | Última lectura, temperatura, humedad, punto de rocío y fecha |
| Por hora | Promedios de un día, selector de hora y gráfico de evolución |
| Por día | Promedios de un rango de fechas y gráfico de barras |
| Extremos | Mínimos, máximos, fechas y repeticiones de todo el historial |

Las tres consultas permiten elegir la variable, activar el filtro por límites y exportar sus resultados. Los gráficos horarios y diarios se pueden desplegar. El tema claro u oscuro se conserva entre sesiones.

## Requisitos

- Android Studio compatible con Android Gradle Plugin **9.4.1**.
- Android SDK Platform **37** y SDK Build Tools **36.0.0**.
- JDK **25** para el daemon de Gradle, según `gradle/gradle-daemon-jvm.properties`.
- Emulador o dispositivo con Android **8.0 (API 26)** o superior.
- Acceso a internet y un proyecto Firebase Realtime Database con permisos de lectura.

El repositorio incluye el wrapper de Gradle **9.6.0**; utiliza el wrapper para compilar y ejecutar pruebas.

## Configuración

### 1. Abrir el proyecto

Clona el repositorio y abre la carpeta `APP-MOVIL/android` desde **File > Open** en Android Studio. Esta carpeta contiene `settings.gradle.kts` y el módulo `app`.

Instala los componentes del SDK desde **SDK Manager** y espera a que termine la sincronización de Gradle. Android Studio genera `local.properties` con la ruta del SDK de la máquina.

### 2. Configurar Firebase

El identificador de la aplicación es:

```text
com.ic3101.consultaambiental
```

El repositorio incluye `app/google-services.json` para el proyecto de demostración. Si utilizas un proyecto propio:

1. Registra una aplicación Android con el identificador anterior en Firebase.
2. Descarga su `google-services.json` y colócalo en `APP-MOVIL/android/app/`.
3. Configura la URL de Realtime Database en `MainActivity.kt`, `ConsultaPorHora.kt`, `ConsultaPorDia.kt` y `ConsultaHistorica.kt`.
4. Conserva la ruta `lecturas/equipo01` o adapta las referencias de lectura al esquema elegido.
5. Configura los permisos de lectura y un índice `.indexOn: ["ts"]` en esa ruta para las consultas por fecha.

El proyecto de demostración utiliza `proyecto-iot-fa353`. La aplicación solo consulta datos; el ESP32 es el componente que envía las lecturas. Las reglas de Firebase determinan quién puede leer y escribir.

### 3. Ejecutar

Selecciona el módulo **app**, elige el emulador o dispositivo y pulsa **Run**. Con Firebase configurado, la fuente **ESP32** muestra el último registro disponible y escucha las nuevas lecturas.

### 4. Consultar el clima de Santa Clara

Selecciona **Santa Clara** en **Fuente de datos**. Inicio consulta las condiciones actuales de temperatura, humedad y punto de rocío al acceder a esta fuente. Las consultas automáticas se repiten cada **15 minutos** mientras Inicio está visible; se detienen al cambiar de pantalla, fuente o enviar la app al fondo. **Actualizar ahora** permite consultar manualmente.

La fecha del dato meteorológico aparece en Inicio; la hora de la última consulta está en **Información (ⓘ)**. Un fallo conserva el último dato recibido durante la sesión con un aviso; si tiene más de 30 minutos de antigüedad, se indica que puede no representar las condiciones actuales. Esta vista funciona sin descargar el historial.

Para las consultas históricas, pulsa **Cargar historial**. Esta fuente utiliza una consulta HTTPS a la API pública de Open-Meteo y no requiere una clave para el uso académico no comercial.

- Ubicación aproximada: Santa Clara, San Carlos, Costa Rica (**10.36, −84.51**).
- Variables: `temperature_2m`, `relative_humidity_2m` y `dew_point_2m`.
- Periodo: diez días completos anteriores al día actual, calculados en `America/Costa_Rica`.
- Resolución: un dato por hora, hasta **240 registros** por descarga.
- Endpoint: `https://api.open-meteo.com/v1/forecast`, con `start_date`, `end_date` y `timeformat=unixtime`.

Son datos meteorológicos modelados para la zona. Por día propone inicialmente el rango completo, Por hora el último día descargado y Extremos analiza ese historial. Las consultas, filtros y CSV utilizan la fuente seleccionada. Esta fuente aporta un dato por hora: cada promedio horario coincide con ese dato y los diarios usan hasta 24 datos.

La última respuesta válida se guarda de forma atómica en `clima_santa_clara.json`, dentro del almacenamiento privado de la app. Se recupera al abrirla y permite consultar el periodo guardado sin conexión. **Actualizar historial** reemplaza esa copia por el nuevo periodo. Si falla la descarga, se conserva la copia previa y se muestra un aviso. Los datos meteorológicos se almacenan localmente.

Datos: [Open-Meteo](https://open-meteo.com/) · [Licencia CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Los promedios, extremos y filtros son transformaciones realizadas por la aplicación.

## Información y ayuda

El botón **ⓘ** de la cabecera abre el apartado **Información** desde cualquier pantalla. Reúne las fuentes, la ubicación, la frecuencia de actualización, los detalles de la descarga, los criterios de cálculo, el filtro, la exportación y los créditos de Open-Meteo con enlace a la licencia CC BY 4.0.

La vista se abre sobre la consulta actual: **Volver** o el botón Atrás de Android permiten cerrar Información conservando las fechas, la variable, el filtro y los resultados. Las explicaciones generales se concentran allí; las pantallas principales presentan controles, resultados y avisos de error o datos antiguos. El panel para cargar o actualizar el historial está en Inicio y aparece también en las consultas cuando falta una descarga.

## Modelo de datos

Cada registro se almacena con un identificador generado por Firebase en `lecturas/equipo01`:

```text
lecturas/
└── equipo01/
    └── <id_lectura>/
        ├── temperatura
        ├── humedad
        ├── puntoRocio
        └── ts
```

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `temperatura` | Número | Temperatura en °C |
| `humedad` | Número | Humedad relativa en % |
| `puntoRocio` | Número | Punto de rocío calculado en °C |
| `ts` | Número | Fecha del servidor en milisegundos Unix |

Las fechas se muestran en `America/Costa_Rica`. Los promedios diarios se calculan directamente sobre las lecturas del día. Los valores ausentes o no finitos se omiten por variable y los periodos vacíos conservan el estado **Sin datos**.

El filtro usa límites inclusivos y editables. Los límites iniciales son valores de demostración; el usuario puede ajustarlos para su conjunto de datos. Activarlo cambia los resultados calculados y sus exportaciones, conservando los registros originales en Firebase.

## Exportación

**Exportar CSV** abre el selector de archivos de Android para elegir nombre y ubicación. No requiere permisos generales de almacenamiento.

Los archivos usan UTF-8 con BOM, separador punto y coma y coma decimal con tres decimales. Incluyen fechas, unidades, zona horaria, cantidades de lecturas y estado de los periodos vacíos. Con el filtro activo se añaden sus límites. Los CSV meteorológicos agregan fuente, ubicación, coordenadas solicitadas, proveedor, licencia y tratamiento de los datos. Si una hoja de cálculo no reconoce las columnas, importa el archivo como CSV con separador `;`.

## Compilación y pruebas

Desde `APP-MOVIL/android`, en Windows:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

En Linux o macOS:

```bash
bash gradlew :app:assembleDebug :app:testDebugUnitTest
```

El APK de desarrollo se genera en:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Las pruebas unitarias verifican los promedios por hora y por día, los extremos históricos, las escalas de los gráficos, el filtro y la generación de CSV. Para comprobar la interfaz, recorre las cuatro pantallas, cambia de tema y contrasta los resultados exportados con las lecturas de Firebase.

Consulta [CONSULTAS.md](android/CONSULTAS.md) para los criterios de cálculo y escenarios de comprobación.

## Simulación ESP32

La carpeta `wokwi/` contiene el firmware (`main.cpp`), el circuito (`diagram.json`) y las bibliotecas (`libraries.txt`). El circuito utiliza un ESP32 y un DHT22 conectado al GPIO 15.

Abre la [simulación en Wokwi](https://wokwi.com/projects/477474234161260545), ejecuta el circuito y modifica la temperatura o humedad del sensor. El firmware calcula el punto de rocío con la aproximación de Magnus y envía una lectura por minuto. Un mensaje **HTTP 200** indica que Firebase aceptó el envío.

Para usar otra base de datos, modifica `FIREBASE_URL` y, si corresponde, `EQUIPO` en el firmware. La versión de laboratorio utiliza `setInsecure()` y no valida el certificado TLS del servidor; una instalación fuera de ese entorno requiere configurar esa validación.

## Organización del código

El código de la aplicación está en `android/app/src/main/java/com/ic3101/consultaambiental/`:

| Archivos | Responsabilidad |
| --- | --- |
| `MainActivity.kt`, `InterfazAmbiental.kt` | Inicio, navegación y presentación de las lecturas |
| `ConsultaPorHora.kt`, `ConsultaPorDia.kt`, `ConsultaHistorica.kt` | Consultas a Firebase y pantallas de resultados |
| `PromediosDiarios.kt`, `ExtremosHistoricos.kt` | Cálculos de promedios diarios y extremos |
| `GraficosAmbientales.kt` | Escalas y representación de los gráficos |
| `FiltroAtipicos.kt` | Validación de límites y filtrado por variable |
| `ExportacionCsv.kt`, `BotonExportarCsv.kt` | Formato CSV y guardado mediante el selector de Android |
| `ClimaSantaClara.kt`, `ClimaActual.kt`, `SelectorFuenteDatos.kt` | Consultas REST actuales e históricas, validación, copia local y selección de fuente |
| `InformacionApp.kt` | Ayuda, fuentes, detalles de actualización y créditos |
| `SelectorTema.kt`, `ui/theme/` | Selector de apariencia y paleta visual |

Los archivos locales del SDK, las carpetas de compilación y las claves de firma se excluyen del control de versiones.

## Alcance actual

La app se ha probado con lecturas simuladas y con exportaciones por hora, por día y de extremos. El historial de diez días se obtiene de Open-Meteo. Las respuestas actuales e históricas de la API se han comprobado en vivo. La comprobación de la interfaz meteorológica se realiza en el dispositivo Android. La consulta de extremos descarga todo el historial y está pensada para el volumen de datos del proyecto académico; conjuntos mayores requerirían una estrategia de agregación o paginación.
