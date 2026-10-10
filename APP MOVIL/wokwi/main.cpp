#include <math.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include "DHTesp.h"
 
// ---------- Configuración ----------
const char* WIFI_SSID    = "Wokwi-GUEST";   // red virtual de Wokwi
const char* WIFI_PASS    = "";              // abierta, sin contraseña
const char* FIREBASE_URL = "https://proyecto-iot-fa353-default-rtdb.firebaseio.com/";
const char* EQUIPO       = "equipo01";      // identificador del equipo
const int   PIN_DHT      = 15;
 
const unsigned long INTERVALO_MS    = 60000;   // una lectura por minuto
const unsigned long ESPERA_BASE_MS  = 15000;   // primera espera tras un fallo
const unsigned long ESPERA_MAX_MS   = 300000;  // espera máxima tras fallos (5 min)
 
// ---------- Objetos globales (se reutilizan entre envíos) ----------
DHTesp dht;
WiFiClientSecure cliente;   // un solo cliente TLS para toda la ejecución
HTTPClient http;
 
unsigned long ultimoIntento = 0;
unsigned long esperaActual  = INTERVALO_MS;
int fallosSeguidos = 0;
 
void conectarWiFi() {
  Serial.print("Conectando a WiFi");
  WiFi.begin(WIFI_SSID, WIFI_PASS, 6);      // el canal 6 acelera la conexión
  while (WiFi.status() != WL_CONNECTED) {
    delay(250);
    Serial.print(".");
  }
  Serial.print(" conectado. IP: ");
  Serial.println(WiFi.localIP());
}
 
// Aproximación de Magnus: punto de rocío en °C, con humedad en porcentaje.
// Es una variable calculada a partir del DHT22, no una medición independiente.
float calcularPuntoRocio(float temperatura, float humedad) {
  if (!isfinite(temperatura) || !isfinite(humedad) ||
      humedad <= 0.0f || humedad > 100.0f || temperatura <= -243.12f) {
    return NAN;
  }
  const float a = 17.62f;
  const float b = 243.12f;
  float gamma = logf(humedad / 100.0f) + a * temperatura / (b + temperatura);
  return b * gamma / (a - gamma);
}

bool enviarLectura(float temp, float hum, float puntoRocio) {
  String url = String(FIREBASE_URL) + "/lecturas/" + EQUIPO + ".json";
 
  // Con setReuse(true), si la conexión anterior sigue abierta,
  // begin() la aprovecha y NO repite el handshake TLS.
  http.begin(cliente, url);
  http.addHeader("Content-Type", "application/json");
 
  // ".sv":"timestamp" le pide a Firebase que ponga la hora del servidor
  char cuerpo[192];
  snprintf(cuerpo, sizeof(cuerpo),
           "{\"temperatura\":%.1f,\"humedad\":%.1f,\"puntoRocio\":%.1f,\"ts\":{\".sv\":\"timestamp\"}}",
           temp, hum, puntoRocio);
 
  unsigned long t0 = millis();
  int codigo = http.POST(cuerpo);
  unsigned long latencia = millis() - t0;
 
  Serial.printf("POST -> HTTP %d  (%lu ms)\n", codigo, latencia);
  if (codigo > 0) Serial.println(http.getString());
 
  if (codigo == 200) {
    http.end();            // con reuse activo, deja la conexión abierta
    return true;
  }
 
  // Fallo: cerrar todo para que el próximo intento empiece limpio
  http.end();
  cliente.stop();
  return false;
}
 
void setup() {
  Serial.begin(115200);
  dht.setup(PIN_DHT, DHTesp::DHT22);
  conectarWiFi();
 
  cliente.setInsecure();   // Simplificación de laboratorio: NO valida certificado
  http.setReuse(true);     // mantener la conexión viva (keep-alive)
  http.setTimeout(15000);  // tolerar la lentitud del gateway público
 
  ultimoIntento = millis() - INTERVALO_MS;   // primer envío inmediato
}
 
void loop() {
  if (WiFi.status() != WL_CONNECTED) {
    cliente.stop();
    conectarWiFi();
  }
 
  if (millis() - ultimoIntento >= esperaActual) {
    ultimoIntento = millis();
 
    TempAndHumidity d = dht.getTempAndHumidity();
    if (isnan(d.temperature) || isnan(d.humidity)) {
      Serial.println("Lectura inválida del sensor");
      return;
    }
    float puntoRocio = calcularPuntoRocio(d.temperature, d.humidity);
    if (!isfinite(puntoRocio)) {
      Serial.println("No se puede calcular punto de rocío: revise temperatura y humedad (>0 y <=100%).");
      return;
    }
    Serial.printf("T = %.1f C   H = %.1f %%   Punto de rocio = %.1f C\n",
                  d.temperature, d.humidity, puntoRocio);
 
    if (enviarLectura(d.temperature, d.humidity, puntoRocio)) {
      fallosSeguidos = 0;
      esperaActual = INTERVALO_MS;
    } else {
      // Retroceso exponencial: 15 s, 30 s, 60 s, 120 s... hasta 5 min
      fallosSeguidos++;
      esperaActual = ESPERA_BASE_MS << (fallosSeguidos - 1);
      if (fallosSeguidos > 5 || esperaActual > ESPERA_MAX_MS) {
        esperaActual = ESPERA_MAX_MS;
      }
      Serial.printf("Envío fallido (%d seguidos). Reintento en %lu s\n",
                    fallosSeguidos, esperaActual / 1000);
    }
  }
  delay(100);
}