import contextlib
import importlib.util
import io
import json
from pathlib import Path
import unittest
from unittest.mock import patch
import urllib.error


RUTA = Path(__file__).resolve().parents[1] / "comprobar-acceso-publico.py"
ESPECIFICACION = importlib.util.spec_from_file_location("comprobar_acceso", RUTA)
comprobacion = importlib.util.module_from_spec(ESPECIFICACION)
ESPECIFICACION.loader.exec_module(comprobacion)

BASE = "https://example-default-rtdb.firebaseio.com"


class AccesoPublicoTest(unittest.TestCase):
    def test_exige_base_explicita_sin_hacer_peticiones(self):
        with patch.object(comprobacion.urllib.request, "urlopen") as abrir:
            with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as salida:
                comprobacion.main([])
        self.assertEqual(salida.exception.code, 2)
        abrir.assert_not_called()

    def test_rechaza_urls_con_credenciales_parametros_o_rutas(self):
        urls = [
            "http://example-default-rtdb.firebaseio.com",
            "https://user:password@example-default-rtdb.firebaseio.com",
            BASE + "?auth=TOKEN_DE_EJEMPLO",
            BASE + "/lecturas",
            "https://example.com",
            BASE + "#fragmento",
        ]
        for url in urls:
            with self.subTest(url=url), patch.object(comprobacion.urllib.request, "urlopen") as abrir:
                with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
                    comprobacion.main(["--database-url", url])
                abrir.assert_not_called()

    def test_consulta_solo_el_proyecto_indicado_sin_mostrar_mediciones(self):
        peticiones = []

        def responder(url, timeout):
            peticiones.append(url)
            if "limitToLast" not in url:
                raise urllib.error.HTTPError(
                    url, 401, "Unauthorized", None,
                    io.BytesIO(b'{"error":"Permission denied"}'),
                )
            return io.StringIO(json.dumps({
                "ejemplo": {"temperatura": 87654321, "humedad": 40, "puntoRocio": 9, "ts": 1}
            }))

        salida = io.StringIO()
        with patch.object(comprobacion.urllib.request, "urlopen", side_effect=responder):
            with contextlib.redirect_stdout(salida):
                codigo = comprobacion.main(["--database-url", BASE + "/"])
        self.assertEqual(codigo, 0)
        self.assertEqual(len(peticiones), 4)
        self.assertTrue(all(url.startswith(BASE + "/") for url in peticiones))
        self.assertNotIn("87654321", salida.getvalue())

    def test_admite_base_regional(self):
        url = "https://example.europe-west1.firebasedatabase.app/"
        self.assertEqual(comprobacion.url_base(url), url[:-1])

    def test_rechaza_campos_inesperados_sin_imprimir_su_contenido(self):
        salida = io.StringIO()
        respuesta = io.StringIO('{"lectura":{"campo_extra":"CONTENIDO_PRIVADO"}}')
        with patch.object(comprobacion.urllib.request, "urlopen", return_value=respuesta):
            with contextlib.redirect_stdout(salida):
                valido = comprobacion.comprobar(BASE, "Lectura", "/lecturas.json", True)
        self.assertFalse(valido)
        self.assertNotIn("CONTENIDO_PRIVADO", salida.getvalue())

    def test_un_error_del_proxy_no_confirma_permisos_de_firebase(self):
        error = urllib.error.HTTPError(
            BASE, 403, "Forbidden", None, io.BytesIO(b"<html>Proxy denied</html>")
        )
        with patch.object(comprobacion.urllib.request, "urlopen", side_effect=error):
            with contextlib.redirect_stdout(io.StringIO()):
                valido = comprobacion.comprobar(BASE, "Raiz", "/.json", False)
        self.assertFalse(valido)


if __name__ == "__main__":
    unittest.main()
