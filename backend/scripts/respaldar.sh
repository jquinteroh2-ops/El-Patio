#!/usr/bin/env bash
#
# Respaldo de la base de El Patio.
#
# Saca un volcado comprimido y con fecha. Se pensó para correrlo desde el cron
# de Railway una vez al día, pero funciona igual desde cualquier máquina que
# alcance la base.
#
# Advertencia que conviene leer una sola vez y no olvidar: un respaldo guardado
# en el mismo proveedor que la base NO es un respaldo. Si se pierde la cuenta,
# se pierden los dos. Por eso el script deja el archivo en disco y, si están
# definidas las variables de un destino externo, lo sube. Los respaldos que
# Railway hace por su cuenta cubren el fallo del disco, no la pérdida de la
# cuenta ni un borrado hecho desde adentro.
#
#   ./respaldar.sh
#   DATABASE_URL=postgresql://... DESTINO=/ruta/respaldos ./respaldar.sh

set -euo pipefail

DESTINO="${DESTINO:-./respaldos}"
# `pg_dump` no entiende el prefijo jdbc: que usa Spring, así que se le quita.
URL="${DATABASE_URL:-postgresql://elpatio:elpatio@localhost:5433/elpatio}"
URL="${URL#jdbc:}"

# Nombre con fecha y hora en UTC: ordena alfabéticamente igual que en el tiempo.
SELLO="$(date -u +%Y%m%dT%H%M%SZ)"
ARCHIVO="${DESTINO}/elpatio-${SELLO}.sql.gz"

mkdir -p "$DESTINO"

echo "Respaldando la base…"

# --no-owner y --no-privileges: el volcado tiene que poder restaurarse en una
# base cuyo usuario se llama distinto, que es justo lo que pasa al pasar de
# Railway a una máquina local para revisar algo.
pg_dump "$URL" \
  --no-owner \
  --no-privileges \
  --clean \
  --if-exists \
  | gzip -9 > "$ARCHIVO"

TAMANO="$(du -h "$ARCHIVO" | cut -f1)"
echo "Listo: $ARCHIVO ($TAMANO)"

# Un archivo de cero bytes es un respaldo que no existe, y el peor momento para
# descubrirlo es cuando hace falta restaurarlo.
if [ ! -s "$ARCHIVO" ]; then
  echo "ERROR: el respaldo salió vacío" >&2
  exit 1
fi

# Comprobación barata de que el gzip no quedó truncado.
if ! gzip -t "$ARCHIVO"; then
  echo "ERROR: el archivo comprimido está dañado" >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# Copia fuera del proveedor
# ---------------------------------------------------------------------------

# Si hay un destino externo configurado, se sube. Sirve cualquier almacén
# compatible con S3: Cloudflare R2, Backblaze B2, el de AWS.
if [ -n "${RESPALDO_S3_BUCKET:-}" ]; then
  if command -v aws >/dev/null 2>&1; then
    echo "Subiendo a ${RESPALDO_S3_BUCKET}…"
    aws s3 cp "$ARCHIVO" "s3://${RESPALDO_S3_BUCKET}/$(basename "$ARCHIVO")"
    echo "Subido."
  else
    echo "AVISO: RESPALDO_S3_BUCKET está definido pero no hay cliente aws" >&2
  fi
fi

# ---------------------------------------------------------------------------
# Rotación
# ---------------------------------------------------------------------------

# Se conservan los últimos DIAS_RETENCION días. Sin esto el disco se llena y el
# respaldo del día que hace falta es justo el que no cupo.
DIAS_RETENCION="${DIAS_RETENCION:-30}"
find "$DESTINO" -name 'elpatio-*.sql.gz' -type f -mtime "+${DIAS_RETENCION}" -delete
echo "Se conservan los respaldos de los últimos ${DIAS_RETENCION} días."
