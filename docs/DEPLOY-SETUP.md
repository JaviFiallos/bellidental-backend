# Despliegue en Contabo — guía y tareas del desarrollador

Lo que **no** automatiza el código: cuentas, DNS, servidor, secretos. Arquitectura: `IMPLEMENTATION-PLAN.md §1` y
§4.12. Tareas de código: backend 018a (imagen, compose, Caddy, CI/CD), frontend 022, agente 018b, backend 018c
(respaldos) y 018d (exportación).

## 0. Resumen

| Pieza | Elección |
|---|---|
| Servidor | Contabo Cloud VPS, 6 GB RAM, **Ubuntu 24.04 LTS**, región EE. UU. (East) |
| Registro de imágenes | GitHub Container Registry: `ghcr.io/javifiallos/bellidental-{backend,frontend,agent}` |
| Etiquetas | `latest` (lo que se despliega) y `sha-<7>` (para volver atrás) |
| CI/CD | GitHub Actions en cada repo: test → imagen → deploy por SSH (solo en `main`) |
| En el VPS | `/opt/bellidental/` con `docker-compose.yml`, `Caddyfile`, `.env` (secretos), `backups/` |
| Puertos públicos | 22 (SSH con llave), 80 y 443 (solo Caddy) |
| Dominios | `app.<dominio>` → sistema · `bot.<dominio>/webhook` → agente |

Orden: §1 → §2 → §3 → §4 → tareas 022 y 018b (suben sus imágenes) → 018a (sube el compose y despliega) → §6 → §7.

**Estado (2026-10-21):** §1–§6 hechos; `https://app.bellidentalv1.site` en línea (db, backend, frontend, caddy).
Configuración real del servidor: `docs/tasks/018e-preparacion-vps.md`. Falta: agente (018b, `bot.` responde 502),
deploy automático del frontend (022b), §7 Meta, respaldos (018c).

| Dato real | Valor |
|---|---|
| Dominio | `bellidentalv1.site` (Cloudflare, "DNS only") |
| VPS | Contabo Cloud VPS 4, `vmi3682955`, región **EU**, IP `169.58.232.65` |
| Usuario inicial | `ubuntu` (Contabo no deja entrar como `root`) |
| Environment de GitHub | `production` en cada repo (no otro nombre: el workflow lo busca así) |

## 1. Dominio y DNS
1. Comprar el dominio (a nombre de la clínica al implantar, D-17). `.com` en Cloudflare Registrar o Porkbun.
2. Registros DNS (cuando tengas la IP del VPS, §2):
   | Tipo | Nombre | Valor | TTL |
   |---|---|---|---|
   | A | `app` | IP del VPS | 300 |
   | A | `bot` | IP del VPS | 300 |
   Si el DNS está en Cloudflare: **"DNS only" (nube gris)**; Caddy necesita recibir el tráfico para sacar el
   certificado.
3. Comprobar: `dig +short app.<dominio>` devuelve la IP.

## 2. Contabo
1. Pedir el VPS: imagen **Ubuntu 24.04**, región EE. UU., y pegar tu llave SSH pública
   (`~/.ssh/id_ed25519.pub`) si el formulario lo permite. Anotar la IP.
2. Si el plan incluye snapshots: tomar uno al terminar §3 ("servidor base").

## 3. Preparar el servidor (una vez)
Automatizado en `deploy/vps-setup.sh` (idempotente, comprueba todo al final):
`scp deploy/vps-setup.sh ubuntu@<IP>:~ && ssh -t ubuntu@<IP> 'sudo bash ~/vps-setup.sh'`; luego `sudo reboot` si
avisa. Los comandos de abajo son la referencia de lo que hace. Diferencia: SSH se cierra con
`/etc/ssh/sshd_config.d/00-bellidental.conf`, no editando `sshd_config` (el `50-cloud-init.conf` de Contabo trae
`PasswordAuthentication yes` y sshd usa el primer valor que lee).
```bash
apt update && apt full-upgrade -y
timedatectl set-timezone UTC                     # el servidor en UTC; la app usa America/Guayaquil

# usuario de trabajo
adduser --disabled-password --gecos "" deploy
mkdir -p /home/deploy/.ssh && cp /root/.ssh/authorized_keys /home/deploy/.ssh/
chown -R deploy:deploy /home/deploy/.ssh && chmod 700 /home/deploy/.ssh && chmod 600 /home/deploy/.ssh/authorized_keys
usermod -aG sudo deploy                          # y darle contraseña: passwd deploy

# SSH solo con llave y sin root
sed -i 's/^#\?PermitRootLogin.*/PermitRootLogin no/; s/^#\?PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
systemctl restart ssh                            # ANTES, probar en otra terminal: ssh deploy@IP

# firewall, fail2ban, actualizaciones automáticas
apt install -y ufw fail2ban unattended-upgrades
ufw default deny incoming && ufw allow OpenSSH && ufw allow 80/tcp && ufw allow 443/tcp && ufw allow 443/udp
ufw enable
dpkg-reconfigure -plow unattended-upgrades       # "Yes"
# reinicio automático de madrugada si una actualización lo pide (los contenedores vuelven solos)
echo 'Unattended-Upgrade::Automatic-Reboot "true";
Unattended-Upgrade::Automatic-Reboot-Time "08:00";' > /etc/apt/apt.conf.d/52bellidental   # 08:00 UTC = 03:00 Ecuador

# swap de 2 GB
fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab

# Docker (repositorio oficial)
curl -fsSL https://get.docker.com | sh
usermod -aG docker deploy                        # ojo: grupo docker = equivalente a root
mkdir -p /opt/bellidental/backups && chown -R deploy:deploy /opt/bellidental
```
Nota: Docker se salta `ufw` en los puertos que publica. Por eso **solo Caddy** publica puertos; Postgres, backend y
agente nunca llevan `ports:` en producción.

## 4. GitHub
1. **Repo del agente**: `bellidental-agent` aún no tiene remoto. Crear `JaviFiallos/bellidental-agent` (privado),
   `git remote add origin git@github.com:JaviFiallos/bellidental-agent.git` y `git push -u origin main`.
2. **Llave de despliegue** (en tu laptop, sin passphrase, solo para CI):
   ```bash
   ssh-keygen -t ed25519 -f ~/.ssh/bellidental_deploy -C "github-actions-deploy" -N ""
   ssh-copy-id -i ~/.ssh/bellidental_deploy.pub deploy@<IP>
   ssh-keyscan -H <IP>          # salida → secreto VPS_KNOWN_HOSTS
   ```
3. En **cada uno** de los 3 repos: Settings → Environments → `production` (exactamente ese nombre) →
   los cuatro `VPS_*` en **Environment secrets** y `APP_DOMAIN` en **Environment variables** (si falta,
   la comprobación final hace `curl https:///` y falla):
   | Nombre | Tipo | Valor |
   |---|---|---|
   | `VPS_HOST` | secret | IP del VPS |
   | `VPS_USER` | secret | `deploy` |
   | `VPS_SSH_KEY` | secret | contenido de `~/.ssh/bellidental_deploy` (privada) |
   | `VPS_KNOWN_HOSTS` | secret | salida de `ssh-keyscan` |
   | `APP_DOMAIN` | variable | `app.<dominio>` (para la comprobación final) |
4. **Acceso del VPS a GHCR** (paquetes privados): crear un *Personal access token (classic)* solo con
   `read:packages`, y en el VPS como `deploy`:
   `echo <TOKEN> | docker login ghcr.io -u javifiallos --password-stdin`.
   (Alternativa: marcar los 3 paquetes como públicos; no contienen secretos.) **Hecho así:** backend y frontend
   son públicos, el VPS no necesita `docker login`.

## 5. `.env` del VPS (`/opt/bellidental/.env`, `chmod 600`)
Plantilla: `deploy/.env.example` (tarea 018a). Generar secretos:
```bash
openssl rand -hex 48      # JWT_SECRET
openssl rand -hex 32      # BOT_API_KEY (el mismo valor lo usan backend y agente)
openssl rand -hex 24      # POSTGRES_PASSWORD (hex: sin / + = que molesten en URLs o shell)
```
Generarlos **en el VPS** (`$(openssl rand ...)` dentro del heredoc que escribe `.env`) para que no salgan del
servidor. `POSTGRES_PASSWORD` solo cuenta en el primer arranque de `db`; cambiarlo después exige `ALTER USER`.
WhatsApp (`WA_*`): de la app de Meta de producción (`WHATSAPP-SETUP.md`). LLM (`LLM_*`): opcional; sin clave el
bot usa solo el menú. Nunca subir este archivo a git; guardar una copia en el gestor de contraseñas.

## 6. Primer despliegue y comprobación
1. Hacer merge a `main` en frontend y agente → sus pipelines suben las imágenes.
2. Merge a `main` del backend → su pipeline copia `docker-compose.yml` y `Caddyfile` a `/opt/bellidental`,
   hace `pull backend` y levanta `db backend frontend caddy` (el agente lo levanta su pipeline, 018b).
   El backend **no** vuelve a bajar el frontend: una imagen nueva del frontend se despliega con su propio job
   (022b) o a mano: `docker compose pull frontend && docker compose up -d frontend`.
   (Manual, si hace falta: `scp deploy/docker-compose.yml deploy/Caddyfile deploy@<IP>:/opt/bellidental/` y en el
   VPS `docker compose up -d`.)
3. En el VPS: `docker compose ps` → todo `healthy`; `docker compose logs caddy` → certificados emitidos.
4. Abrir `https://app.<dominio>`, entrar con cada perfil, generar una receta PDF.
5. **Cambiar las contraseñas de `director`, `odontologa` y `secretaria`** (la semilla V4 usa la misma de
   desarrollo). Si no hay pantalla para hacerlo:
   `htpasswd -bnBC 12 "" 'NuevaClave' | tr -d ':\n'` y
   `docker compose exec db psql -U bellidental -c "UPDATE users SET password_hash='<hash>' WHERE username='director';"`
6. Comprobar que `https://app.<dominio>/api/v1/bot/info` responde 404 desde internet.

## 7. Meta (webhook de producción)
App de Meta → WhatsApp → Configuración → Webhook: URL `https://bot.<dominio>/webhook`, token de verificación =
`WA_VERIFY_TOKEN`, suscribir el campo `messages`. Probar escribiendo al número del bot.

## 8. Operación diaria
- Ver estado: `docker compose ps` · logs: `docker compose logs -f --tail=100 backend`.
- **Volver a una versión anterior**: en `.env` poner `BACKEND_TAG=sha-abc1234` (o `FRONTEND_TAG`, `AGENT_TAG`) y
  `docker compose up -d backend`. Cuidado: Flyway no deshace migraciones; si la versión nueva migró la BD, restaurar
  el respaldo previo al despliegue (`backups/predeploy-*.dump`, tarea 018a) antes de bajar de versión.
- Respaldos, restauración y rotación de secretos: `docs/OPERATIONS.md` (tarea 018c).

## 9. Pendientes antes de la clínica real
- Datos reales de la clínica (nombre, dirección, teléfonos, WhatsApp de recepción) en `application.yml`.
- Pasar dominio, VPS, Meta, proveedor de IA y Drive a cuentas de la clínica (D-17).
