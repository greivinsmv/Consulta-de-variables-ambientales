# Componente web · Consulta Ambiental

Esta carpeta está reservada para la interfaz web del proyecto Consulta Ambiental. Su desarrollo corresponde a otro integrante del equipo; actualmente no contiene una aplicación ejecutable.

## Integración prevista

La interfaz web compartirá con la aplicación Android la fuente de datos en Firebase Realtime Database. El esquema utilizado por el proyecto contiene:

| Campo | Descripción |
| --- | --- |
| `temperatura` | Temperatura en °C |
| `humedad` | Humedad relativa en % |
| `puntoRocio` | Punto de rocío calculado en °C |
| `ts` | Fecha del servidor en milisegundos Unix |

Los registros se almacenan en `lecturas/equipo01`. La descripción general del sistema está en el [README principal](../README.md) y el funcionamiento del cliente Android en la [guía móvil](../APP-MOVIL/README.md).

La elección de tecnologías, las instrucciones de ejecución y las funcionalidades web se documentarán junto con su implementación.
