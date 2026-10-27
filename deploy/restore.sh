#!/usr/bin/env bash
# Restaura la BD desde un dump de pg_dump -Fc (tarea 018c). Se ejecuta en el VPS, en /opt/bellidental:
#   ./restore.sh backups/daily/bellidental-YYYYMMDD-HHMM.dump
# Si pg_restore falla, los servicios quedan detenidos: revisar y levantar con
#   docker compose up -d backend agent backup
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "Uso: $0 <archivo.dump>" >&2
  exit 1
fi
dump="$1"

[ -f docker-compose.yml ] || { echo "Ejecutar desde /opt/bellidental" >&2; exit 1; }
[ -f "$dump" ] || { echo "No existe: $dump" >&2; exit 1; }

echo "Esto BORRA y reemplaza la base de datos bellidental con $dump."
read -r -p "Escribe RESTAURAR para continuar: " answer
[ "$answer" = "RESTAURAR" ] || { echo "Cancelado"; exit 1; }

docker compose stop backend agent backup
docker compose exec -T db pg_restore --clean --if-exists --no-owner -U bellidental -d bellidental < "$dump"
docker compose up -d backend agent backup
echo "Restauración terminada."
