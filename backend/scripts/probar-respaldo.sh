#!/usr/bin/env bash
#
# Prueba de que el respaldo sirve.
#
# Toma el respaldo más reciente, lo restaura en una base NUEVA y vacía, y
# compara los conteos contra la base de origen. No toca la base de producción en
# ningún momento: crea `elpatio_prueba_restauracion`, la usa y la borra.
#
# Correr esto cada tanto es la diferencia entre tener respaldos y creer que se
# tienen. Un volcado que nunca se restauró no es una copia de seguridad, es una
# suposición.
#
#   ./probar-respaldo.sh

set -euo pipefail

URL="${DATABASE_URL:-postgresql://elpatio:elpatio@localhost:5433/elpatio}"
URL="${URL#jdbc:}"
DESTINO="${DESTINO:-./respaldos}"
BASE_PRUEBA="elpatio_prueba_restauracion"

# Se arma la URL de la base de prueba reemplazando el nombre al final.
URL_PRUEBA="$(echo "$URL" | sed -E "s#/[^/?]+(\?.*)?\$#/${BASE_PRUEBA}\1#")"
# Para crear y borrar bases hay que conectarse a otra: `postgres` siempre existe.
URL_ADMIN="$(echo "$URL" | sed -E "s#/[^/?]+(\?.*)?\$#/postgres\1#")"

ULTIMO="$(ls -1t "${DESTINO}"/elpatio-*.sql.gz 2>/dev/null | head -1 || true)"
if [ -z "$ULTIMO" ]; then
  echo "No hay respaldos en ${DESTINO}. Corra primero ./respaldar.sh" >&2
  exit 1
fi

echo "Probando el respaldo: $ULTIMO"
echo ""

conteos() {
  psql "$1" --tuples-only --no-align --command "
    select
      (select count(*) from usuarios) || ',' ||
      (select count(*) from mesas) || ',' ||
      (select count(*) from items_carta) || ',' ||
      (select count(*) from ordenes) || ',' ||
      (select count(*) from items_orden) || ',' ||
      (select count(*) from pagos) || ',' ||
      (select count(*) from zonas_domicilio);
  " | tr -d ' '
}

ORIGEN="$(conteos "$URL")"
echo "Origen:      $ORIGEN"

# Se limpia cualquier resto de una prueba anterior que haya quedado a medias.
psql "$URL_ADMIN" --quiet --command "drop database if exists ${BASE_PRUEBA};" >/dev/null
psql "$URL_ADMIN" --quiet --command "create database ${BASE_PRUEBA};" >/dev/null

# `trap` se asegura de borrar la base de prueba aunque la restauración falle:
# dejarla colgando ocuparía espacio y confundiría a quien mire la lista.
limpiar() {
  psql "$URL_ADMIN" --quiet --command "drop database if exists ${BASE_PRUEBA};" >/dev/null 2>&1 || true
}
trap limpiar EXIT

gunzip -c "$ULTIMO" | psql "$URL_PRUEBA" --set ON_ERROR_STOP=on --quiet --output=/dev/null

RESTAURADO="$(conteos "$URL_PRUEBA")"
echo "Restaurado:  $RESTAURADO"
echo ""

if [ "$ORIGEN" = "$RESTAURADO" ]; then
  echo "CORRECTO: el respaldo restaura la misma cantidad de filas en todas las tablas."
  echo "(usuarios, mesas, carta, órdenes, ítems, pagos, zonas)"
  exit 0
fi

echo "FALLO: los conteos no coinciden." >&2
echo "El respaldo puede estar incompleto o haberse tomado a mitad de una escritura." >&2
exit 1
