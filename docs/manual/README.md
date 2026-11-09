# Manual de usuario: entorno local y datos demo

Requisitos: Docker, JDK 21, Node y Python 3 (sin librerías externas).

1. Base de datos: `docker compose up -d` (en `bellidental-backend`). Para empezar limpio:
   `docker compose down -v && docker compose up -d`.
2. Backend: `./mvnw spring-boot:run` (Flyway crea el esquema y las semillas; API en `http://localhost:8080`).
3. Frontend: `npm start` en `../bellidental-frontend` → `http://localhost:4200`.
4. Datos demo: `python3 docs/manual/seed-demo.py [AAAA-MM-DD]` (fecha de la demo; por defecto hoy). Se puede
   repetir: no duplica profesionales, turnos, pacientes ni citas.

Usuarios demo (contraseña `Cambiar.2026!`):

| Correo | Perfil | Profesional |
|---|---|---|
| `secretaria@bellidental.local` | Recepción | — |
| `odontologa@bellidental.local` | Consultorio | Dra. Carolina Mena (sin horario: lo crea el manual) |
| `director@bellidental.local` | Dirección | Dr. Andrés Villacís (lun–vie; turno por fecha si la demo cae en fin de semana) |
| `endodoncista@bellidental.local` | Consultorio | Dra. Paula Ortiz, Endodoncia (martes) |

Pacientes demo: María José Paredes, Luis Fernando Chicaiza, Gabriela Sánchez y Jorge Altamirano, con tres citas del
Dr. Villacís en la fecha de la demo. Todos los datos son ficticios.
