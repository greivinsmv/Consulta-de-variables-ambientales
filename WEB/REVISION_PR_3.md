# Correcciones de la revisión del PR #3

Seguimiento del [PR #3: Feat/pagina web](https://github.com/greivinsmv/Consulta-de-variables-ambientales/pull/3).
Rama de trabajo: `fix/web-consultas-confiables`.
Checklist en GitHub: [issue #4](https://github.com/greivinsmv/Consulta-de-variables-ambientales/issues/4).

Una tarea se marca terminada después de ejecutar su prueba de regresión.

- [x] Limitar los reintentos de Firebase y Open-Meteo cuando faltan datos o falla la conexión.
- [x] Descartar respuestas pendientes al cambiar de fuente, zona o fechas.
- [x] Calcular el resumen y el PDF solo con los registros del periodo consultado.
- [x] Bloquear resultados y exportaciones si el filtro activo es inválido.
- [x] Conservar el foco y el cursor al escribir los límites del filtro.
- [x] Respetar la selección de horas y días sin lecturas.
- [x] Actualizar las fechas disponibles cuando llegan nuevas lecturas del ESP32.
- [ ] Recuperar Inicio después de un error aunque la última lectura no haya cambiado.

## Validación antes de integrar

- [ ] Ejecutar las 58 pruebas existentes de `pruebas.html`.
- [ ] Ejecutar las pruebas de regresión de interfaz.
- [ ] Comprobar las exportaciones CSV y PDF.
- [ ] Revisar el diff y publicar los commits en la rama de trabajo.

La integración se dirige a `feat/pagina-web`, para que las correcciones formen parte del PR #3.
No se modifica `main` ni se publica una revisión de GitHub.
