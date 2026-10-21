#!/usr/bin/env bash
# Preparación del VPS (docs/DEPLOY-SETUP.md §3, tarea 018e). Idempotente: se puede volver a correr.
# Uso, desde la laptop:
#   scp deploy/vps-setup.sh ubuntu@<IP>:~ && ssh -t ubuntu@<IP> 'sudo bash ~/vps-setup.sh'
# Antes de cerrar SSH comprueba que deploy tenga sus dos llaves; la sesión abierta no se corta.
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
[ "$(id -u)" -eq 0 ] || { echo "Correr con sudo"; exit 1; }

PERSONAL_KEY='ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIP53wg4skfaKCRHwWxl99RdZcqnGojWfzIPMcBSqQc5A fiallosedisonreggae@gmail.com'
DEPLOY_KEY='ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIMFwQCwiWnkGaicVG2sgazKuwiyNxSv0U+oLkYpqyw5x github-actions-deploy'

step() { echo; echo "==> $*"; }

step "1. Sistema y hora"
apt-get update -q
apt-get -y -q -o Dpkg::Options::="--force-confold" full-upgrade
timedatectl set-timezone UTC

step "2. Usuario deploy"
id deploy >/dev/null 2>&1 || adduser --disabled-password --gecos "" deploy
usermod -aG sudo deploy
install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
touch /home/deploy/.ssh/authorized_keys
for key in "$PERSONAL_KEY" "$DEPLOY_KEY"; do
  grep -qxF "$key" /home/deploy/.ssh/authorized_keys || echo "$key" >> /home/deploy/.ssh/authorized_keys
done
chown deploy:deploy /home/deploy/.ssh/authorized_keys && chmod 600 /home/deploy/.ssh/authorized_keys

step "3. Firewall, fail2ban y actualizaciones automáticas"
apt-get install -y -q ufw fail2ban unattended-upgrades
ufw default deny incoming
ufw default allow outgoing
ufw allow OpenSSH && ufw allow 80/tcp && ufw allow 443/tcp && ufw allow 443/udp
ufw --force enable
systemctl enable --now fail2ban
cat > /etc/apt/apt.conf.d/20auto-upgrades <<'EOF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
EOF
cat > /etc/apt/apt.conf.d/52bellidental <<'EOF'
Unattended-Upgrade::Automatic-Reboot "true";
Unattended-Upgrade::Automatic-Reboot-Time "08:00";
EOF

step "4. Swap de 2 GB"
if [ ! -f /swapfile ]; then
  fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile
fi
swapon --show=NAME --noheadings | grep -qx /swapfile || swapon /swapfile
grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab

step "5. Docker"
command -v docker >/dev/null || curl -fsSL https://get.docker.com | sh
usermod -aG docker deploy
install -d -o deploy -g deploy /opt/bellidental /opt/bellidental/backups

step "6. SSH solo con llave y sin root"
[ "$(grep -c '^ssh-ed25519' /home/deploy/.ssh/authorized_keys)" -ge 2 ] || { echo "deploy sin llaves: no cierro SSH"; exit 1; }
cat > /etc/ssh/sshd_config.d/00-bellidental.conf <<'EOF'
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
EOF
sshd -t && systemctl restart ssh

step "7. Comprobación"
fails=0
check() { if eval "$2" >/dev/null 2>&1; then echo "  OK    $1"; else echo "  FALLA $1"; fails=$((fails+1)); fi; }
check "zona horaria UTC"              '[ "$(timedatectl show -p Timezone --value)" = UTC ]'
check "deploy en sudo y docker"       'id -nG deploy | grep -qw sudo && id -nG deploy | grep -qw docker'
check "deploy con 2 llaves"           '[ "$(grep -c ^ssh-ed25519 /home/deploy/.ssh/authorized_keys)" -ge 2 ]'
check "ufw activo (22, 80, 443)"      'ufw status | grep -q "Status: active" && ufw status | grep -q "443/udp"'
check "fail2ban activo"               'systemctl is-active fail2ban'
check "unattended-upgrades activo"    'grep -q "Unattended-Upgrade \"1\"" /etc/apt/apt.conf.d/20auto-upgrades'
check "swap 2 GB"                     'swapon --show=NAME --noheadings | grep -qx /swapfile && grep -q "^/swapfile " /etc/fstab'
check "docker y compose"              'docker version && docker compose version'
check "/opt/bellidental de deploy"    '[ "$(stat -c %U /opt/bellidental/backups)" = deploy ]'
check "sshd: sin root ni contraseña"  'sshd_effective=$(sshd -T) && grep -qx "permitrootlogin no" <<<"$sshd_effective" && grep -qx "passwordauthentication no" <<<"$sshd_effective"'
echo
docker --version; docker compose version; swapon --show; ufw status
[ -f /var/run/reboot-required ] && echo "AVISO: reinicio pendiente → sudo reboot"
[ "$fails" -eq 0 ] && echo "TODO OK" || { echo "$fails comprobaciones fallaron"; exit 1; }
