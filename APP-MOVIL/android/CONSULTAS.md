# Consultas y clima

La app tiene dos fuentes: ESP32 (lecturas simuladas en Firebase) y Clima de Costa Rica (Open-Meteo). Las fechas se interpretan en `America/Costa_Rica`, independientemente de la zona horaria del teléfono.

## Por hora y por día

Elige fecha o periodo, marca una o varias variables y pulsa **Consultar**. Por día incluye ambas fechas y rechaza rangos invertidos o futuros. Cambiar las fechas limpia la consulta anterior.

Cada variable se calcula sobre sus propias lecturas numéricas finitas. El promedio diario usa las lecturas originales, no los promedios horarios. Un registro incompleto puede contar para temperatura y no para humedad. Los periodos vacíos conservan **Sin datos** y no se sustituyen por cero.

La gráfica aparece con los resultados y permite comparar hasta tres series:

- Temperatura: violeta, línea continua.
- Humedad: verde azulado, línea continua.
- Punto de rocío: rosa, línea discontinua.

Temperatura y rocío comparten el eje izquierdo en °C. Si se incluye humedad, utiliza el eje derecho en %. Con humedad sola, el eje izquierdo muestra %. Dos curvas que se cruzan con escalas diferentes no representan valores iguales. La leyenda, los ejes y el resumen conservan sus unidades.

La gráfica solo une periodos consecutivos de cada serie. Una sola hora o día disponible aparece como punto, con margen en la escala. Tocar la gráfica selecciona el periodo; los menús permiten elegir cualquier hora o fecha, incluidas las que no tienen datos.

Por día muestra un resumen de la fecha elegida en lugar de una tarjeta por cada fecha. Cada fila indica variable, promedio y cantidad de lecturas. El CSV conserva el rango completo.

## Filtros

**Filtrar valores atípicos** usa límites inclusivos y editables, sin modificar los registros. No determina si una lectura es real ni detecta anomalías estadísticas. Los límites iniciales son orientativos: temperatura −10 a 50 °C, humedad 0 a 100 % y rocío −30 a 40 °C.

Al comparar varias series, los botones sobre el filtro permiten editar los límites de cada variable. El filtro se aplica por separado: una temperatura excluida no elimina la humedad del mismo registro. Campos vacíos, no numéricos, no finitos o límites invertidos bloquean esa comparación. Se aceptan punto o coma decimal y números negativos.

Los resultados, la gráfica y el CSV usan el mismo conjunto filtrado. Al desactivar el filtro se recuperan los cálculos sobre las lecturas descargadas.

## Extremos

**Consultar máximos y mínimos** analiza una variable de todo el historial de la fuente elegida. Presenta mínimo, máximo, fechas, cantidades, amplitud y repeticiones. En un empate muestra la primera fecha; una sola lectura puede ser ambos extremos.

En Firebase se descargan las lecturas de `lecturas/equipo01` y se escuchan los cambios mientras la pantalla está abierta. Los registros con fecha inválida se omiten con aviso. Esta consulta está pensada para el volumen académico; historiales grandes requerirían agregación o paginación.

## Exportación CSV

**Exportar CSV** abre el selector de archivos de Android sin permiso general de almacenamiento. El contenido queda fijado al pulsar el botón, aunque entren nuevas lecturas mientras se elige el destino.

- Por hora: 24 filas por variable seleccionada, con horas vacías incluidas.
- Por día: una fila por día y variable seleccionada.
- Extremos: mínimo y máximo de la variable elegida.

Se utiliza UTF-8 con BOM, separador `;`, coma decimal y tres decimales. Los valores ausentes quedan vacíos con estado **Sin datos** y cantidad cero. No se habilita la exportación si no hay ningún valor válido. Cada fila identifica variable, unidad, fecha y cantidad; con filtro activo incluye sus límites. La fuente meteorológica añade zona, coordenadas, proveedor y licencia.

## Clima de Costa Rica

**Cambiar** permite buscar por lugar o provincia entre diez zonas del catálogo. No se solicita GPS. La zona se guarda en preferencias y cada historial utiliza un archivo privado distinto. Se mantiene el nombre `clima_santa_clara.json` para recuperar las descargas existentes de Santa Clara.

### Inicio: condiciones y pronóstico

Una petición a `/v1/forecast` solicita:

- `current`: temperatura, humedad, rocío, código WMO e indicador de día/noche.
- `hourly`: las tres variables, código WMO y probabilidad de precipitación para diez días. La interfaz presenta las próximas 24 horas.
- `daily`: temperatura mínima y máxima, condición y probabilidad máxima de precipitación para diez días, incluido hoy.

Se solicitan °C, porcentajes y tiempo Unix. El parser valida unidades, tamaños y fechas ordenadas. Los segundos Unix se convierten a milisegundos sin sumar de nuevo UTC−6. Un valor ausente queda sin dato; un código WMO ausente o desconocido no se presenta como cielo despejado.

La temperatura grande corresponde al dato actual del modelo. Las filas diarias muestran mínimos y máximos previstos; sus barras comparten el rango térmico del periodo. Los porcentajes junto a los iconos y al final de las columnas horarias representan probabilidad de lluvia.

El pronóstico se actualiza cada quince minutos mientras Inicio está visible. Cambiar de pantalla, fuente, abrir Información o enviar la app al fondo pausa las consultas periódicas. **Actualizar clima** permite consultar manualmente y no duplica una petición en curso. Una descarga iniciada puede finalizar, pero su resultado siempre queda asociado a su zona.

Los pronósticos se conservan en memoria durante la sesión. Si falla una actualización, se mantiene el resultado de esa zona con aviso. Se identifican datos de más de treinta minutos como anteriores. Una nueva sesión requiere conexión para obtener un pronóstico.

### Historial: consultas académicas

**Cargar historial** solicita las 240 horas de los diez días completos anteriores. Hoy y el pronóstico futuro quedan excluidos. La copia se valida y se escribe con `AtomicFile`; un error de red, formato o guardado conserva el archivo anterior.

Por hora propone el último día descargado, Por día el rango completo y Extremos todo el historial guardado. Un dato por hora produce un promedio horario igual a ese dato. Cambiar de zona limpia la consulta y muestra únicamente su historial; si falta una copia, aparece el control de descarga.

Los valores de Open-Meteo son estimaciones meteorológicas para coordenadas aproximadas. La celda del modelo puede estar cerca del lugar solicitado y no equivale a una estación local. Créditos y licencia CC BY 4.0 están en Información y en los CSV.

## Firebase

La ruta `lecturas/equipo01` contiene `ts` en milisegundos Unix y las variables `temperatura`, `humedad` y `puntoRocio`. Las consultas de fecha necesitan permisos de lectura y `.indexOn: ["ts"]`. Las pantallas de consulta no escriben datos ni cambian reglas.

## Información y apariencia

El botón ⓘ abre la ayuda sobre la pantalla actual. Volver o Atrás conservan fechas, variables, filtros y resultados. El selector CLARO/OSCURO mantiene la paleta del proyecto y guarda la preferencia.

## Comprobación

Desde `APP-MOVIL/android`, en Windows:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Las pruebas unitarias cubren cálculos, filtros independientes, escalas por unidad, CSV, respuestas incompletas y fechas y unidades del pronóstico. La compilación y las pruebas no verifican la interacción visual del teléfono.

Recorrido manual recomendado:

1. Abrir Inicio con Clima de Costa Rica y probar Santa Clara, San José y Liberia. Verificar que cambia el lugar, la temperatura, las condiciones y el pronóstico.
2. Cargar el historial de dos zonas, alternar entre ellas y comprobar la procedencia del CSV. Cambiar de zona durante una descarga para comprobar que su respuesta no aparece en otro lugar.
3. Seleccionar temperatura y humedad en Por hora; añadir rocío. Verificar leyenda, ejes, colores y selección por toque.
4. Consultar un rango diario. Elegir fechas desde la gráfica y el menú: debe aparecer un solo resumen de fecha, con una fila por variable.
5. Editar un filtro y comparar sus cantidades con el CSV. Desactivarlo y recuperar los resultados originales.
6. Consultar periodos vacíos y una sola lectura. No deben aparecer promedios inventados ni líneas sobre huecos.
7. Cerrar la app y abrirla sin conexión: los historiales descargados deben estar disponibles. Intentar actualizar y comprobar que se conserva la copia anterior.
8. Recorrer las pantallas con ambos temas y fuente grande. Abrir y cerrar Información, exportar, cancelar el selector de archivos y revisar el contraste de las gráficas.
9. Volver a ESP32 y enviar una lectura desde Wokwi. Las consultas Firebase deben seguir actualizándose.
