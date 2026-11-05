# 018c — Respaldos automáticos, restauración y guía de operación

## Objetivo
Respaldos diarios de la BD según D-18 y la guía para operar el sistema en el VPS.
- `pg_dump -Fc` diario a las **20:30 hora de Ecuador**, retención 30 días en el VPS.
- La misma copia, cifrada, en el Google Drive de la clínica (`rclone` con un remoto `crypt`).
- `restore.sh` documentado (sin prueba de restauración en el MVP).
- Solo esas dos copias. Sin alertas: un fallo se ve en `docker compose logs backup` (recomendación de la tesis).

Plan §1 y §4.12. Requiere 018a (compose de producción) y 018e (VPS). Pasos manuales de rclone: §"Prueba manual".
Drive puede configurarse después del despliegue: sin remoto, el respaldo local funciona igual (criterio 5).

## Archivos a tocar
```
deploy/
  backup/
    Dockerfile                (nuevo) postgres:16.15-trixie (la misma de db) + rclone + supercronic
    crontab                   (nuevo) 30 20 * * * /usr/local/bin/backup.sh
    backup.sh                 (nuevo)
  restore.sh                  (nuevo) se ejecuta en el VPS, fuera de contenedores
  docker-compose.yml          + servicio backup
  .env.example                + BACKUP_RCLONE_REMOTE, BACKUP_TAG
.github/workflows/ci-cd.yml   + imagen bellidental-backup; scp de restore.sh; pull/up del servicio backup
docs/OPERATIONS.md            (nuevo)
```
No tocar nada más (en particular, los cambios sin commit de la tarea 032 en `identity/` no son de esta tarea).

## Criterios de aceptación

**Imagen `backup`**
1. Basada en la **misma** imagen de `db` (`postgres:16.15-trixie`: mismo `pg_dump` que el servidor). Instala `rclone`
   (paquete de Debian o binario oficial con versión fijada), `tzdata` si la imagen no lo trae, y `supercronic`
   (binario con versión fijada y su checksum **oficial** de la página de releases, comprobado con `sha1sum -c` en el
   build: si el checksum no coincide, el build falla). `CMD` = supercronic con el crontab, en primer plano.
   **Sin `USER postgres`**: corre como root porque `./backups` del VPS es de `deploy` (`vps-setup.sh`) y el uid 999
   de `postgres` no puede escribir ahí (ver 3b).
2. `TZ=America/Guayaquil` en el servicio para que `30 20 * * *` sea hora local (`date` dentro del contenedor → `-05`).

**`backup.sh`** (`set -euo pipefail`, en `/usr/local/bin`, ejecutable)
3. `pg_dump -h db -U bellidental -Fc bellidental` a `/backups/daily/bellidental-<YYYYMMDD-HHMM>.dump`
   (contraseña por `PGPASSWORD` desde `POSTGRES_PASSWORD`). Escribe primero a `.tmp` y renombra al terminar;
   si `pg_dump` falla, borra el `.tmp`.
3b. `umask 077` al inicio (los dumps tienen datos clínicos). Tras escribir, `chown` de `/backups/daily` y del dump
   al dueño de `/backups` (`stat -c %u:%g /backups`), para que `deploy` pueda leerlos y usar `restore.sh`.
4. Comprueba el archivo con `pg_restore --list` (> 0 líneas); si falla, línea ERROR, código ≠ 0 y no borra nada.
5. Copia a Drive:
   - `BACKUP_RCLONE_REMOTE` vacío → no llama a rclone; la línea final dice `drive=OMITIDO` y sale con 0.
   - Si no, `rclone copy` del dump a `${BACKUP_RCLONE_REMOTE}` (un remoto `crypt` sobre Drive: el cifrado lo hace
     rclone). Si falla → línea ERROR, código ≠ 0; el dump local se conserva.
6. Retención, solo después de un dump verificado: borra los `bellidental-*.dump` de `/backups/daily/` con más de
   30 días; con remoto y copia correcta, `rclone delete --min-age 30d` en el remoto.
7. Cada ejecución deja **una** línea final en stdout: fecha, archivo, tamaño, `drive=OK|OMITIDO|ERROR`, `OK|ERROR`.
   Nunca imprime la contraseña.

**Compose** (`deploy/docker-compose.yml`)
8. Servicio `backup`: imagen `ghcr.io/javifiallos/bellidental-backup:${BACKUP_TAG:-latest}`, `restart:
   unless-stopped`, `mem_limit: 256m`, `logging: *default-logging`, sin puertos, `depends_on: db`
   (`service_healthy`), `TZ: America/Guayaquil`, volúmenes `./backups:/backups` y **la carpeta**
   `./secrets/rclone:/config/rclone` (no un archivo suelto ni `:ro`: rclone reescribe el `rclone.conf` al renovar el
   token de Drive con un archivo temporal + renombrado, y Docker crea un directorio si el archivo montado no existe);
   `RCLONE_CONFIG=/config/rclone/rclone.conf`. Variables: `POSTGRES_PASSWORD`, `BACKUP_RCLONE_REMOTE`
   (`${BACKUP_RCLONE_REMOTE:-}`).
9. Los `predeploy-*.dump` de 018a viven en `backups/` (raíz) y no los toca la retención diaria (`backups/daily/`).

**CI/CD** (`.github/workflows/ci-cd.yml`)
10. En el job `image`: segundo `metadata-action` + `build-push-action` para `ghcr.io/javifiallos/bellidental-backup`
    con contexto `deploy/backup`, mismas etiquetas (`latest`, `sha-`) y caché con `scope` propio (para no pisar la
    del backend).
11. En `deploy`: el `scp` copia también `deploy/restore.sh`; luego `chmod +x restore.sh` en el VPS.
    `docker compose pull backend backup` y `up -d --remove-orphans db backend frontend caddy backup`.

**`restore.sh <archivo.dump>`** (en `/opt/bellidental`, `set -euo pipefail`)
12. Comprueba que el archivo existe; pide confirmación escribiendo `RESTAURAR`; detiene `backend`, `agent` y
    `backup`; `pg_restore --clean --if-exists --no-owner -U bellidental -d bellidental` dentro del contenedor `db`
    (el dump por stdin con `exec -T`); vuelve a levantar `backend`, `agent` y `backup`.
13. Admite un dump traído de Drive (`docker compose run --rm backup rclone copy "$BACKUP_RCLONE_REMOTE<archivo>"
    /backups/`) — documentado en OPERATIONS.

**`docs/OPERATIONS.md`** (breve, comandos copiables)
14. Secciones: estado y logs · desplegar y volver atrás (`*_TAG`) · respaldos (dónde están, cómo forzar uno:
    `docker compose exec backup backup.sh`, cómo listar los de Drive) · configurar Drive (resumen de §"Prueba
    manual") · restaurar (desde el VPS y desde Drive) · exportar (018d) · rotar secretos (`JWT_SECRET` cierra todas
    las sesiones; `BOT_API_KEY` en backend y agente a la vez; token de WhatsApp) · renovar el token de GHCR · qué
    hacer si el disco se llena. Remite a `DEPLOY-SETUP.md` para la instalación.

## Tests requeridos
Ninguno (scripts de infraestructura). En Resultado se anotan, con la salida real resumida:
1. `shellcheck` de `backup.sh` y `restore.sh` sin avisos (si no está instalado:
   `docker run --rm -v "$PWD:/mnt" koalaman/shellcheck:stable deploy/backup/backup.sh deploy/restore.sh`).
2. `docker build -t bellidental-backup:local deploy/backup` correcto (incluye la comprobación del checksum).
3. Ejecución local, sin GHCR ni Drive: red Docker propia, contenedor `postgres:16.15-trixie` con alias `db` y una
   tabla con una fila, y la imagen local con `/backups` en una carpeta temporal:
   a. con `BACKUP_RCLONE_REMOTE=prueba:/backups/remote` y `RCLONE_CONFIG_PRUEBA_TYPE=local` → dump creado,
      verificado y copiado a `remote/`; línea final `drive=OK ... OK`;
   b. con `BACKUP_RCLONE_REMOTE` vacío → dump creado, `drive=OMITIDO`, código 0;
   c. retención: un `bellidental-old.dump` con `touch -d '40 days ago'` se borra y un `predeploy-x.dump` en la raíz
      no se toca;
   d. `date` en el contenedor con `TZ=America/Guayaquil` muestra `-05`.
   Al terminar, borrar los contenedores, la red y la carpeta temporal.
4. `docker compose -f deploy/docker-compose.yml config -q` sin errores (con un `.env` de prueba temporal si hace
   falta; no dejarlo en el repo).
No se corre `./mvnw` en esta tarea salvo en el paso de commit.

## Prueba manual (desarrollador)
1. En la laptop: `rclone config` → remoto `gdrive` (Google Drive, scope `drive.file`, cuenta de la clínica;
   `client_id` vacío = el de rclone. Si se usa un client_id propio, publicar la app OAuth en **Producción**: en
   modo *Testing* el token caduca a los 7 días y la copia a Drive deja de funcionar sin avisar) →
   remoto `bellidental-crypt` tipo `crypt` sobre `gdrive:bellidental-backups` con contraseña y salt **guardadas en
   el gestor de contraseñas** (sin ellas los respaldos no se pueden leer).
2. En el VPS: `install -d -m 700 /opt/bellidental/secrets/rclone`, copiar ahí `~/.config/rclone/rclone.conf`
   (`chmod 600`) y poner `BACKUP_RCLONE_REMOTE=bellidental-crypt:` en `.env`; `docker compose up -d backup`.
3. `docker compose exec backup backup.sh` → dump en `backups/daily/` y archivo cifrado en Drive (`drive=OK`).
4. Al día siguiente: `docker compose logs backup` muestra la ejecución de las 20:30.

## Resultado

**Archivos creados**
- `deploy/backup/Dockerfile`: base `postgres:16.15-trixie`; `rclone`, `tzdata`, `ca-certificates`, `curl` por apt;
  supercronic v0.2.51 (amd64) con `sha1sum -c` contra `1692066e350a88ff4d7d6474fe3c7a6c41ec5e89`, que es el SHA1
  oficial publicado en la descripción del release v0.2.51 de aptible/supercronic (comprobado: "La suma coincide").
- `deploy/backup/crontab` (`30 20 * * * /usr/local/bin/backup.sh`) y `deploy/backup/backup.sh` (ejecutable).
- `deploy/restore.sh` (ejecutable), `docs/OPERATIONS.md`.

**Archivos modificados**
- `deploy/docker-compose.yml`: servicio `backup` (imagen, TZ, `POSTGRES_PASSWORD`, `BACKUP_RCLONE_REMOTE`,
  `RCLONE_CONFIG`, `./backups:/backups`, `./secrets/rclone:/config/rclone`, `depends_on: db` healthy).
- `deploy/.env.example`: `BACKUP_TAG`, `BACKUP_RCLONE_REMOTE`.
- `.github/workflows/ci-cd.yml`: job `image` con `bellidental-backup` (contexto `deploy/backup`, `scope=backup`);
  `deploy` copia `restore.sh`, hace `chmod +x`, `pull backend backup` y `up -d ... caddy backup`.

**Verificaciones**
1. `shellcheck` (imagen `koalaman/shellcheck:stable`; no instalado en el host): `backup.sh` y `restore.sh` sin avisos, exit 0.
2. `docker build -t bellidental-backup:local deploy/backup`: correcto, incluido el `sha1sum -c` del checksum.
   Herramientas dentro: rclone v1.60.1, supercronic v0.2.51, pg_dump/pg_restore 16.15.
3. Ejecución local (red `bd018c-net`, BD `postgres:16.15-trixie` con alias `db`, tabla `prueba_018c` con 1 fila):
   a. remoto `prueba:/backups/remote` (`RCLONE_CONFIG_PRUEBA_TYPE=local`): dump en `daily/`, copia en `remote/`
      idéntica (`cmp`), `pg_restore --list` con la tabla. Línea final:
      `2026-10-27 02:18:25 -05 bellidental-20261027-0218.dump 4.0K drive=OK OK`, exit 0.
   b. `BACKUP_RCLONE_REMOTE` vacío: `... 4.0K drive=OMITIDO OK`, exit 0, sin carpeta `remote`.
   c. `bellidental-old.dump` (40 días, local y remoto) borrado en ambos sitios; `predeploy-x.dump` en la raíz intacto.
      Línea final `drive=OK OK`, exit 0.
   d. `date` con `TZ=America/Guayaquil`: `Tue Oct 27 02:18:40 AM -05 2026`.
   Limpieza: contenedores, red, imagen `bellidental-backup:local` y carpeta de pruebas eliminados.
4. `docker compose -f deploy/docker-compose.yml --env-file deploy/.env.example config -q`: exit 0; servicios
   `db frontend backend agent backup caddy`. No se creó ningún `.env`.

No se corrió `./mvnw`. No se probó `restore.sh` contra una BD (no está en la lista de tests); la YAML de
`ci-cd.yml` solo se validó como YAML, no con una ejecución real de GitHub Actions.

**Problemas y supuestos**
- Un fallo de verificación (`pg_restore --list`) deja el `.tmp` en `daily/` para inspección (coherente con "no borra nada").
  Un fallo de `pg_dump` sí borra el `.tmp`.
- Si la copia a Drive falla, el script sale con ERROR antes de la retención ese día; el dump local se conserva.
  Si falla la retención remota, también sale con ERROR.
- Línea de error: `ERROR: ...` en stderr más la línea final en stdout (`... ERROR`, exit 1); `docker compose logs backup` las muestra ambas.
- `restore.sh`: si `pg_restore` falla, `backend`, `agent` y `backup` quedan detenidos; la guía lo indica.
- rclone viene de apt (Debian trixie, v1.60.1 en esta build), no con versión fijada exacta; la base sí está fijada.
- La imagen solo se construye para `linux/amd64` (el SHA1 es del binario amd64, y CI solo compila amd64).
- En las pruebas, rclone creó la carpeta `remote/` como root con modo 700 (comportamiento de rclone local, no afecta a Drive).
- Pendiente de la tarea 018e/VPS: `secrets/rclone/` y `.env` con `BACKUP_RCLONE_REMOTE` se configuran a mano según OPERATIONS.

## Revisión
Estado: APROBADO

- Imagen `backup`: base `postgres:16.15-trixie`, sin `USER postgres`, supercronic v0.2.51 con `sha1sum -c`; SHA1
  `1692066e…5e89` comprobado de forma independiente contra el body del release v0.2.51 (API de GitHub).
- `backup.sh`: los fallos de `pg_dump`, verificación y rclone van dentro de `if`/`||`, así que dejan `ERROR` +
  línea final y exit 1; `.tmp` borrado si falla `pg_dump`; `umask 077` + `chown` al dueño de `/backups`; retención
  local solo `daily/bellidental-*.dump` y remota solo tras copia correcta; la contraseña no se imprime.
- Compose: carpeta `./secrets/rclone` sin `:ro`, `RCLONE_CONFIG`, `TZ`, `depends_on` healthy, logging, restart y
  `BACKUP_RCLONE_REMOTE:-` correctos. CI: caché `scope=backup` (la del backend queda igual), scp + `chmod` de
  `restore.sh`, `pull`/`up` con `backup`; el resto del workflow no cambia.
- `restore.sh` cumple el criterio 12. 018c no toca los archivos de la tarea 032.
- Notas menores (sin corrección): si falta `POSTGRES_PASSWORD` o fallan `mkdir`/`chown`/`find`, el script muere por
  `set -e` sin línea final (el error sale igual en los logs); la imagen hereda el `ENTRYPOINT` y el
  `VOLUME /var/lib/postgresql/data` de postgres (un volumen anónimo vacío, inofensivo); `-mtime +30` borra a partir
  de 31 días; rclone de apt sin versión exacta (la base sí está fijada).

Correcciones mínimas del revisor: `docs/OPERATIONS.md` — el título de "Configurar Google Drive" remitía a una
sección "Prueba manual" de `DEPLOY-SETUP.md` que no existe; se quitó esa referencia y se añadió el aviso del token OAuth
en modo *Testing* (7 días), que venía en la Prueba manual de la tarea.

**Prueba manual (2026-11-05, VPS):** `backup.sh` → `bellidental-20261105-0314.dump 272K drive=OMITIDO OK` sin
remoto; con `BACKUP_RCLONE_REMOTE=bellidental-crypt:` → `bellidental-20261105-1301.dump 272K drive=OK OK` y
`rclone ls` lista el dump en Drive (cifrado en `gdrive:bellidental-backups`). Pendiente: `client_id` propio de Google
(ver `docs/OPERATIONS.md` §Configurar Google Drive).
