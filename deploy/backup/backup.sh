#!/usr/bin/env bash
# Respaldo diario de la BD (tarea 018c). Lo ejecuta supercronic a las 20:30 o a mano:
#   docker compose exec backup backup.sh
# Sale con 1 si algo falla; deja siempre una línea final en stdout.
set -euo pipefail
umask 077

readonly BACKUP_ROOT=/backups
readonly DAILY_DIR="${BACKUP_ROOT}/daily"
readonly DB_HOST=db
readonly DB_USER=bellidental
readonly DB_NAME=bellidental
readonly RETENTION_DAYS=30
readonly REMOTE="${BACKUP_RCLONE_REMOTE:-}"

timestamp="$(date +%Y%m%d-%H%M)"
dump_name="bellidental-${timestamp}.dump"
dump_path="${DAILY_DIR}/${dump_name}"
tmp_path="${dump_path}.tmp"
drive_status="OMITIDO"

# Línea final: fecha, archivo, tamaño, drive=..., OK|ERROR
summary() {
  local status="$1" size="-" file
  file="$dump_path"
  [ -f "$dump_path" ] || file="$tmp_path"
  [ -f "$file" ] && size="$(du -h "$file" | cut -f1)"
  printf '%s %s %s drive=%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S %Z')" "$dump_name" "$size" "$drive_status" "$status"
}

fail() {
  echo "ERROR: $*" >&2
  summary ERROR
  exit 1
}

mkdir -p "$DAILY_DIR"

# 3. Volcado a .tmp; si pg_dump falla, se borra el .tmp.
if ! PGPASSWORD="${POSTGRES_PASSWORD:?POSTGRES_PASSWORD no definido}" \
    pg_dump -h "$DB_HOST" -U "$DB_USER" -Fc "$DB_NAME" > "$tmp_path"; then
  rm -f "$tmp_path"
  fail "pg_dump ha fallado"
fi

# 4. Verificación: el índice del dump debe tener entradas.
if ! listing="$(pg_restore --list "$tmp_path")" || [ -z "$listing" ]; then
  fail "el dump no se puede leer con pg_restore --list (se conserva ${tmp_path})"
fi
mv "$tmp_path" "$dump_path"

# 3b. Dueño de los archivos = dueño de /backups (deploy en el VPS).
owner="$(stat -c %u:%g "$BACKUP_ROOT")"
chown "$owner" "$DAILY_DIR" "$dump_path"

# 5. Copia a Drive (remoto crypt: rclone cifra).
if [ -z "$REMOTE" ]; then
  drive_status="OMITIDO"
elif rclone copy "$dump_path" "$REMOTE"; then
  drive_status="OK"
else
  drive_status="ERROR"
  fail "la copia a Drive ha fallado (el dump local se conserva)"
fi

# 6. Retención, solo tras un dump verificado: local y, si la copia fue correcta, remota.
find "$DAILY_DIR" -maxdepth 1 -type f -name 'bellidental-*.dump' -mtime +"$RETENTION_DAYS" -delete
if [ -n "$REMOTE" ]; then
  rclone delete --min-age "${RETENTION_DAYS}d" --include 'bellidental-*.dump' "$REMOTE" \
    || fail "no se pudo aplicar la retención en Drive"
fi

# 7. Línea final.
summary OK
