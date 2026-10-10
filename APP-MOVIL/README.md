# Aplicación móvil

## Archivos

- `wokwi/`: simulación ESP32 + DHT22 confirmada por el equipo.
- `android/`: ubicación prevista del proyecto Android Studio; todavía no se ha creado.

## Primer paso en Android Studio

1. Clona este repositorio desde GitHub con **Get from VCS** (o **Clone Repository**, según la versión):
   `https://github.com/greivinsmv/Consulta-de-variables-ambientales.git`.
2. Elige **New Project > Empty Activity** (Jetpack Compose).
3. Usa el nombre `Consulta Ambiental`, el paquete `com.ic3101.consultaambiental`,
   Kotlin y mínimo SDK API 26.
4. En **Save location**, selecciona `APP MOVIL/android` dentro del repositorio clonado.
5. Espera a que termine Gradle Sync y ejecuta la pantalla inicial en un emulador o teléfono.
6. Antes de conectar Firebase, confirma que la app inicial abre correctamente.

No inicialices otro repositorio Git dentro de `android`: móvil, Wokwi y web
comparten el Git de la raíz. No subas `local.properties`, carpetas `.gradle/`,
`build/`, claves de firma ni credenciales.

## Datos que consumirá la app

Firebase Realtime Database, proyecto `proyecto-iot-fa353`, ruta `lecturas/equipo01`.
Cada registro tiene `temperatura` (°C), `humedad` (%), `puntoRocio` (°C calculados)
y `ts` (fecha/hora del servidor en milisegundos Unix).
La app será de consulta. Las reglas y la autenticación deben revisarse antes de
integrar la lectura; no es necesario habilitar escritura pública para la app.

## Validación de Wokwi

Carga los tres archivos de `wokwi/` en el proyecto guardado:
https://wokwi.com/projects/477474234161260545

Ejecuta la simulación y comprueba `HTTP 200` y un nuevo registro con las cuatro
propiedades. El equipo confirmó el funcionamiento de esta versión; no se volvió
a ejecutar en este entorno.

El firmware conserva la configuración del prototipo, incluido `setInsecure()`;
la validación del certificado TLS queda pendiente antes de usarlo en producción.
