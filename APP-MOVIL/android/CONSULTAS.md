# Consultas ambientales

La app permite ver la lectura actual y consultar promedios por hora o por día.
La navegación superior se desplaza horizontalmente si no caben los botones.

## Promedios diarios

Selecciona fecha inicial y final, marca al menos una variable y pulsa
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
Desmarca todas las variables y comprueba que no se habilita la consulta.
La comprobación con varios días poblados requiere un historial en Firebase;
la app no genera ni carga datos sintéticos.

## Máximos y mínimos históricos

Selecciona variables y pulsa **Consultar máximos y mínimos**. Se analizan las
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
Desmarca todas las variables y verifica que el botón queda deshabilitado.

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
