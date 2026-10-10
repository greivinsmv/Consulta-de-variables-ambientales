# Consulta de variables ambientales

Proyecto IC-3101: temperatura, humedad relativa y punto de rocío calculado.
Los datos se capturan en un ESP32 simulado en Wokwi y se guardan en Firebase
Realtime Database para consultarlos desde una app móvil y una web.

- [APP MOVIL](<APP MOVIL/README.md>): instrucciones de Android Studio y simulación.
- [WEB](WEB/README.md): espacio reservado para la web.

## Estado

- ESP32 + DHT22 y envío de datos: funcionamiento confirmado por el equipo.
- Aplicación Android y aplicación web: pendientes de implementación.
- Archivo local e historial sintético de 10 días: pendientes.

`ts` conserva la fecha/hora de las lecturas. `puntoRocio` es una estimación
calculada a partir de temperatura y humedad, no un tercer sensor independiente.
