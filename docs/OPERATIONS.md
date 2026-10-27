# Operación del sistema en el VPS

Instalación desde cero: `DEPLOY-SETUP.md`. Todos los comandos se ejecutan como `deploy` en `/opt/bellidental`.

## Estado y logs
```bash
docker compose ps
docker compose logs --tail=100 backend
docker compose logs --tail=50 backup        # última ejecución de los respaldos
docker compose logs -f agent
```

## Desplegar y volver atrás
Cada push a `main` despliega solo (GitHub Actions). Para volver a una versión anterior, usar la etiqueta
`sha-<7>` de esa versión en `.env` y levantar solo ese servicio:
```bash
nano .env                                   # p. ej. BACKEND_TAG=sha-1a2b3c4
docker compose pull backend && docker compose up -d backend
```
Etiquetas: `BACKEND_TAG`, `FRONTEND_TAG`, `AGENT_TAG`, `BACKUP_TAG`. Antes de cada despliegue se guarda
`backups/predeploy-<sha>.dump` (se conservan los 5 últimos).

## Respaldos
- Copia diaria a las **20:30 (Ecuador)**: `backups/daily/bellidental-<YYYYMMDD-HHMM>.dump`, 30 días en el VPS.
- Copia cifrada en Google Drive (`BACKUP_RCLONE_REMOTE`), 30 días. Si la variable está vacía, solo hay copia local.
- Los `backups/predeploy-*.dump` no los borra la retención diaria.

Forzar una copia ahora:
```bash
docker compose exec backup backup.sh
```
Listar las copias de Drive:
```bash
docker compose exec backup sh -c 'rclone ls "$BACKUP_RCLONE_REMOTE"'
```

## Configurar Google Drive
1. En la laptop: `rclone config` → remoto `gdrive` (Google Drive, scope `drive.file`, cuenta de la clínica) y remoto
   `bellidental-crypt` tipo `crypt` sobre `gdrive:bellidental-backups`. La contraseña y el salt van al gestor de
   contraseñas: sin ellos los respaldos no se pueden leer. Con un `client_id` propio, publicar la app OAuth en
   Producción (en *Testing* el token caduca a los 7 días y la copia a Drive deja de funcionar).
2. En el VPS:
   ```bash
   install -d -m 700 /opt/bellidental/secrets/rclone
   cp ~/.config/rclone/rclone.conf /opt/bellidental/secrets/rclone/rclone.conf   # copiar desde la laptop
   chmod 600 /opt/bellidental/secrets/rclone/rclone.conf
   ```
3. En `.env`: `BACKUP_RCLONE_REMOTE=bellidental-crypt:` y después `docker compose up -d backup`.
4. Comprobar: `docker compose exec backup backup.sh` → la última línea debe terminar en `drive=OK OK`.

## Restaurar
Desde una copia local (parar `backend`, `agent` y `backup`, restaurar y levantarlos de nuevo):
```bash
cd /opt/bellidental
./restore.sh backups/daily/bellidental-<YYYYMMDD-HHMM>.dump    # escribir RESTAURAR para confirmar
```
Desde Drive: traer el dump a `backups/` y restaurar con el mismo script:
```bash
docker compose run --rm backup sh -c 'rclone copy "$BACKUP_RCLONE_REMOTE"bellidental-<YYYYMMDD-HHMM>.dump /backups/'
./restore.sh backups/bellidental-<YYYYMMDD-HHMM>.dump
```
Si `restore.sh` falla, los servicios quedan detenidos: revisar el error y ejecutar `docker compose up -d backend agent backup`.

## Exportar
Exportación de datos para la clínica: tarea 018d (pendiente).

## Rotar secretos
Cambiar el valor en `.env` y levantar los servicios que lo usan:
- `JWT_SECRET`: `docker compose up -d backend`. Cierra todas las sesiones de los usuarios.
- `BOT_API_KEY`: debe cambiar **a la vez** en backend y agente: `docker compose up -d backend agent`.
- Token de WhatsApp (`WA_ACCESS_TOKEN`): `docker compose up -d agent`.
- `POSTGRES_PASSWORD`: no se cambia solo en `.env`; hay que cambiarla también dentro de PostgreSQL (`ALTER USER`)
  antes de levantar los servicios.

## Renovar el acceso a GHCR
Solo si las imágenes son privadas. Si `docker compose pull` responde `unauthorized`, crear un token de GitHub con
`read:packages` y ejecutar:
```bash
echo <TOKEN> | docker login ghcr.io -u javifiallos --password-stdin
```

## Si el disco se llena
```bash
df -h /
docker system df
docker image prune -f                        # imágenes que ya no usa ningún servicio
du -sh backups/* | sort -h                   # tamaño de los respaldos
```
Borrar primero los `backups/predeploy-*.dump` más antiguos. No borrar `backups/daily/` sin una copia en Drive.
