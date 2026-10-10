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
