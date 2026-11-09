# 036 — Datos demo para el manual de usuario

## Objetivo
Dejar la base **local** con datos limpios y verosímiles para tomar las capturas del manual (tarea 037): profesionales
con nombre real de clínica, horarios semanales, pacientes y algunas citas del día. Se hace con un script que llama a
la API (sin SQL manual), repetible sobre una base recién creada. No toca código de la aplicación.

Contexto: la base local ya se reinició (`docker compose down -v && docker compose up -d`); al arrancar el backend,
Flyway la recrea con las semillas de V4 (usuarios `director`, `odontologa`, `secretaria`, contraseña `Cambiar.2026!`).
Hoy es domingo 2026-11-08; la clínica atiende 08:00–12:00 y 14:00–20:00 (`docs/CONTEXT.md §1`). Todos los datos son
ficticios (nombres inventados, cédulas que solo pasan el dígito verificador).

## Archivos a tocar
```
docs/manual/
  seed-demo.py        nuevo: Python 3 (solo stdlib) contra http://localhost:8080/api/v1 (jq no está instalado)
  README.md           nuevo: cómo levantar backend y frontend en local y correr el script (10–15 líneas)
```

## Criterios de aceptación
1. El script inicia sesión (`POST /auth/login`) como `director` y, donde el permiso lo exija (horarios, pacientes),
   como `secretaria`. Falla con mensaje claro si el backend no responde o una llamada devuelve error (sale con código distinto de 0).
2. Profesionales (`PUT /professionals/{id}` sobre los dos de V4, `POST /professionals` para el tercero):
   - `55555555-…` (director) → **Dr. Andrés Villacís**, Odontología general, color azul `#1E88E5`.
   - `66666666-…` (odontóloga) → **Dra. Carolina Mena**, Odontología general, color verde `#43A047`.
   - Nuevo: **Dra. Paula Ortiz**, Endodoncia, color naranja; con acceso (`POST /professionals/{id}/access`,
     `endodoncista@bellidental.local`, contraseña `Cambiar.2026!`).
   - Cédulas válidas (dígito verificador correcto) y números de registro MSP con formato verosímil.
3. Horarios semanales (`POST /professionals/{id}/schedules`, `weekday` ISO 1 = lunes):
   - Dr. Villacís: lunes a viernes 08:00–12:00 y 14:00–20:00.
   - Dra. Ortiz: martes 08:00–12:00 y 14:00–18:00 (especialista de días fijos).
   - **Dra. Mena: sin horario.** Su horario del lunes (y el turno por fecha de hoy) los crea la secretaria en el
     manual, para mostrar esa pantalla.
4. Si la fecha de la demo (`DEMO_DATE`, por defecto hoy) no cae en el horario semanal del Dr. Villacís, el script le
   crea un turno **por fecha** ese día 08:00–20:00, para que la agenda del día tenga citas.
5. Pacientes (como `secretaria`, `POST /patients`): 4 adultos con nombre, cédula válida, fecha de nacimiento y
   teléfono E.164 sin "+" (`5939…`). Uno con `admin_notes` ("Presupuesto de ortodoncia entregado").
   No se registran antecedentes (eso lo hace el odontólogo).
6. Citas en `DEMO_DATE` para el Dr. Villacís (como `secretaria`, `POST /appointments`): 3 citas futuras respecto a la
   hora de ejecución, en huecos libres (usar `/availability`). Ninguna para la Dra. Mena (las crea el manual).
7. El script es idempotente en lo razonable: si un paciente con esa cédula ya existe, no lo duplica (buscar antes);
   al final imprime un resumen (profesionales, turnos, pacientes, citas creadas).
8. `README.md`: requisitos (Docker, JDK, Node, Python 3), orden de arranque (`docker compose up -d` → `./mvnw spring-boot:run`
   → `npm start` en `../bellidental-frontend` → `python3 docs/manual/seed-demo.py`), URL `http://localhost:4200` y los
   cuatro usuarios demo.

## Tests requeridos
- Unit: ninguno.
- Integration: ninguno (no cambia la aplicación). Verificación: correr el script sobre la base recién creada y pegar
  en Resultado su resumen final, más la salida de
  `docker exec bellidental_db psql -U bellidental -d bellidental -c "select count(*) from patients"` (y lo mismo para
  `professionals`, `professional_schedules`, `appointments`). No correr `./mvnw clean verify`.

## Resultado
Ejecutada por Claude a pedido del usuario (2026-11-08), no por Gemini.

- Creados: `docs/manual/seed-demo.py` (stdlib, sin jq) y `docs/manual/README.md`.
- Base local reiniciada (`docker compose down -v`); el backend que ya corría en :8080 aplicó Flyway sobre ella.
- Salida de la primera corrida: `Demo 2026-11-08: profesionales 3, turnos 13, pacientes 4, citas 3`.
- Segunda corrida: duplicaba citas. Se añadió `has_appointments` (no crea citas si ya hay PENDING/CONFIRMED del
  Dr. Villacís ese día); las 2 citas sobrantes se cancelaron y luego se borraron por SQL (datos demo locales).
  Tercera corrida: `profesionales 2, turnos 0, pacientes 0, citas 0` (los 2 son las actualizaciones por PUT).
- Conteos finales: patients 4 · professionals 3 · professional_schedules 13 · appointments 3 (16:30, 18:00 y 19:30
  de hoy con el Dr. Villacís). Login de `endodoncista@bellidental.local` probado.
- Supuesto: hoy es domingo, así que el Dr. Villacís tiene un turno por fecha 2026-11-08 08:00–20:00. La Dra. Mena
  queda sin horario (lo crea el manual, tarea 037).

## Revisión
Estado: APROBADO
