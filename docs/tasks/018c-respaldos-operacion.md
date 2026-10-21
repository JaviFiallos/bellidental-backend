# 018c — Respaldos automáticos, restauración y guía de operación

## Objetivo
Respaldos diarios de la BD según D-18 y la guía para operar el sistema en el VPS.
- `pg_dump -Fc` diario a las **20:30 hora de Ecuador**, retención 30 días en el VPS.
- La misma copia, cifrada, en el Google Drive de la clínica (`rclone` con un remoto `crypt`).
- `restore.sh` documentado (sin prueba de restauración en el MVP).

Plan §1 y §4.12. Requiere 018a (compose de producción). Pasos manuales de rclone: §"Prueba manual".

## Archivos a tocar
```
deploy/
  backup/
    Dockerfile                (nuevo) postgres:16.<minor>-trixie (la misma de db) + rclone + supercronic
    crontab                   (nuevo) 30 20 * * * /usr/local/bin/backup.sh
    backup.sh                 (nuevo)
  restore.sh                  (nuevo) se ejecuta en el VPS, fuera de contenedores
  docker-compose.yml          + servicio backup
  .env.example                + BACKUP_RCLONE_REMOTE
.github/workflows/ci-cd.yml   + construir y publicar ghcr.io/javifiallos/bellidental-backup; scp de restore.sh
docs/OPERATIONS.md            (nuevo)
```

## Criterios de aceptación

**Imagen `backup`**
1. Basada en la **misma** imagen de `db` (mismo `pg_dump` que el servidor). Instala `rclone` (paquete de Debian o
   binario oficial con versión fijada) y `supercronic` (binario con versión y checksum fijados) para cron sin root.
2. `TZ=America/Guayaquil` en el servicio para que `30 20 * * *` sea hora local.

**`backup.sh`** (`set -euo pipefail`)
3. `pg_dump -h db -U bellidental -Fc bellidental > /backups/daily/bellidental-<YYYYMMDD-HHMM>.dump`
   (contraseña por `PGPASSWORD` desde `POSTGRES_PASSWORD`). Escribe primero a `.tmp` y renombra al terminar.
4. Comprueba el archivo con `pg_restore --list` (> 0 líneas); si falla, sale con código ≠ 0 y no borra nada.
5. `rclone copy` del dump al remoto `${BACKUP_RCLONE_REMOTE}` (un remoto `crypt` sobre Drive: el cifrado lo hace
   rclone).
6. Retención: borra los dumps locales con más de 30 días y `rclone delete --min-age 30d` en el remoto.
7. Cada ejecución deja una línea en stdout (`docker compose logs backup`): fecha, tamaño, OK/ERROR.

**Compose**
8. Servicio `backup`: imagen `ghcr.io/javifiallos/bellidental-backup:${BACKUP_TAG:-latest}`, `mem_limit: 256m`,
   sin puertos, `depends_on: db` (`service_healthy`), volúmenes `./backups:/backups` y
   `./secrets/rclone.conf:/config/rclone/rclone.conf:ro`; `RCLONE_CONFIG=/config/rclone/rclone.conf`.
   Variables: `POSTGRES_PASSWORD`, `BACKUP_RCLONE_REMOTE`.
9. Los `predeploy-*.dump` de 018a viven en `backups/` (raíz) y no los toca la retención diaria (`backups/daily/`).

**`restore.sh <archivo.dump>`**
10. Pide confirmación escribiendo `RESTAURAR`; detiene `backend` y `agent`; `pg_restore --clean --if-exists
    --no-owner -d bellidental` dentro del contenedor `db`; vuelve a levantar `backend` y `agent`.
11. Admite un dump traído de Drive (`rclone copy <remoto>:<archivo> ./backups/`) — documentado en OPERATIONS.

**`docs/OPERATIONS.md`** (breve, comandos copiables)
12. Secciones: estado y logs · desplegar y volver atrás (`*_TAG`) · respaldos (dónde están, cómo forzar uno:
    `docker compose exec backup backup.sh`) · restaurar (desde el VPS y desde Drive) · exportar (018d) ·
    rotar secretos (`JWT_SECRET` cierra todas las sesiones; `BOT_API_KEY` en backend y agente a la vez; token de
    WhatsApp) · renovar el token de GHCR · qué hacer si el disco se llena. Remite a `DEPLOY-SETUP.md` para la
    instalación.

## Tests requeridos
Ninguno (scripts de infraestructura). Gemini anota en Resultado: `shellcheck` de los scripts, y una ejecución local
con el compose de 018a y un remoto rclone `local` (carpeta) en lugar de Drive: dump creado, verificado y copiado;
retención probada con un archivo de fecha antigua (`touch -d '40 days ago'`).

## Prueba manual (desarrollador)
1. En la laptop: `rclone config` → remoto `gdrive` (Google Drive, scope `drive.file`, cuenta de la clínica) →
   remoto `bellidental-crypt` tipo `crypt` sobre `gdrive:bellidental-backups` con contraseña y salt **guardadas en
   el gestor de contraseñas** (sin ellas los respaldos no se pueden leer).
2. Copiar `~/.config/rclone/rclone.conf` a `/opt/bellidental/secrets/rclone.conf` (`chmod 600`) y poner
   `BACKUP_RCLONE_REMOTE=bellidental-crypt:` en `.env`.
3. `docker compose exec backup backup.sh` → dump en `backups/daily/` y archivo cifrado en Drive.
4. Al día siguiente: `docker compose logs backup` muestra la ejecución de las 20:30.

## Resultado

## Revisión
