#!/bin/sh
#
# Arranque del backend.
#
# Existe por una sola razón: EL VOLUMEN LLEGA CON DUEÑO ROOT.
#
# La imagen crea /aplicacion/datos y se lo da al usuario `elpatio` al
# construirse, pero el volumen se monta DESPUÉS, encima de esa carpeta y con sus
# propios permisos. El proceso, que corre sin privilegios, se encontraba con un
# `AccessDeniedException` al intentar crear la carpeta de documentos: las hojas
# de vida y los adjuntos de PQR no se podían guardar.
#
# La salida fácil era correr todo como root. Este guion hace lo contrario: entra
# como root, ajusta el dueño de la carpeta montada, y CAMBIA A `elpatio` antes
# de arrancar la máquina virtual. Root existe durante unos milisegundos y solo
# para un `chown`; el proceso que atiende peticiones —el único al que alguien
# podría llegar desde fuera— nunca tiene privilegios.
#
# Se usa `setpriv` y no `su`: `su` arranca una sesión intermedia que se queda de
# PID 1, y entonces la señal de apagado que manda la plataforma le llega a ella
# y no a Java. El contenedor tardaría treinta segundos en morir y se cerraría a
# la fuerza, cortando cualquier petición en curso. `setpriv` reemplaza el
# proceso, así que Java queda de PID 1 y recibe las señales directamente.
set -e

DATOS="${RUTA_DATOS:-/aplicacion/datos}"

if [ "$(id -u)" = "0" ]; then
  # Ninguna de las dos cosas es motivo para no arrancar. Si el volumen no se
  # deja tocar, el almacén de documentos ya avisa en la bitácora y el resto del
  # sistema —el salón, la cocina, la caja— sigue funcionando. Tumbar el
  # restaurante entero porque no se puede guardar una hoja de vida sería una
  # desproporción.
  mkdir -p "$DATOS/documentos" "$DATOS/imagenes" "$DATOS/erp" 2>/dev/null ||
    echo "[arranque] AVISO: no se pudieron crear las carpetas de $DATOS"
  chown -R elpatio:elpatio "$DATOS" 2>/dev/null ||
    echo "[arranque] AVISO: no se pudo ajustar el dueño de $DATOS"

  # Los identificadores se resuelven a número: `--reuid` acepta el nombre en las
  # versiones recientes de setpriv, pero no en todas, y el número funciona en
  # cualquiera.
  exec setpriv \
    --reuid="$(id -u elpatio)" \
    --regid="$(id -g elpatio)" \
    --init-groups \
    java $JAVA_OPTS -jar aplicacion.jar
fi

# Ya se está corriendo sin privilegios: alguien pasó `--user` al arrancar el
# contenedor, o la plataforma lo impone. No hay nada que ajustar y tampoco se
# podría; se arranca y punto.
echo "[arranque] Sin privilegios de root: no se ajusta el dueño de $DATOS"
exec java $JAVA_OPTS -jar aplicacion.jar
