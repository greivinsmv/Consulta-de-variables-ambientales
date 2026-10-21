"""Comprueba las lecturas anónimas previstas sin escribir ni mostrar mediciones."""

import json
import sys
import urllib.error
import urllib.request

BASE_DATOS = "https://proyecto-iot-fa353-default-rtdb.firebaseio.com"
CONSULTAS = (
    ("Raíz", "/.json?shallow=true", False),
    ("Otro equipo", "/lecturas/equipo02.json?shallow=true", False),
    ("Ruta privada", "/privado.json?shallow=true", False),
    ("Última lectura pública", '/lecturas/equipo01.json?orderBy=%22ts%22&limitToLast=1', True),
)
CAMPOS_LECTURA = {"temperatura", "humedad", "puntoRocio", "ts"}


def comprobar(nombre, ruta, publica):
    try:
        with urllib.request.urlopen(BASE_DATOS + ruta, timeout=20) as respuesta:
            datos = json.load(respuesta)
    except urllib.error.HTTPError as error:
        # Un 403 del proxy no prueba que Firebase haya denegado el acceso.
        try:
            mensaje = json.loads(error.read()).get("error", "")
        except (ValueError, AttributeError):
            mensaje = ""
        denegado = error.code in (401, 403) and "permission denied" in str(mensaje).lower()
        if denegado and not publica:
            print(f"OK: {nombre}: Firebase deniega la lectura anónima.")
            return True
        print(f"REVISAR: {nombre}: HTTP {error.code}; no cumple el acceso esperado.")
        return False
    except (urllib.error.URLError, TimeoutError, OSError, ValueError):
        print(f"SIN VERIFICAR: {nombre}: falló la conexión o la respuesta. No se puede concluir sobre los permisos.")
        return False

    if not publica:
        print(f"REVISAR: {nombre}: la lectura anónima está permitida y debería estar bloqueada.")
        return False
    if datos is None:
        print(f"OK: {nombre}: acceso permitido; no hay registros.")
        return True
    if not isinstance(datos, dict):
        print(f"REVISAR: {nombre}: formato inesperado.")
        return False
    for lectura in datos.values():
        if not isinstance(lectura, dict) or set(lectura) - CAMPOS_LECTURA:
            print(f"REVISAR: {nombre}: hay campos o registros fuera del esquema esperado. No se muestran sus valores.")
            return False
    print(f"OK: {nombre}: acceso permitido; no se muestran las mediciones.")
    return True


def main():
    resultados = [comprobar(*consulta) for consulta in CONSULTAS]
    print("Esta comprobación solo revisa lecturas anónimas; no prueba permisos de escritura ni todas las rutas de la base.")
    return 0 if all(resultados) else 1


if __name__ == "__main__":
    sys.exit(main())
