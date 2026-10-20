"""Regresiones de la web. Las respuestas HTTP son locales; nunca se escribe en Firebase."""

import functools
import http.server
import json
import os
from pathlib import Path
import shutil
import threading
import unittest
from datetime import datetime, timedelta, timezone
from urllib.parse import parse_qs, urlparse

from playwright.sync_api import sync_playwright, expect


WEB = Path(__file__).resolve().parents[1]
FIREBASE = "https://proyecto-iot-fa353-default-rtdb.firebaseio.com/**"
CLIMA = "https://api.open-meteo.com/**"
HOY, AYER, ANTES = "2026-10-19", "2026-10-18", "2026-10-17"
CR = timezone(timedelta(hours=-6))


def ts(dia, hora=0):
    return int(datetime.fromisoformat(dia).replace(hour=hora, tzinfo=CR).timestamp() * 1000)


def lectura(valor, dia=AYER, hora=8):
    return {"temperatura": valor, "humedad": 60, "puntoRocio": 15, "ts": ts(dia, hora)}


LECTURAS = {"a": lectura(20), "b": lectura(30, hora=10)}
HISTORIAL = {
    "version": 1, "zonaId": "santa_clara", "rango": {"inicio": ANTES, "fin": AYER},
    "descargadoEn": ts(HOY),
    "registros": [
        {"ts": ts(dia, hora), "valores": {"temperatura": valor, "humedad": 60, "puntoRocio": 15}}
        for dia, valor in [(ANTES, 10), (AYER, 30)] for hora in range(24)
    ],
}
UNIDADES = {"time": "unixtime", "temperature_2m": "°C", "relative_humidity_2m": "%", "dew_point_2m": "°C"}
PRONOSTICO = {
    "current_units": UNIDADES, "hourly_units": UNIDADES,
    "current": {"time": ts(HOY, 12) // 1000, "interval": 900, "temperature_2m": 25,
                "relative_humidity_2m": 60, "dew_point_2m": 15, "weather_code": 3, "is_day": 1},
    "hourly": {"time": [], "temperature_2m": []}, "daily": {"time": []},
}

# Estas pruebas verifican controles y datos. El dibujo se sustituye para no depender
# de un CDN ni de animaciones; pruebas.html comprueba los cálculos de las series.
CHART = """
window.Chart = class {
  constructor(canvas, config) { this.config = config; }
  destroy() {}
};
"""


class ServidorSilencioso(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *_):
        pass


class InterfazTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        manejador = functools.partial(ServidorSilencioso, directory=str(WEB))
        cls.servidor = http.server.ThreadingHTTPServer(("127.0.0.1", 0), manejador)
        cls.hilo = threading.Thread(target=cls.servidor.serve_forever, daemon=True)
        cls.hilo.start()
        cls.base = f"http://127.0.0.1:{cls.servidor.server_port}"
        cls.playwright = sync_playwright().start()
        ejecutable = os.environ.get("CHROMIUM_PATH") or shutil.which("chromium")
        cls.browser = cls.playwright.chromium.launch(executable_path=ejecutable, headless=True)

    @classmethod
    def tearDownClass(cls):
        cls.browser.close()
        cls.playwright.stop()
        cls.servidor.shutdown()
        cls.servidor.server_close()
        cls.hilo.join()

    def setUp(self):
        self.contexto = self.browser.new_context(viewport={"width": 390, "height": 844})
        self.page = self.contexto.new_page()
        self.errores = []
        self.page.on("pageerror", lambda error: self.errores.append(str(error)))
        self.page.clock.install(time=datetime.fromisoformat(HOY).replace(hour=12, tzinfo=CR))
        self.page.route("https://cdnjs.cloudflare.com/**", lambda ruta: ruta.fulfill(
            body=CHART if "chart.umd" in ruta.request.url else "", content_type="application/javascript"))
        self.responder_firebase = self.firebase_normal
        self.responder_clima = lambda ruta: ruta.fulfill(json=PRONOSTICO)
        self.page.route(FIREBASE, lambda ruta: self.responder_firebase(ruta))
        self.page.route(CLIMA, lambda ruta: self.responder_clima(ruta))

    def tearDown(self):
        self.contexto.close()
        self.assertEqual(self.errores, [], "Errores de JavaScript no controlados")

    def firebase_normal(self, ruta):
        parametros = parse_qs(urlparse(ruta.request.url).query)
        if "limitToFirst" in parametros:
            datos = {"a": LECTURAS["a"]}
        elif "limitToLast" in parametros:
            datos = {"b": LECTURAS["b"]}
        else:
            datos = LECTURAS
        ruta.fulfill(json=datos)

    def abrir(self, fuente="esp32"):
        preferencias = {"fuente": fuente, "tema": "claro", "clima_santa_clara": json.dumps(HISTORIAL)}
        self.page.add_init_script("for (const [k,v] of Object.entries(" + json.dumps(preferencias) + ")) localStorage.setItem(k,v);")
        self.page.goto(self.base, wait_until="networkidle")

    def consultar_horas(self, fuente="esp32"):
        self.abrir(fuente)
        self.page.locator('[data-ir="hora"]').click()
        self.page.locator("[data-consultar]").click()
        expect(self.page.locator("[data-periodo]")).to_be_visible()

    def test_pruebas_del_nucleo(self):
        self.page.goto(self.base + "/pruebas.html")
        expect(self.page.locator("#resumen")).to_contain_text("58")
        self.assertEqual(self.page.locator(".fallo").count(), 0)

    def comprobar_edicion_del_filtro(self):
        self.page.locator("[data-filtro-activo]").click()
        maximo = self.page.locator('[data-limite="maximo"]')
        maximo.fill("")
        self.page.keyboard.type("100", delay=20)
        expect(maximo).to_have_value("100")
        expect(maximo).to_be_focused()
        maximo.evaluate("campo => campo.setSelectionRange(1, 2)")
        self.page.keyboard.type("2")
        expect(maximo).to_have_value("120")
        self.assertEqual(maximo.evaluate("campo => campo.selectionStart"), 2)
        minimo = self.page.locator('[data-limite="minimo"]')
        minimo.fill("")
        self.page.keyboard.type("-12,5", delay=20)
        expect(minimo).to_have_value("-12,5")
        expect(minimo).to_be_focused()
        expect(self.page.locator("[data-consultar]")).to_be_enabled()

    def test_editar_filtro_conserva_foco_y_cursor(self):
        self.consultar_horas()
        self.comprobar_edicion_del_filtro()

    def test_editar_filtro_de_extremos_conserva_foco(self):
        self.abrir()
        self.page.locator('[data-ir="historico"]').click()
        self.comprobar_edicion_del_filtro()

    def test_filtro_invalido_oculta_resultados_y_exportaciones(self):
        self.consultar_horas()
        self.page.locator("[data-filtro-activo]").click()
        self.page.locator('[data-limite="maximo"]').fill("25")
        expect(self.page.locator(".estadisticas")).to_contain_text("1 lecturas")
        self.page.locator('[data-limite="maximo"]').fill("")
        expect(self.page.locator("[data-consultar]")).to_be_disabled()
        expect(self.page.locator("[data-exportar]")).to_have_count(0)
        expect(self.page.locator(".estadisticas")).to_have_count(0)
        self.page.locator('[data-limite="maximo"]').fill("25")
        expect(self.page.locator(".estadisticas")).to_contain_text("1 lecturas")
        with self.page.expect_download() as descarga:
            self.page.locator('[data-exportar="csv"]').click()
        csv = Path(descarga.value.path()).read_text(encoding="utf-8-sig")
        self.assertIn("limite_superior", csv)
        self.assertNotIn('"30,000"', csv)

    def test_extremos_no_exporta_con_filtro_invalido(self):
        self.abrir()
        self.page.locator('[data-ir="historico"]').click()
        self.page.locator("[data-consultar]").click()
        expect(self.page.locator(".panel-extremo")).to_have_count(2)
        self.page.locator("[data-filtro-activo]").click()
        self.page.locator('[data-limite="minimo"]').fill("no es un numero")
        expect(self.page.locator("[data-exportar]")).to_have_count(0)
        expect(self.page.locator(".panel-extremo")).to_have_count(0)
        expect(self.page.locator("[data-consultar]")).to_be_disabled()

    def test_resumen_y_exportaciones_respetan_el_dia_consultado(self):
        self.consultar_horas("clima")
        resumen = self.page.locator(".estadisticas")
        expect(resumen.locator(".estadistica__valor")).to_have_text(["30,0 °C"] * 3)
        expect(resumen).to_contain_text("24 lecturas")
        self.assertNotIn("17/10/2026", resumen.inner_text())

        with self.page.expect_download() as descarga:
            self.page.locator('[data-exportar="csv"]').click()
        csv = Path(descarga.value.path()).read_text(encoding="utf-8-sig")
        self.assertEqual(len(csv.splitlines()), 25)
        self.assertNotIn(ANTES, csv)
        self.assertIn('"30,000"', csv)

        # Captura las tablas entregadas a jsPDF: así se comprueba el contenido del
        # informe sin depender de fuentes PDF ni de un extractor instalado en el equipo.
        self.page.evaluate("""() => {
          window.tablasPdf = [];
          window.jspdf = { jsPDF: class {
            internal = {pageSize: {getWidth: () => 210, getHeight: () => 297}, getNumberOfPages: () => 1};
            setFont() {} setFontSize() {} setTextColor() {} text() {} addImage() {} setPage() {}
            splitTextToSize(texto) { return [texto]; }
            autoTable(tabla) { window.tablasPdf.push(tabla); this.lastAutoTable = {finalY: 80}; }
            save() { window.pdfGuardado = true; }
          }};
        }""")
        self.page.locator('[data-exportar="pdf"]').click()
        self.page.wait_for_function("window.pdfGuardado === true")
        fila = self.page.evaluate("window.tablasPdf[0].body[0]")
        self.assertEqual(fila[2], "30,000")
        self.assertEqual(fila[-1], "24")
        self.assertNotIn(ANTES, " ".join(fila))

    def test_resumen_diario_no_incluye_otros_dias_del_cache(self):
        self.abrir("clima")
        self.page.locator('[data-ir="dia"]').click()
        self.page.locator('[data-fecha="inicio"]').fill(AYER)
        self.page.locator("[data-consultar]").click()
        expect(self.page.locator(".estadisticas")).to_contain_text("24 lecturas")
        expect(self.page.locator(".estadistica__valor")).to_have_text(["30,0 °C"] * 3)

    def test_extremos_descarta_respuesta_de_otra_fuente(self):
        self.abrir()
        self.page.locator('[data-ir="historico"]').click()
        pendientes = []
        self.responder_firebase = lambda ruta: pendientes.append(ruta)
        self.page.locator("[data-consultar]").click()
        self.page.wait_for_function("document.querySelector('[data-consultar]').disabled")
        self.assertEqual(len(pendientes), 1)
        self.page.locator('[data-fuente="clima"]').click()
        self.page.locator("[data-consultar]").click()
        expect(self.page.locator(".panel-extremo__valor")).to_have_text(["10,0 °C", "30,0 °C"])
        pendientes[0].fulfill(json={"tardia": lectura(99)})
        self.page.wait_for_timeout(100)
        expect(self.page.locator(".panel-extremo__valor")).to_have_text(["10,0 °C", "30,0 °C"])

    def test_cambiar_fecha_descarta_consulta_pendiente(self):
        def dos_dias(ruta):
            parametros = parse_qs(urlparse(ruta.request.url).query)
            if "limitToFirst" in parametros:
                ruta.fulfill(json={"anterior": lectura(10, ANTES)})
            else:
                self.firebase_normal(ruta)
        self.responder_firebase = dos_dias
        self.abrir()
        self.page.locator('[data-ir="hora"]').click()
        pendientes = []
        self.responder_firebase = lambda ruta: pendientes.append(ruta)
        self.page.locator("[data-consultar]").click()
        self.page.wait_for_function("document.querySelector('[data-consultar]').disabled")
        self.assertEqual(len(pendientes), 1)
        self.page.locator('[data-fecha="dia"]').fill(ANTES)
        pendientes[0].fulfill(json=LECTURAS)
        self.page.wait_for_timeout(100)
        expect(self.page.locator("[data-periodo]")).to_have_count(0)
        expect(self.page.locator("[data-consultar]")).to_be_enabled()

    def test_firebase_vacio_no_reintenta_en_cada_render(self):
        consultas = []
        def vacio(ruta):
            consultas.append(ruta.request.url)
            ruta.fulfill(json=None)
        self.responder_firebase = vacio
        self.abrir()
        for _ in range(3):
            self.page.locator("#boton-tema").click()
        self.page.wait_for_timeout(150)
        limites = [url for url in consultas if '%22ts%22' in url]
        self.assertEqual(len(limites), 2)

    def test_pronostico_fallido_espera_y_permite_reintento_manual(self):
        consultas = []
        def fallo(ruta):
            consultas.append(ruta.request.url)
            ruta.fulfill(status=429, json={"error": True})
        self.responder_clima = fallo
        self.abrir("clima")
        self.page.locator("#boton-tema").click()
        expect(self.page.locator(".aviso--error")).to_contain_text("demasiadas consultas")
        self.assertEqual(len(consultas), 1)
        self.page.locator("[data-refrescar-clima]").click()
        expect(self.page.locator(".aviso--error")).to_contain_text("demasiadas consultas")
        self.assertEqual(len(consultas), 2)
        self.page.clock.fast_forward(15 * 60 * 1000)
        expect(self.page.locator(".aviso--error")).to_contain_text("demasiadas consultas")
        self.assertEqual(len(consultas), 3)


if __name__ == "__main__":
    unittest.main()
