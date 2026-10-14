# Aplicación Android · Consulta Ambiental

Cliente de consulta ambiental desarrollado en Kotlin y Jetpack Compose. Recibe lecturas de Firebase Realtime Database y descarga datos meteorológicos de Open-Meteo para varias zonas de Costa Rica. Ofrece análisis por hora, por día y de extremos históricos, junto con filtros configurables y exportación CSV.

## Pantallas

| Pantalla | Función |
| --- | --- |
| Inicio | Lectura del ESP32 o clima actual, pronóstico horario y de diez días de la zona elegida |
| Por hora | Promedios de un día, comparación de variables y selector de hora |
| Por día | Comparación de promedios diarios y detalle de una fecha seleccionada |
| Extremos | Mínimos, máximos, fechas y repeticiones de todo el historial |

Por hora y Por día permiten seleccionar una o varias variables en la misma gráfica. Extremos mantiene su selector individual. Las tres consultas incluyen filtro por límites y exportación CSV. El tema claro u oscuro se conserva entre sesiones.

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
5. Sigue la [guía de acceso](../firebase/README.md) para permitir lectura de esa ruta y reservar la escritura al dispositivo. Las reglas incluyen el índice `.indexOn: ["ts"]` necesario para las consultas por fecha.

El proyecto de demostración utiliza `proyecto-iot-fa353`. La aplicación solo consulta datos; el ESP32 es el componente que envía las lecturas. Las reglas de Firebase determinan quién puede leer y escribir.

### 3. Ejecutar

Selecciona el módulo **app**, elige el emulador o dispositivo y pulsa **Run**. Con Firebase configurado, la fuente **ESP32** muestra el último registro disponible y escucha las nuevas lecturas.

### 4. Consultar el clima por zona

Selecciona **Clima de Costa Rica** en **Fuente de datos** y pulsa **Cambiar** para buscar un lugar o provincia. Las ubicaciones están definidas en `ZonasClimaticas.kt`; no se solicita permiso de ubicación. Santa Clara sigue disponible con sus coordenadas originales (**10.36, −84.51**).

Inicio solicita condiciones actuales, las próximas 24 horas y diez días, incluido hoy. Los datos se actualizan cada **15 minutos** mientras Inicio está visible; cambiar de pantalla, abrir Información o enviar la app al fondo pausa la programación. **Actualizar clima** permite una consulta manual. Se reutiliza el pronóstico recibido para cada zona durante la sesión. Si falla una petición, se conserva el último resultado de esa zona y se muestra un aviso.

El endpoint `https://api.open-meteo.com/v1/forecast` recibe las coordenadas, `timeformat=unixtime`, `timezone=America/Costa_Rica` y `forecast_days=10`. Se solicitan temperatura, humedad, rocío, códigos WMO, indicador de día/noche y probabilidad de precipitación. Los mínimos y máximos diarios pertenecen al pronóstico; no son los extremos de los registros de Firebase.

Para las consultas académicas, pulsa **Cargar historial**:

- Periodo: diez días completos anteriores; hoy queda excluido.
- Variables: temperatura, humedad relativa y punto de rocío, con **240 registros horarios** por respuesta completa.
- La copia de cada zona se guarda atómicamente en `clima_<id_zona>.json`, dentro del almacenamiento privado de la app. El archivo anterior de Santa Clara conserva el mismo nombre.
- El historial guardado funciona sin conexión. **Actualizar historial** reemplaza solo la copia de esa zona; un fallo conserva la descarga anterior.
- Cambiar de zona reinicia la consulta visible y utiliza únicamente su historial. La elección de zona se conserva entre sesiones.

El pronóstico se conserva en memoria y se consulta de nuevo en una sesión posterior. No se mezcla con los diez días históricos ni se escribe en Firebase. Los valores son datos modelados para coordenadas aproximadas; la celda del modelo puede estar próxima al lugar solicitado. La API pública no requiere una clave para el uso académico no comercial.

Datos: [Open-Meteo](https://open-meteo.com/) · [Licencia CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Los CSV identifican la zona, coordenadas, proveedor y licencia.

## Información y ayuda

El botón **ⓘ** de la cabecera abre el apartado **Información** desde cualquier pantalla. Reúne las fuentes, la ubicación, la frecuencia de actualización, los detalles de la descarga, los criterios de cálculo, el filtro, la exportación y los créditos de Open-Meteo con enlace a la licencia CC BY 4.0.

La vista se abre sobre la consulta actual: **Volver** o el botón Atrás de Android permiten cerrar Información conservando las fechas, las variables, el filtro y los resultados. Las explicaciones generales se concentran allí; las pantallas principales presentan controles, resultados y avisos de error o datos antiguos. El panel para cargar o actualizar el historial está en Inicio y aparece también en las consultas cuando falta una descarga.

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

Las pruebas unitarias verifican promedios, extremos, filtros independientes por variable, escalas compartidas por unidad, CSV con varias series y validación de fechas, unidades y arrays del pronóstico. Para comprobar la interfaz, recorre las cuatro pantallas, cambia de tema y contrasta los resultados exportados con las lecturas de Firebase.

Consulta [CONSULTAS.md](android/CONSULTAS.md) para los criterios de cálculo y escenarios de comprobación.

## Integración con el ESP32

El firmware y el circuito de Wokwi se mantienen fuera del repositorio. El dispositivo simula un DHT22 en GPIO 15, calcula el punto de rocío mediante Magnus y envía una lectura por minuto a `lecturas/equipo01`.

La app no necesita credenciales de escritura. Para proteger la base, configura una cuenta de Firebase Authentication exclusiva del dispositivo, autoriza su UID en las reglas y proporciona un ID token temporal al iniciar Wokwi. La contraseña no se incorpora a la app ni a un proyecto Wokwi accesible mediante un enlace.

El procedimiento, el script de PowerShell y las pruebas de reglas están en [firebase/README.md](../firebase/README.md). Publicar el código en GitHub no despliega las reglas ni habilita Authentication. La lectura pública queda limitada a la ruta del equipo; la cuenta del dispositivo solo crea lecturas nuevas.

## Organización del código

El código de la aplicación está en `android/app/src/main/java/com/ic3101/consultaambiental/`:

| Archivos | Responsabilidad |
| --- | --- |
| `MainActivity.kt`, `InterfazAmbiental.kt` | Inicio, navegación y presentación de las lecturas |
| `ConsultaPorHora.kt`, `ConsultaPorDia.kt`, `ConsultaHistorica.kt` | Consultas a Firebase y pantallas de resultados |
| `PromediosDiarios.kt`, `ExtremosHistoricos.kt` | Cálculos de promedios diarios y extremos |
| `GraficosAmbientales.kt`, `ComparacionVariables.kt` | Gráfica de varias series, escalas por unidad, selección y filtrado independiente |
| `FiltroAtipicos.kt` | Validación de límites y filtrado por variable |
| `ExportacionCsv.kt`, `BotonExportarCsv.kt` | Formato CSV y guardado mediante el selector de Android |
| `ClimaSantaClara.kt` | Descarga y archivo privado del historial por zona |
| `ClimaActual.kt`, `PronosticoClima.kt` | Validación del dato actual, petición y lectura del pronóstico |
| `EstadoClima.kt` | Estado de peticiones y resultados por zona |
| `ZonasClimaticas.kt` | Catálogo de lugares, coordenadas y procedencia |
| `VistaPronostico.kt`, `SelectorFuenteDatos.kt` | Selector de zona, tarjetas, símbolos del clima y fuente |
| `InformacionApp.kt` | Ayuda, fuentes, detalles de actualización y créditos |
| `SelectorTema.kt`, `ui/theme/` | Selector de apariencia y paleta visual |

### Cambios habituales

- **Añadir una zona:** agrega una entrada a `zonasClimaticas` con un identificador único y coordenadas aproximadas. Mantén los identificadores existentes: forman parte de las preferencias y los archivos guardados.
- **Cambiar la presentación del clima:** edita `VistaPronostico.kt`. `CabeceraPronostico` presenta el estado actual y `PronosticoPorDia` dibuja las filas de diez días. Los fondos e iconos son vectores locales.
- **Cambiar las escalas o colores:** revisa `escalasComparacion` en `GraficosAmbientales.kt` y `colorVariable` en `ComparacionVariables.kt`. Las unidades deben permanecer explícitas al comparar °C y %.
- **Solicitar otro campo a Open-Meteo:** actualiza la URL y el parser de `PronosticoClima.kt`, incluyendo la validación de su unidad y longitud. Añade un caso de prueba para datos ausentes.

Los archivos locales del SDK, las carpetas de compilación y las claves de firma se excluyen del control de versiones.

## Alcance actual

La app se ha probado con lecturas simuladas y con exportaciones por hora, por día y de extremos. El historial de diez días se obtiene de Open-Meteo. Las respuestas actuales e históricas de la API se han comprobado en vivo. Los nuevos pronósticos y la comparación de series requieren además recorrer la interfaz en un dispositivo. La comprobación de la interfaz meteorológica se realiza en el dispositivo Android. La consulta de extremos descarga todo el historial y está pensada para el volumen de datos del proyecto académico; conjuntos mayores requerirían una estrategia de agregación o paginación.
