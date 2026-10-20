"""Servidor de desarrollo para la carpeta WEB/.

La aplicación usa módulos de JavaScript, y los navegadores los bloquean cuando la página
se abre con doble clic desde el disco (file://). Hace falta servirla por HTTP, aunque sea
en la propia máquina: eso es lo único que hace este archivo.

Además pide al navegador que no guarde nada en caché. Sin eso, al editar un archivo el
navegador seguiría ejecutando la versión anterior y uno acaba depurando código que ya no
existe. En producción (GitHub Pages) interesa lo contrario, que los archivos se guarden en
caché, y por eso las hojas de estilo llevan `?v=` en index.html.

Es una herramienta de desarrollo: no forma parte de lo que se publica.

Uso:
    python servidor-dev.py              # arranca en el puerto 8000
    python servidor-dev.py 8080         # arranca en otro puerto
    python servidor-dev.py --abrir      # arranca y abre el navegador
"""

import errno
import functools
import http.server
import socket
import sys
import threading
import webbrowser
from pathlib import Path

# La carpeta se resuelve a partir de la ubicación de este archivo, no del directorio
# actual: así funciona igual con doble clic que desde la terminal, esté donde esté.
#
# Admite las dos colocaciones posibles: junto a una carpeta WEB (en la raíz del proyecto)
# o dentro de ella. Así el mismo archivo sirve tal cual esté en este repositorio y en el
# del equipo, sin tener que mantener dos versiones.
_AQUI = Path(__file__).resolve().parent
CARPETA_WEB = _AQUI / "WEB" if (_AQUI / "WEB").is_dir() else _AQUI


class SinCache(http.server.SimpleHTTPRequestHandler):
    """Sirve los archivos pidiendo al navegador que no los guarde."""

    def end_headers(self):
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate")
        self.send_header("Pragma", "no-cache")
        self.send_header("Expires", "0")
        super().end_headers()

    def log_message(self, formato, *args):
        # El registro por omisión llena la consola con una línea por archivo.
        if not args or not str(args[0]).startswith("GET"):
            super().log_message(formato, *args)


def puerto_ocupado(puerto):
    """Comprueba si ya hay algo escuchando en el puerto.

    Hace falta mirarlo antes de arrancar: `ThreadingHTTPServer` activa SO_REUSEADDR, y en
    Windows eso permite enlazar un puerto que ya está en uso en vez de dar error. El
    resultado serían dos servidores peleándose por las peticiones, que es justo el tipo de
    fallo que cuesta media hora entender.
    """
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as prueba:
        prueba.settimeout(0.4)
        return prueba.connect_ex(("127.0.0.1", puerto)) == 0


def leer_argumentos(argumentos):
    puerto = 8000
    abrir = False
    for argumento in argumentos:
        if argumento in ("--abrir", "-a"):
            abrir = True
        elif argumento.isdigit():
            puerto = int(argumento)
        else:
            print(f"Argumento no reconocido: {argumento}")
            print(__doc__)
            sys.exit(1)
    return puerto, abrir


def main():
    puerto, abrir = leer_argumentos(sys.argv[1:])

    # Se comprueba el index y no solo la carpeta: con las dos colocaciones posibles,
    # saber que el directorio existe no basta para saber que es el correcto.
    if not (CARPETA_WEB / "index.html").is_file():
        print(f"No se encontró index.html en {CARPETA_WEB}")
        print("Coloca este archivo dentro de la carpeta WEB, o junto a ella.")
        input("\nPulsa Enter para cerrar...")
        return

    manejador = functools.partial(SinCache, directory=str(CARPETA_WEB))
    direccion = f"http://localhost:{puerto}"

    if puerto_ocupado(puerto):
        print(f"El puerto {puerto} ya está ocupado.")
        print(f"Puede que el servidor ya esté corriendo: prueba a abrir {direccion}")
        print(f"O usa otro puerto:  python servidor-dev.py {puerto + 1}")
        if abrir:
            webbrowser.open(direccion)
        input("\nPulsa Enter para cerrar...")
        return

    try:
        servidor = http.server.ThreadingHTTPServer(("127.0.0.1", puerto), manejador)
    except OSError as error:
        # En Windows el error llega como WSAEADDRINUSE (10048), no como el EADDRINUSE
        # de siempre, así que se comprueban los dos.
        if getattr(error, "errno", None) in (errno.EADDRINUSE, 10048):
            print(f"El puerto {puerto} ya está ocupado.")
            print(f"Puede que el servidor ya esté corriendo: prueba a abrir {direccion}")
            print(f"O usa otro puerto:  python servidor-dev.py {puerto + 1}")
        else:
            print(f"No se pudo arrancar el servidor: {error}")
        input("\nPulsa Enter para cerrar...")
        return

    with servidor:
        print(f"Sirviendo {CARPETA_WEB.name}/ en {direccion}")
        print(f"Pruebas del núcleo en {direccion}/pruebas.html")
        print("\nDeja esta ventana abierta mientras uses la página.")
        print("Para detener el servidor, pulsa Ctrl+C.\n")

        if abrir:
            # Un hilo aparte: el navegador tarda en responder y el servidor debe quedar
            # escuchando mientras tanto.
            threading.Timer(1.0, lambda: webbrowser.open(direccion)).start()

        try:
            servidor.serve_forever()
        except KeyboardInterrupt:
            print("\nServidor detenido.")


if __name__ == "__main__":
    main()
