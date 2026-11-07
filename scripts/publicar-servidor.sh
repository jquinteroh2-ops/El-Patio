#!/usr/bin/env bash
# Escribe la direccion del servidor en servidor.json de la rama gh-pages
# (GitHub Pages) sin tocar la carpeta de trabajo. Uso: publicar-servidor.sh <url>
set -e
cd "$(dirname "$0")/.."
git fetch -q origin gh-pages
indice="$(mktemp -u)"
export GIT_INDEX_FILE="$indice"
git read-tree origin/gh-pages
blob=$(printf '{ "api": "%s" }' "$1" | git hash-object -w --stdin)
git update-index --add --cacheinfo "100644,$blob,servidor.json"
arbol=$(git write-tree)
unset GIT_INDEX_FILE; rm -f "$indice"
commit=$(git commit-tree "$arbol" -p origin/gh-pages -m "Direccion del servidor: $1")
git push -q origin "$commit:refs/heads/gh-pages"
echo "Publicado: $1"
