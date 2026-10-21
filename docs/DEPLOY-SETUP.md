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

## 3. Preparar el servidor (una vez, como root)
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
3. En **cada uno** de los 3 repos: Settings → Environments → `production` → secretos y variables:
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
   (Alternativa: marcar los 3 paquetes como públicos; no contienen secretos.)

## 5. `.env` del VPS (`/opt/bellidental/.env`, `chmod 600`)
Plantilla: `deploy/.env.example` (tarea 018a). Generar secretos:
```bash
openssl rand -base64 48   # JWT_SECRET
openssl rand -hex 32      # BOT_API_KEY (el mismo valor lo usan backend y agente)
openssl rand -base64 24   # POSTGRES_PASSWORD
```
WhatsApp (`WA_*`): de la app de Meta de producción (`WHATSAPP-SETUP.md`). LLM (`LLM_*`): opcional; sin clave el
bot usa solo el menú. Nunca subir este archivo a git; guardar una copia en el gestor de contraseñas.

## 6. Primer despliegue y comprobación
1. Hacer merge a `main` en frontend y agente → sus pipelines suben las imágenes.
2. Merge a `main` del backend → su pipeline copia `deploy/` a `/opt/bellidental` y levanta todo.
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
