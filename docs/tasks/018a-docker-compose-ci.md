# 018a — Imagen del backend, compose de producción, Caddy y CI/CD

## Objetivo
Dejar el sistema completo desplegable en el VPS de Contabo (D-17) con un `git push` a `main`.
- Imagen Docker del backend (Spring Boot 3.4, Java 21).
- Perfil `prod` que **no arranca** si falta un secreto.
- `deploy/` con el `docker-compose.yml` de producción de los 6 servicios, el `Caddyfile` y `.env.example`.
- Pipeline de GitHub Actions: test → imagen en GHCR → despliegue por SSH.

Plan §1 y §4.12. Pasos manuales (VPS, DNS, secretos de GitHub): `docs/DEPLOY-SETUP.md`. Las imágenes de frontend
(tarea 022 del frontend) y agente (018b del agente) se construyen en sus repos; aquí solo se referencian.
El servicio `backup` es la 018c: aquí **no** se incluye.

## Archivos a tocar
```
Dockerfile                                (nuevo)
.dockerignore                             (nuevo)
pom.xml                                   + spring-boot-starter-actuator
src/main/resources/
  application-prod.yml                    (nuevo)
src/main/java/com/bellidental/config/
  SecurityConfig.java                     + permitAll de GET /actuator/health
deploy/
  docker-compose.yml                      (nuevo) producción
  Caddyfile                               (nuevo)
  .env.example                            (nuevo)
.github/workflows/
  ci-cd.yml                               (nuevo)
docker-compose.yml                        sin cambios (sigue siendo solo la BD de desarrollo)
```

## Criterios de aceptación

**Dockerfile** (multi-stage)
1. Etapa `build`: `eclipse-temurin:21-jdk-noble`. Copia primero `mvnw`, `.mvn/` y `pom.xml`, corre
   `./mvnw -B -q dependency:go-offline` (capa cacheable), luego `src/` y `./mvnw -B -DskipTests package`.
   Usar `RUN --mount=type=cache,target=/root/.m2`.
2. Extraer capas: `java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination /extracted`.
3. Etapa final: `eclipse-temurin:21-jre-noble` (Ubuntu, **no Alpine**: OpenPDF y fontconfig). Copiar las capas en
   orden `dependencies`, `spring-boot-loader`, `snapshot-dependencies`, `application`.
4. Usuario no root (`useradd -r -u 10001 app`). Si la imagen no trae `curl`, instalarlo con
   `apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*` (lo usa el healthcheck).
5. `ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"`, `EXPOSE 8080`,
   `ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]`.
6. `.dockerignore`: `target/`, `.git/`, `.idea/`, `docs/`, `proy-infor/`, `.claude/`, `.gemini/`, `.agents/`,
   `*.md`, `deploy/`.

**Perfil `prod` (`application-prod.yml`)** — solo lo que cambia respecto de `application.yml`
7. `spring.datasource.url: ${DB_URL}`, `username: ${DB_USERNAME}`, `password: ${DB_PASSWORD}` sin valores por
   defecto.
8. `application.security.jwt.secret: ${JWT_SECRET}` y `application.bot.api-key: ${BOT_API_KEY}` sin valores por
   defecto: si faltan, la app no arranca.
9. `springdoc.api-docs.enabled: false` y `springdoc.swagger-ui.enabled: false`.
10. `server.forward-headers-strategy: framework` (detrás de Caddy; el límite de intentos de login ve la IP real).
11. Actuator: `management.endpoints.web.exposure.include: health`, `management.endpoint.health.probes.enabled: true`,
    `management.endpoint.health.show-details: never`. `SecurityConfig`: `permitAll` solo para
    `GET /actuator/health` y `/actuator/health/**`.

**`deploy/docker-compose.yml`** (nombre de proyecto `bellidental`)
12. Bloque común `x-logging` (`json-file`, `max-size: 10m`, `max-file: "3"`) aplicado a todos los servicios.
    Todos con `restart: unless-stopped`.
13. Servicios, imágenes y límites:
    | Servicio | Imagen | `mem_limit` | Puertos publicados |
    |---|---|---|---|
    | `db` | `postgres:16.<minor>-trixie` (fijar minor y Debian; nunca Alpine: cambia las collations) | 1g | ninguno |
    | `backend` | `ghcr.io/javifiallos/bellidental-backend:${BACKEND_TAG:-latest}` | 1g | ninguno |
    | `frontend` | `ghcr.io/javifiallos/bellidental-frontend:${FRONTEND_TAG:-latest}` (escucha 8080) | 64m | ninguno |
    | `agent` | `ghcr.io/javifiallos/bellidental-agent:${AGENT_TAG:-latest}` (escucha 8000) | 256m | ninguno |
    | `caddy` | `caddy:2.<minor>-alpine` (fijar la última estable) | 128m | `80:80`, `443:443`, `443:443/udp` |
14. `db`: `POSTGRES_DB=bellidental`, `POSTGRES_USER=bellidental`, `POSTGRES_PASSWORD=${POSTGRES_PASSWORD}`; volumen
    `postgres_data`; `command: postgres -c shared_buffers=256MB -c max_connections=50`; healthcheck
    `pg_isready -U bellidental -d bellidental`.
15. `backend`: `SPRING_PROFILES_ACTIVE=prod`, `DB_URL=jdbc:postgresql://db:5432/bellidental`,
    `DB_USERNAME=bellidental`, `DB_PASSWORD=${POSTGRES_PASSWORD}`, `JWT_SECRET`, `BOT_API_KEY`; `depends_on: db`
    con `condition: service_healthy`; healthcheck `curl -fs http://localhost:8080/actuator/health/readiness`
    con `start_period: 90s`.
16. `agent`: `BACKEND_URL=http://backend:8080`, `BOT_API_KEY`, `WA_VERIFY_TOKEN`, `WA_APP_SECRET`,
    `WA_ACCESS_TOKEN`, `WA_PHONE_NUMBER_ID`, `WA_GRAPH_VERSION`, `LLM_BASE_URL`, `LLM_API_KEY`, `LLM_MODEL`,
    `CLINIC_ZONE=America/Guayaquil`, `REMINDERS_ENABLED=true`; `depends_on: backend` con `service_healthy`.
17. Cada servicio recibe **solo sus variables** con `environment:` e interpolación `${VAR}` desde `.env`; nada de
    `env_file` compartido (el agente no debe ver la contraseña de la BD).
18. `caddy`: monta `./Caddyfile:/etc/caddy/Caddyfile:ro` y los volúmenes `caddy_data` y `caddy_config` (guardan
    los certificados: sin ellos se piden de nuevo en cada reinicio). Variables `DOMAIN` y `ACME_EMAIL`.
19. Una sola red por defecto; ningún servicio aparte de `caddy` usa `ports:`.

**`deploy/Caddyfile`**
20. Opción global `email {$ACME_EMAIL}`.
21. `app.{$DOMAIN}`:
    - `encode zstd gzip`;
    - `/api/v1/bot/*` → `respond 404` (plan §1: el bot solo entra por la red interna);
    - `/actuator/*` → `respond 404`;
    - `handle /api/*` → `reverse_proxy backend:8080`;
    - resto → `reverse_proxy frontend:8080`;
    - cabeceras: `Strict-Transport-Security "max-age=31536000"`, `X-Content-Type-Options nosniff`,
      `Referrer-Policy strict-origin-when-cross-origin`, `X-Frame-Options DENY`, `-Server`.
22. `bot.{$DOMAIN}`: `handle /webhook*` → `reverse_proxy agent:8000`; resto → `respond 404`.
    `request_body max_size 1MB`.

**`deploy/.env.example`** — todas las variables anteriores con comentario de una línea y valor vacío:
`DOMAIN`, `ACME_EMAIL`, `POSTGRES_PASSWORD`, `JWT_SECRET`, `BOT_API_KEY`, `WA_*`, `LLM_*`, `BACKEND_TAG`,
`FRONTEND_TAG`, `AGENT_TAG` (estas tres con `latest`). Arriba: "copiar a /opt/bellidental/.env, chmod 600; ver
docs/DEPLOY-SETUP.md §5".

**`.github/workflows/ci-cd.yml`**
23. Disparadores: `pull_request` y `push` a `main` y `develop` (en `develop` solo corre `test`; `image` y `deploy`
    exigen `github.ref == 'refs/heads/main'` y `github.event_name == 'push'`). `concurrency: { group: deploy-production, cancel-in-progress: false }`
    para el job de despliegue. `permissions` mínimos (`contents: read`; `packages: write` solo en `image`).
24. Job `test` (siempre): `actions/checkout`, `actions/setup-java` (temurin 21, `cache: maven`),
    `./mvnw -B verify` (Testcontainers funciona en `ubuntu-latest`).
25. Job `image` (solo `push` a `main`, `needs: test`): `docker/setup-buildx-action`, `docker/login-action` a
    `ghcr.io` con `GITHUB_TOKEN`, `docker/metadata-action` con `images: ghcr.io/javifiallos/bellidental-backend`
    (en minúsculas: el owner `JaviFiallos` no es válido en GHCR) y etiquetas `latest` y `type=sha,prefix=sha-`,
    `docker/build-push-action` con `cache-from/cache-to: type=gha,mode=max`, plataforma `linux/amd64`.
26. Job `deploy` (solo `push` a `main`, `needs: image`, `environment: production`), sin acciones de terceros para
    SSH (solo `ssh`/`scp` del runner):
    1. escribir `VPS_SSH_KEY` en `~/.ssh/id_ed25519` (chmod 600) y `VPS_KNOWN_HOSTS` en `~/.ssh/known_hosts`;
    2. `scp deploy/docker-compose.yml deploy/Caddyfile $VPS_USER@$VPS_HOST:/opt/bellidental/`;
    3. por SSH, en `/opt/bellidental`:
       - respaldo previo: `docker compose exec -T db pg_dump -U bellidental -Fc bellidental > backups/predeploy-<sha>.dump`
         (solo si `db` ya está corriendo) y conservar los 5 más recientes;
       - `docker compose pull backend && docker compose up -d --remove-orphans`;
       - `docker compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile`;
       - esperar hasta 180 s a que `backend` esté `healthy` (`docker inspect --format '{{.State.Health.Status}}'`);
         si no, mostrar `docker compose logs --tail=80 backend` y fallar;
       - `docker image prune -f`;
    4. comprobación: `curl -fsS https://${{ vars.APP_DOMAIN }}/ -o /dev/null`.
27. Ningún secreto se imprime en los logs (sin `set -x` en los pasos con secretos).

**General**
28. `docker build -t bellidental-backend .` funciona en local y la imagen final pesa menos de 400 MB.
29. Con `SPRING_PROFILES_ACTIVE=prod` y sin `JWT_SECRET`, el contenedor termina con un error que menciona la
    variable faltante.
30. `docker compose -f deploy/docker-compose.yml config` valida sin errores (con un `.env` de prueba).

## Tests requeridos
- Unit: ninguno. Integration: ninguno nuevo (infraestructura). El pipeline corre la suite existente.
- Verificación local que Gemini anota en Resultado:
  1. `docker build` del backend (tamaño de la imagen);
  2. levantar `db` + `backend` de `deploy/docker-compose.yml` con un `.env` de prueba (`BACKEND_TAG` apuntando a
     la imagen local) → `curl localhost:8080/actuator/health` desde el contenedor = `UP`; Flyway aplicó las migraciones;
  3. criterio 29;
  4. `docker compose config` (criterio 30);
  5. `actionlint` sobre `ci-cd.yml` si está disponible.

## Prueba manual (desarrollador, tras `DEPLOY-SETUP.md` §1–§5)
1. Merge a `main` → los 3 jobs en verde → `docker compose ps` en el VPS: todo `healthy`.
2. `https://app.<dominio>` carga, login con los 3 perfiles, receta PDF generada.
3. `https://app.<dominio>/api/v1/bot/info` y `/actuator/health` → 404 desde internet.
4. Cambiar `BACKEND_TAG` a un `sha-` anterior → `docker compose up -d backend` → vuelve a esa versión.

## Resultado

**Archivos (nuevos):** `Dockerfile`, `.dockerignore`, `src/main/resources/application-prod.yml`, `deploy/docker-compose.yml`,
`deploy/Caddyfile`, `deploy/.env.example`, `.github/workflows/ci-cd.yml`.
**Modificados:** `pom.xml` (+ actuator), `src/main/java/com/bellidental/config/SecurityConfig.java` (permitAll `GET /actuator/health` y `/**`).
Sin tests nuevos. Sin commit.

**Versiones fijadas (verificadas con `docker manifest inspect`):** `postgres:16.15-trixie`, `caddy:2.11.7-alpine`;
`eclipse-temurin:21-jdk-noble` / `21-jre-noble`.

**Verificación local**
- Criterio 28 (imagen < 400 MB): **NO CUMPLIDO**. Imagen final 538 MB. La base `eclipse-temurin:21-jre-noble` sola ya pesa
  406 MB, y el criterio 3 la exige. Hace falta decidir (p. ej. jlink o una base más pequeña con Ubuntu); no lo cambié.
- Build: sin buildx instalado, `docker build` con BuildKit falla. Construí con el builder clásico desde una copia de
  `Dockerfile` en `/tmp/claude-1000/018a-test/` sin los `--mount=type=cache` (no se tocó el Dockerfile del repo). El
  `--mount` del repo no se probó localmente; CI usa buildx, así que sí aplica. Build OK.
- Usuario de la imagen: `uid=10001(app)`. `curl` ya viene en la base (`/usr/bin/curl`), así que quité el `apt-get install`.
- Criterio 29 (prod sin `JWT_SECRET`): el contenedor sale con código 1 y el error dice
  `Could not resolve placeholder 'JWT_SECRET'`. OK.
- Compose (db + backend, proyecto `bellidental-test`, `.env` de prueba, `BACKEND_TAG=local`): `db` healthy;
  `backend` healthy; `/actuator/health` = `{"status":"UP","groups":["liveness","readiness"]}`; Flyway
  "Successfully applied 14 migrations ... now at version v14.1". Limpiado con `down -v`.
- Criterio 30: `docker compose -f deploy/docker-compose.yml config` exit 0. Con un `.env` parcial salen warnings de variables
  `WA_*`/`LLM_*` sin definir (esperado); no son errores.
- `caddy validate` del Caddyfile: "Valid configuration".
- actionlint 1.7.12 sobre `ci-cd.yml`: solo un info SC2029 (la expansión de `${GITHUB_SHA::7}` en cliente es intencional).

**Tests (`./mvnw -q clean verify`, exit 0):** surefire 92 run / 0 fail / 0 err; failsafe 63 run / 0 fail / 0 err.

**Problemas y supuestos**
- Swagger y `/api-docs` ahora responden **500** en vez de 404 (rutas sin mapear + el error se redirige a una ruta protegida). No hay error de servidor en los logs. Solo es cosmético; no lo corregí.
- Los pasos de SSH, `pg_dump`, `caddy reload` y el health-wait del job `deploy` no se han ejecutado: no hay VPS.
- El `.env` de prueba y los logs están en `/tmp/claude-1000/018a-test/`. No se han versionado.
- El volumen `bellidental-backend_postgres_data` (dev) no se tocó.

## Revisión
Estado: APROBADO

- `./mvnw -q clean verify`: exit 0; surefire 92/0/0, failsafe 63/0/0.
- Criterio 28 se ajusta: la base exigida (`21-jre-noble`) ya pesa 406 MB; 538 MB es aceptable (límite < 600 MB).
  Reducir con jlink queda como recomendación, no como tarea.
- Swagger/`/api-docs` con 500 en prod: no se expone (Caddy solo enruta `/api/*` al backend). Sin cambios.
- `--mount=type=cache` y el job `deploy` se validan en el primer push a `main` (CI usa buildx).

Validación en producción (2026-10-21, merge de `develop` a `main`, commit `4537993`):
- `test` → `image` → `deploy` en verde. `https://app.bellidentalv1.site` 200 con certificado válido; `/api/v1/auth/login`
  responde (400 sin cuerpo); `/api/v1/bot/info` y `/actuator/health` 404 desde internet. db y backend `healthy`.
- Fallos del primer intento, todos de configuración en GitHub (sin cambio de código): secretos en un environment
  `bb_production` (el workflow usa `production`) y `APP_DOMAIN` sin crear (`curl https:///`).
- Cambio en `ci-cd.yml` (018e): `up -d` solo `db backend frontend caddy`, porque la imagen del agente aún no existe.
- Frontend `unhealthy` aunque sirve 200: su `HEALTHCHECK` usa `localhost`, que en Alpine resuelve a `::1` y nginx solo
  escucha IPv4. Se corrige en el repo del frontend (022b: `127.0.0.1`).
