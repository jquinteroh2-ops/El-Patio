#!/usr/bin/env bash
#
# Restauración de un respaldo de El Patio.
#
# Este script BORRA lo que haya en la base de destino y lo reemplaza por el
# contenido del respaldo. Por eso exige que se le escriba el nombre de la base a
# mano: es la clase de operación que no debe poder hacerse por accidente con una
# flecha arriba en la terminal.
#
#   ./restaurar.sh respaldos/elpatio-20260820T210000Z.sql.gz
#
# Restaurar en una base limpia para comprobar que el respaldo sirve, sin tocar
# la de producción, es lo que hace `probar-respaldo.sh`. Hágalo cada tanto: un
# respaldo que nunca se restauró es una suposición, no una copia de seguridad.

set -euo pipefail

ARCHIVO="${1:-}"
URL="${DATABASE_URL:-postgresql://elpatio:elpatio@localhost:5433/elpatio}"
URL="${URL#jdbc:}"

if [ -z "$ARCHIVO" ]; then
  echo "Uso: $0 <archivo.sql.gz>" >&2
  echo "" >&2
  echo "Respaldos disponibles:" >&2
  ls -1t ./respaldos/elpatio-*.sql.gz 2>/dev/null | head -10 >&2 || echo "  (ninguno)" >&2
  exit 1
fi

if [ ! -f "$ARCHIVO" ]; then
  echo "ERROR: no existe $ARCHIVO" >&2
  exit 1
fi

if ! gzip -t "$ARCHIVO"; then
  echo "ERROR: el archivo está dañado. NO se tocó la base." >&2
  exit 1
fi

# La base a la que se va a escribir, para que quede a la vista antes de aceptar.
BASE="$(echo "$URL" | sed -E 's#.*/([^/?]+).*#\1#')"
SERVIDOR="$(echo "$URL" | sed -E 's#.*@([^/]+)/.*#\1#')"

echo ""
echo "==========================================================="
echo "  RESTAURACIÓN — ESTO BORRA LOS DATOS ACTUALES"
echo "==========================================================="
echo "  Archivo:   $ARCHIVO"
echo "  Servidor:  $SERVIDOR"
echo "  Base:      $BASE"
echo "==========================================================="
echo ""

# `CONFIRMAR` permite correrlo sin preguntar desde otro script, pero hay que
# escribir el nombre exacto de la base: no basta con un «sí».
if [ "${CONFIRMAR:-}" != "$BASE" ]; then
  printf "Escriba el nombre de la base para confirmar (%s): " "$BASE"
  read -r RESPUESTA
  if [ "$RESPUESTA" != "$BASE" ]; then
    echo "Cancelado. No se tocó nada."
    exit 1
  fi
fi

echo "Restaurando…"

# ON_ERROR_STOP hace que un error a mitad de camino detenga todo en vez de
# dejar la base a medio restaurar, que es peor que no haber empezado.
gunzip -c "$ARCHIVO" | psql "$URL" --set ON_ERROR_STOP=on --quiet --output=/dev/null

echo ""
echo "Restauración terminada. Comprobando…"

# Un conteo rápido: si esto sale en cero, algo salió mal y hay que saberlo ya.
psql "$URL" --tuples-only --command "
  select
    'usuarios=' || (select count(*) from usuarios) ||
    ' mesas='   || (select count(*) from mesas) ||
    ' carta='   || (select count(*) from items_carta) ||
    ' ordenes=' || (select count(*) from ordenes) ||
    ' pagos='   || (select count(*) from pagos);
"

echo ""
echo "Antes de dar por buena la restauración, arranque el backend contra esta"
echo "base y entre a /admin/cierre: si el turno cuadra, el respaldo sirvió."
