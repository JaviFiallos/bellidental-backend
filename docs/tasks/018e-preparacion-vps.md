# 018e — Preparación del VPS de producción

## Objetivo
Dejar el VPS listo para que el pipeline de 018a despliegue: usuario `deploy`, firewall, SSH solo con llave,
actualizaciones automáticas, swap y Docker. Es una tarea de operación (sin código Java ni tests Maven); la ejecuta
el desarrollador con `deploy/vps-setup.sh` y queda como registro de la configuración real del servidor.
Plan §4.12 y `docs/DEPLOY-SETUP.md` §1–§3.

## Datos del servidor
| Dato | Valor |
|---|---|
| Proveedor / plan | Contabo, Cloud VPS 4 (2026), instancia `vmi3682955`, región EU |
| Sistema | Ubuntu 24.04.5 LTS (kernel 6.8), imagen estándar sin panel |
| IP | `169.58.232.65` (IPv6 `2a02:c207:2368:2955::1`, sin registro AAAA) |
| Disco | 96 GB |
| Usuario inicial | `ubuntu` (sudo sin contraseña; Contabo no permite entrar como `root`) |
| Usuario de trabajo | `deploy` (grupos `sudo`, `docker`; contraseña de sudo en el gestor de contraseñas) |
| Huella SSH del host | `SHA256:0nvem213n0Bgak5yO9i6opmb8vFm+Ob1slWiAwAdxr4` (ED25519) |

DNS (Cloudflare, "DNS only", TTL Auto):
| Tipo | Nombre | Valor |
|---|---|---|
| A | `app.bellidentalv1.site` | `169.58.232.65` |
| A | `bot.bellidentalv1.site` | `169.58.232.65` |

Llaves SSH (en la laptop del desarrollador; las privadas nunca se suben a git):
| Llave | Uso |
|---|---|
| `~/.ssh/id_ed25519` | Acceso personal (`ssh deploy@169.58.232.65`) |
| `~/.ssh/bellidental_deploy` | Solo GitHub Actions (secreto `VPS_SSH_KEY`); sin passphrase |

## Archivos a tocar
```
deploy/
  vps-setup.sh        script idempotente de preparación (pasos 1–7)
docs/tasks/
  018e-preparacion-vps.md
```

## Criterios de aceptación
1. Zona horaria del servidor en UTC (la app usa `America/Guayaquil` internamente).
2. Usuario `deploy` en los grupos `sudo` y `docker`, con las dos llaves públicas en `authorized_keys`.
3. `ufw` activo: entrada denegada salvo 22/tcp, 80/tcp, 443/tcp y 443/udp (HTTP/3 de Caddy).
4. `fail2ban` activo (jail `sshd` por defecto).
5. `unattended-upgrades` activo, con reinicio automático a las 08:00 UTC (03:00 Ecuador) si hace falta.
6. Swap de 2 GB en `/swapfile`, persistente en `/etc/fstab`.
7. Docker Engine y el plugin `docker compose` (repositorio oficial vía `get.docker.com`).
8. `/opt/bellidental` y `/opt/bellidental/backups` con dueño `deploy`.
9. SSH sin root ni contraseñas: `/etc/ssh/sshd_config.d/00-bellidental.conf` (el `00-` gana sobre
   `50-cloud-init.conf` de Contabo, que trae `PasswordAuthentication yes`; en sshd vale el primer valor leído).
10. El paso 7 del script imprime `TODO OK`, y desde la laptop entran ambas llaves como `deploy`.

## Tests requeridos
Unit: ninguno. Integration: ninguno (sin código de la app).

Verificación manual, desde la laptop:
```bash
scp deploy/vps-setup.sh ubuntu@169.58.232.65:~ && ssh -t ubuntu@169.58.232.65 'sudo bash ~/vps-setup.sh'
ssh deploy@169.58.232.65 'whoami && docker ps'                                          # llave personal
ssh -i ~/.ssh/bellidental_deploy -o IdentitiesOnly=yes deploy@169.58.232.65 whoami     # llave de CI
ssh -o PubkeyAuthentication=no deploy@169.58.232.65                                     # debe rechazar: "Permission denied (publickey)"
ssh-keyscan -H 169.58.232.65                                                            # → secreto VPS_KNOWN_HOSTS
```
Si el script avisa de reinicio pendiente: `ssh deploy@169.58.232.65 sudo reboot` y repetir las dos primeras
comprobaciones.

## Resultado
2026-10-21. Pasos 0–2 a mano; pasos 3–7 con `deploy/vps-setup.sh`.
- Comprobación del script: 9 OK, 1 FALLA (`sshd: sin root ni contraseña`). Falso negativo: con `pipefail`,
  `sshd -T | grep -q` da SIGPIPE. `sshd -T` real: `permitrootlogin no`, `passwordauthentication no`,
  `kbdinteractiveauthentication no`. Corregido en el script (lee `sshd -T` en una variable).
- Versiones: Docker 29.9.2, Docker Compose v5.6.0. Swap `/swapfile` 2G. `ufw` activo con 22, 80/tcp, 443/tcp,
  443/udp (v4 y v6).
- Desde la laptop: llave personal y llave de CI entran como `deploy`; `docker ps` sin sudo funciona;
  `deploy` y `root` sin llave → `Permission denied (publickey)`.
- `ssh-keyscan -H 169.58.232.65` guardado para el secreto `VPS_KNOWN_HOSTS`.
- El script avisó de reinicio pendiente (actualización de kernel).

## Revisión
Estado: APROBADO. Criterios 1–10 cumplidos; el único fallo era del chequeo, no del servidor. Pendiente operativo:
`sudo reboot` (no bloquea) y los secretos de GitHub (DEPLOY-SETUP §4.3–4.4).
