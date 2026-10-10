# Consultas ambientales

La app permite ver la lectura actual y consultar promedios por hora o por día.
La navegación superior se desplaza horizontalmente si no caben los botones.

## Promedios diarios

Selecciona fecha inicial y final, elige una variable con los botones y pulsa
**Consultar promedios diarios**. Ambas fechas se incluyen y se interpretan en
`America/Costa_Rica`. Se rechazan fechas invertidas y fechas futuras.

Cada promedio diario usa todas las lecturas numéricas finitas de esa variable
registradas durante el día; no es el promedio de promedios horarios.
La cantidad de lecturas puede diferir entre variables si un registro está incompleto.
Los días sin lecturas se muestran como **Sin datos**, sin sustituirlos por cero.
Los extremos del resumen son extremos de los promedios diarios del rango,
no máximos y mínimos de las lecturas individuales.

El gráfico de barras tiene una escala por variable, admite valores negativos y
permite desplazamiento horizontal. La lista detalla fecha, promedio y cantidad.
Los resultados se actualizan al recibir lecturas nuevas dentro del rango consultado.
Cambiar las fechas limpia los resultados anteriores y requiere volver a consultar.

## Firebase

Ruta: `lecturas/equipo01`. Cada registro contiene `ts` numérico (milisegundos Unix),
`temperatura`, `humedad` y `puntoRocio`. Las consultas de fecha requieren un índice
`.indexOn: ["ts"]` en esa ruta y permisos de lectura apropiados.
No se escriben registros desde estas pantallas ni se cambian las reglas desde el código.

## Comprobar en Windows

Desde la carpeta `APP-MOVIL/android`:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Para la prueba manual, consulta un día con registros, un rango que lo incluya junto
con días vacíos, y un rango invertido (el botón debe quedar deshabilitado).
Cambia de variable y comprueba que se muestran sus propios resultados.
La comprobación con varios días poblados requiere un historial en Firebase;
la app no genera ni carga datos sintéticos.

## Máximos y mínimos históricos

Selecciona una variable y pulsa **Consultar máximos y mínimos**. Se analizan las
lecturas originales de todo `lecturas/equipo01`, con fecha y hora de Costa Rica,
por separado para cada variable. Los registros sin fecha numérica válida se
omiten con aviso; los valores ausentes y no finitos no cuentan para esa variable.
Se muestran mínimo, máximo, fechas, cantidad válida y amplitud del rango.
Cuando hay empates se muestra la fecha más antigua y el número de repeticiones.
Una sola lectura puede ser a la vez mínimo y máximo.

Esta consulta descarga el historial completo y escucha nuevos cambios mientras
la pantalla está abierta. Es adecuada para el conjunto de muestra del proyecto;
un historial de gran volumen requerirá agregación o paginación.

Prueba manual: consulta con las lecturas actuales y cambia los valores del DHT22
en Wokwi para enviar una nueva lectura. Comprueba los extremos contra Firebase.
Cambia de variable y verifica que sus extremos y cantidades corresponden.

## Apariencia

La navegación inferior ofrece Inicio, Por hora, Por día y Extremos.
La cabecera usa un degradado azul/violeta y las consultas se presentan en tarjetas
redondeadas. Inicio destaca temperatura y muestra humedad, punto de rocío y fecha.
El interruptor **Modo oscuro** guarda la preferencia localmente y la conserva al
cerrar la app; en el primer inicio usa el tema del sistema. La apariencia no usa
los colores dinámicos del teléfono para conservar la paleta del proyecto.

Comprobación visual pendiente en el emulador: recorrer las cuatro pantallas,
activar/desactivar el modo oscuro, reiniciar la app y verificar la preferencia,
y probar un tamaño de fuente mayor desde la configuración de Android.

## Selector de hora

En **Por hora**, el menú **Hora del promedio** permite elegir cualquiera de las
24 franjas (00:00–00:59 a 23:00–23:59). La franja se conserva al cambiar
de variable con los botones. Se muestra solo el promedio y cantidad de la franja elegida,
o **Sin datos**; los gráficos conservan la evolución de todo el día.
Cambiar de hora no requiere una nueva descarga ni cambia los cálculos.

## Botones de variables y gráficos plegables

Las tres consultas tienen botones de Temperatura, Humedad y Punto de rocío.
Solo una variable está activa; por defecto es Temperatura. Al cambiarla se
reutilizan los registros descargados y no se modifica la consulta a Firebase.
Los gráficos horarios y diarios están plegados bajo **Ver gráfico**.
Cuando solo hay un promedio horario, se muestra un punto con ejes y un aviso
de que aún no hay evolución para comparar, sin dibujar una línea inexistente.

## Gráficos con ejes

Los gráficos horarios muestran el periodo entre la primera y última hora con datos,
escala vertical con unidades, cuadrícula y etiquetas de horas. Las horas intermedias
vacías no se conectan. Con seis puntos o menos se rotulan también los valores.
Tocar un punto elige la hora en la tarjeta de resultados; el menú sigue permitiendo
consultar cualquiera de las 24 horas. Una sola hora se representa como punto
etiquetado con aviso de que aún no hay evolución. Las escalas tienen margen incluso
si los valores son constantes.

Los gráficos diarios muestran fechas, valores sobre las barras y escala vertical.
Las barras parten de cero, también para valores negativos. Los días vacíos se
representan con un guion. Todos los valores siguen disponibles en texto.
La comprobación de legibilidad, selección por toque y modo oscuro en el emulador
se realiza además de la compilación y pruebas de cálculo.

El selector de tema usa una cápsula con sol/luna y etiquetas CLARO/OSCURO.
El control circular se desplaza al cambiar el modo; toda la cápsula es pulsable.
Expone el estado como interruptor para lectores de pantalla y mantiene la misma
preferencia local de apariencia. Comprobar la animación y contraste en el emulador.

## Exportar CSV

Después de consultar datos válidos, **Exportar CSV** abre el selector de archivos
Android. El usuario elige nombre y ubicación sin conceder acceso general al
almacenamiento. Se informa guardado correcto, cancelación o fallo. La exportación
usa una copia de los resultados al pulsar el botón; nuevos registros no cambian
el contenido mientras se elige el destino.

- Por hora: todas las 24 franjas del día consultado, de la variable seleccionada.
- Por día: todos los días del rango consultado, de la variable seleccionada.
- Extremos: mínimo y máximo, primera fecha de cada extremo, repeticiones y cantidad
  de lecturas válidas.

Los campos incluyen unidades y zona `America/Costa_Rica`. El archivo usa UTF-8
con BOM, separador punto y coma, coma decimal y tres decimales; la pantalla redondea
a un decimal. Los promedios ausentes quedan vacíos con estado **Sin datos** y cero
lecturas; un promedio de cero se escribe numéricamente. No se exportan consultas
sin ninguna lectura válida. Si Excel no separa las columnas automáticamente,
importar desde texto/CSV indicando UTF-8 y separador punto y coma.

Prueba manual pendiente: guardar CSV de cada consulta, cancelar el selector,
abrir los archivos y comparar cifras y fechas con los resultados de la app.
La exportación no sube datos a Firebase ni a otro servicio.

## Filtro de valores atípicos

Las tres consultas tienen **Filtrar valores atípicos**, apagado inicialmente.
Se filtra por límites configurables e inclusivos de la variable seleccionada;
no es un detector estadístico ni una validación de que los datos sean reales.
Los límites iniciales sirven para demostrar el filtro: temperatura -10 a 50 °C,
humedad 0 a 100 %, punto de rocío -30 a 40 °C. El equipo debe ajustarlos y justificar
los que use para su conjunto ambiental. Se aceptan punto o coma decimal y negativos.

Cada variable conserva sus límites mientras la pantalla está abierta. Se rechazan
campos vacíos, valores no numéricos/no finitos y mínimo mayor que máximo.
El resumen distingue lecturas utilizadas, valores fuera de límites y datos ausentes
(o no válidos). Un valor atípico de temperatura no excluye la humedad del mismo registro.
No se escribe ni borra nada de Firebase. El filtro no altera la lectura actual de Inicio.

Promedios, extremos, gráficos y CSV usan el mismo conjunto filtrado. Los CSV con
filtro activo agregan columnas `filtro`, `limite_inferior` y `limite_superior`.
Si ninguna lectura pasa el filtro, se muestra ausencia de datos y se deshabilita la
exportación; no se inventan promedios cero. Al desactivar se recuperan los resultados
sin el filtro, usando los registros originales descargados.

Prueba manual: consulta una variable con valores de prueba, activa el filtro,
reduce el límite máximo para excluir una lectura y comprueba cantidades, promedio,
gráfico y CSV. Desactiva el filtro y verifica que reaparece el resultado original.
Prueba límites invertidos y cambia de variable para comprobar sus límites propios.
