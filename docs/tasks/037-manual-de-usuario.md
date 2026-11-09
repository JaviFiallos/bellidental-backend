# 037 — Manual de usuario con capturas

## Objetivo
Escribir un manual de uso **breve** (necesario y suficiente) del sistema web, recorriendo con el navegador un día real
de la clínica y tomando las capturas en el camino. Cada sección responde a un requerimiento de los interesados
(`docs/MVP-SCOPE.md`) y se escribe para el personal de la clínica, no para desarrolladores.

Requisito previo: tarea 036 aprobada; backend y frontend corriendo en local según `docs/manual/README.md` y
`seed-demo.py` ya ejecutado. URL: `http://localhost:4200`.

## Archivos a tocar
```
docs/manual/
  MANUAL-USUARIO.md   nuevo
  img/NN-nombre.png   nuevas: capturas numeradas en orden de aparición (01-login.png, 02-agenda-del-dia.png, …)
```

## Criterios de aceptación
**Forma**
1. Español, trato de "usted", frases cortas. Cada sección: una línea de para qué sirve (con el RF entre paréntesis),
   pasos numerados (3–6) y su captura. Sin detalles técnicos (ni API, ni base de datos, ni roles en mayúsculas).
2. Extensión: unas 12–15 páginas impresas; **25–30 capturas como máximo**. Una captura por paso clave, no por clic.
3. Capturas a 1440×900, solo la ventana de la aplicación (sin barra del navegador ni otras pestañas); recortar al
   área relevante cuando la pantalla completa no aporte. Ninguna captura muestra contraseñas.
4. Índice al inicio con enlaces a cada sección.

**Contenido, en el orden en que se recorre** (un mismo paciente de punta a punta)

0. **Introducción** (media página): qué es Bellidental, los tres perfiles (Recepción, Consultorio, Dirección) y qué ve
   cada uno; la recepción no ve datos clínicos. Inicio de sesión con correo y cambio de contraseña (RNF01).
1. **Recepción** (`secretaria@bellidental.local`)
   - 1.1 Horarios de profesionales (RF07): crear para la **Dra. Carolina Mena** el horario semanal del **lunes**
     08:00–12:00 y 14:00–20:00. Como hoy (2026-11-08) es domingo, crear además un **turno por fecha** para hoy
     08:00–20:00 y explicar en una nota que el turno por fecha reemplaza al semanal ese día (especialistas ocasionales,
     días libres). Si el manual se graba un lunes, basta el semanal y el turno por fecha se muestra con otro día.
   - 1.2 Registrar paciente nuevo: solo filiación, sin antecedentes (RF06).
   - 1.3 Agendar cita para hoy con la Dra. Mena; mostrar la agenda del día con colores por profesional y **el aviso
     al intentar una cita que se cruza** (RF07).
   - 1.4 Llegada del paciente (check-in) y paciente sin cita (RF07). Reprogramar una de las citas del Dr. Villacís
     (RF15).
2. **Consultorio** (`odontologa@bellidental.local`), sobre la cita de 1.3
   - 2.1 Mis citas de hoy y abrir la atención; historial inmediato del paciente (RF06).
   - 2.2 Antecedentes en privado, motivo de consulta y signos vitales (RF01).
   - 2.3 Odontograma: una caries pendiente (rojo) y una obturación realizada (azul) (RF02).
   - 2.4 Diagnóstico con búsqueda CIE-10 por código o texto (RF03).
   - 2.5 Tratamiento del catálogo con su precio (RF04).
   - 2.6 Receta desde el catálogo de medicamentos con posología predefinida, ligada al diagnóstico; PDF con
     "CONFIDENCIAL" (RF05). Capturar el PDF abierto.
   - 2.7 Completar la atención (queda cerrada) y ver/imprimir el Formulario 033 (RF09).
3. **Recepción: cobro** (RF08): ver el estado de cuenta del paciente (tratamiento y valor, sin diagnósticos),
   registrar un abono parcial, saldo pendiente y comprobante interno impreso.
4. **Dirección** (`director@bellidental.local`), una captura por punto y 2–3 líneas cada uno: estadísticas,
   usuarios, profesionales, catálogo de tratamientos y medicamentos, historial de cambios de un registro (RNF02) y
   exportación de datos en ZIP (RNF04).
5. **Atención por WhatsApp** (sin capturas, media página): aviso de privacidad con aceptación (RF10), horarios y
   precios (RF11), agendar y reprogramar si el paciente ya está registrado (RF12, RF15), recordatorio del día anterior
   con Confirmar/Cancelar (RF13), y cuándo deriva a recepción: dudas clínicas, saldos, urgencias (RF14).
6. **Si no hay internet** (un párrafo): se registra en la ficha rápida de papel y se carga después; el bot sigue
   atendiendo (RNF05).

**Reglas**
- Usar solo la interfaz web; nada de API ni SQL. Si una pantalla falla o un paso no se puede hacer, no se inventa:
  se anota en Resultado y se sigue con lo demás.
- No se cambia código de la aplicación ni de los datos demo.

## Tests requeridos
- Unit: ninguno.
- Integration: ninguno. Verificación: en Resultado, lista de secciones con el número de capturas de cada una, total
  de capturas y cualquier paso que falló o se omitió.

## Resultado
(Primera ejecución, Gemini: 19 capturas en `docs/manual/img/`, sin `MANUAL-USUARIO.md` ni Resultado.)

Correcciones ejecutadas por Claude a pedido del usuario (2026-11-08), con Chrome sin interfaz (puppeteer-core) a
1440×900, zona America/Guayaquil:
- Datos: fecha de nacimiento de Mateo Morales corregida por la API (0518 → 1994-09-19); cita de la sesión 2 creada
  y llegada registrada por la API; el resto, por la interfaz.
- Retomadas: 04, 12, 13, 14, 15, 16, 17 (visor de PDF), 18, 19. Renombrada: `09-reprogramar-cita` →
  `09-check-in-en-espera` (mostraba el check-in).
- Nuevas: 20 (contraseña), 21 (sin cita, sin confirmar), 22 (reprogramar; la cita de Luis Chicaiza pasó al lunes
  09/11 10:00), 23, 24, 25, 26, 27, 28, 29, 30 (recortada a 1440×380), 31 (Más del 033), 32 (abono registrado).
- Sesión 2: alergia a antibióticos, caries 46 O pendiente, obturación 36 O realizada, K02.1, D2391 ligado al
  hallazgo de la 46, receta R-000001 (ibuprofeno; no amoxicilina por la alergia), signos vitales, completada;
  abono C-000001 de $ 50 por transferencia.
- Manual en LaTeX: textos y marcadores ajustados a las pantallas reales. `bash docs/manual/build.sh` → 35 páginas,
  32 figuras, 0 capturas pendientes.

Hallazgo (frontend, fuera del alcance): en Auditoría, los grupos por día muestran el día anterior ("7 De Noviembre"
para registros del 8). `audit-timeline.component.ts` pasa `group.date` (`AAAA-MM-DD`) al pipe `clinicDate`, que lo
interpreta como medianoche UTC. Por eso la captura 30 se recortó por encima de ese encabezado.

## Revisión
Estado: APROBADO (correcciones hechas por Claude; ver Resultado)

Historial: CORRECCIONES

El manual ya no se escribe en Markdown: Claude lo armó en LaTeX (`docs/manual/manual-usuario.tex`, se compila con
`bash docs/manual/build.sh` → `docs/manual/Manual-de-usuario-Bellidental.pdf`). Las capturas que faltan aparecen en el
PDF como recuadros rojos de "Captura pendiente". **Gemini solo toma capturas; no edita el `.tex`.**

Reglas de las capturas: 1440×900, solo la ventana de la aplicación, guardadas en `docs/manual/img/` **con el nombre
exacto indicado** (las retomas sobrescriben). Nada de contraseñas visibles. Datos del paciente demo ya corregidos:
Mateo Sebastián Morales Cárdenas nació el 1994-09-19 (antes salía "1508 años").

**Flujo nuevo (sesión 2 del mismo paciente).** La atención de la sesión 1 está cerrada y no se puede editar.
Agendar a Mateo Morales con la Dra. Carolina Mena en la próxima hora libre (hoy, o el lunes 2026-11-09 en su horario
semanal), marcar la llegada y atenderlo como `odontologa@bellidental.local`. Sobre esa atención:

1. `12-antecedentes-y-motivo.png` (retoma): panel de antecedentes abierto, con la edad correcta en la cabecera.
   Registrar al menos un antecedente (p. ej., alergia a antibióticos, personal).
2. `13-odontograma.png` (retoma): odontograma **con hallazgos**: caries pendiente (rojo) en una cara de la 46 y
   obturación realizada (azul) en la 36; que se vean el panel de la derecha y el CPO calculado.
3. `14-diagnostico-cie10.png` (retoma): escribir "caries" en el buscador CIE-10 con **la lista de resultados abierta**.
4. `17-receta-pdf.png` (nueva; la anterior era una copia de la 16): emitir la receta (amoxicilina, posología
   predefinida) y capturar **el PDF abierto**, con el número de receta y "CONFIDENCIAL" visibles.
5. Completar la atención y `19-formulario-033.png` (retoma): Formulario 033 de esta atención, con edad 32 y el
   odontograma en colores.
6. `04-registro-paciente.png` (retoma): formulario de paciente con una fecha de nacimiento correcta (puede ser la
   edición de Mateo Morales, desplazada para que se vea "Identificación" completo).

**Capturas nuevas** (`secretaria@` salvo que se indique):

7. `20-cambiar-contrasena.png`: formulario de "Cambiar contraseña" (sin guardar cambios).
8. `21-paciente-sin-cita.png`: diálogo "Sin cita" con un paciente demo (p. ej., Jorge Altamirano) y profesional elegido.
9. `22-reprogramar-cita.png`: diálogo de reprogramar una cita del Dr. Villacís con la nueva hora elegida.
10. `23-estado-de-cuenta.png`: ficha de Mateo Morales con el estado de cuenta (tratamientos, valor, saldo).
11. `24-registrar-abono.png`: formulario "Registrar abono" con un abono parcial (p. ej., 20 de 35).
12. `25-comprobante.png`: comprobante del abono abierto.
13. `26-estadisticas.png` (`director@`): Estadísticas.
14. `27-usuarios.png` (`director@`): Usuarios.
15. `28-catalogo.png` (`director@`): Catálogo de tratamientos y especialidades.
16. `29-historial-de-cambios.png` (`director@`): historial de cambios de la ficha de Mateo Morales o de su atención.
17. `30-exportar-datos.png` (`director@`): Auditoría con el botón "Exportar datos" (no hace falta descargar).

Al terminar: `bash docs/manual/build.sh` y, en Resultado, la lista de archivos creados o retomados, cualquier paso que
no se pudo hacer y **cualquier botón o texto de pantalla que no coincida con el manual** (Claude ajusta el texto).
