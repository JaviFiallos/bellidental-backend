# Respaldo diario de la BD (tarea 018c). Misma base que el servicio db: el pg_dump coincide con el servidor.
FROM postgres:16.15-trixie

# supercronic v0.2.51 (linux/amd64). SHA1 oficial publicado en la página de releases de aptible/supercronic.
ENV SUPERCRONIC_VERSION=v0.2.51 \
    SUPERCRONIC_SHA1SUM=1692066e350a88ff4d7d6474fe3c7a6c41ec5e89 \
    SUPERCRONIC=supercronic-linux-amd64

RUN apt-get update \
 && apt-get install -y --no-install-recommends rclone tzdata ca-certificates curl \
 && rm -rf /var/lib/apt/lists/* \
 && curl -fsSLO "https://github.com/aptible/supercronic/releases/download/${SUPERCRONIC_VERSION}/${SUPERCRONIC}" \
 && echo "${SUPERCRONIC_SHA1SUM}  ${SUPERCRONIC}" | sha1sum -c - \
 && chmod +x "${SUPERCRONIC}" \
 && mv "${SUPERCRONIC}" /usr/local/bin/supercronic

COPY backup.sh /usr/local/bin/backup.sh
COPY crontab /etc/supercronic/crontab
RUN chmod 755 /usr/local/bin/backup.sh

# Corre como root a propósito: ./backups del VPS es de deploy y el uid 999 de postgres no puede escribir ahí.
CMD ["supercronic", "/etc/supercronic/crontab"]
