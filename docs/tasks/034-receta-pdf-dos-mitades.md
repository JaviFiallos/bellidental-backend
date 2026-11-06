# 034 — Receta PDF en dos mitades: Rp. (izquierda) e Indicaciones (derecha)

## Objetivo
Requerimiento de la clínica: la receta se imprime en **una hoja dividida en dos mitades** que se cortan y se
entregan por separado (formato habitual de receta en Ecuador): a la **izquierda** el *Rp.* (qué medicamento y
cuánto) y a la **derecha** las *Indicaciones* (posología: cómo tomarlo). Cada mitad debe valer sola tras el corte,
así que ambas llevan cabecera, datos del paciente, fecha, "CONFIDENCIAL", firma y sello.

Solo cambia el render del PDF (§4.7, tareas 011b/011d). Sin migraciones, sin cambios de endpoints, DTO ni reglas.
Se conservan la marca (logo, marca de agua, colores, franja de contacto) y el enlace temporal (026).

Distribución (A4 **horizontal**, 842 × 595 pt; cada mitad ≈ A5 vertical):
```
┌──────────────────────────────┬┄┬──────────────────────────────┐
│ [logo] Clínica · Od. · Reg.  │┆│ [logo] Clínica · Od. · Reg.  │
│ Dir. · Tel.   [D|M|A] RECETA │┆│ Dir. · Tel. [D|M|A] INDICAC. │
│               CONFIDENCIAL   │┆│               CONFIDENCIAL   │
│──── línea dorada ────────────│┆│──── línea dorada ────────────│
│ Paciente · Cédula · Edad     │┆│ Paciente · Cédula · Edad     │
│ Fecha · Diagnóstico CIE-10   │┆│ Fecha · Diagnóstico CIE-10   │
│ Rp.                          │┆│ Indicaciones                 │
│ 1. Amoxicilina 500 mg        │┆│ 1. Amoxicilina 500 mg        │
│    (cápsulas)                │┆│    Dosis · Frecuencia ·      │
│    Cant.: 21 cápsulas        │┆│    Duración · Indicaciones   │
│ 2. Ibuprofeno 400 mg ...     │┆│ 2. Ibuprofeno 400 mg ...     │
│                              │┆│ Indicaciones generales       │
│                              │┆│ Presente esta receta en su   │
│                              │┆│ próxima consulta.            │
│   Sello        Firma         │┆│   Sello        Firma         │
│                              │┆│ Los mejores tratamientos...  │
├──── franja de contacto ──────┴┄┴──── franja de contacto ──────┤
└────────────────────────────────────────────────────────────────┘
          (┆ = línea discontinua de corte en el centro)
```

## Archivos a tocar
```
src/main/java/com/bellidental/prescriptions/application/
  PrescriptionPdfService.java         render en A4 horizontal con una tabla de 3 columnas [mitad | corte | mitad]
  PrescriptionPdfPageDecorator.java   marca de agua por mitad, línea de corte, contactos en cada mitad
  PrescriptionPdfPalette.java         solo si hacen falta fuentes nuevas o más pequeñas (título "INDICACIONES")
src/test/java/com/bellidental/prescriptions/
  PrescriptionPdfIT.java              aserciones nuevas en el caso 1 (sin casos nuevos)
docs/IMPLEMENTATION-PLAN.md          §4.7, fila 034 y decisión D-34
```
`PrescriptionPdfImages` no cambia. No tocar el PDF de comprobante (`billing`).

## Criterios de aceptación
1. **Página:** `PageSize.A4.rotate()` (horizontal). Márgenes simétricos izquierda/derecha para que el centro de la
   tabla coincida con el centro de la página; margen inferior que deje libre la franja de contacto.
2. **Estructura:** una `PdfPTable` principal de 3 columnas con anchos `[1, gutter, 1]` (gutter ≈ 28 pt, vacía).
   - Filas de cabecera (`setHeaderRows`) con, en cada mitad: logo (≤ 48 pt), nombre de la clínica, tagline si hay,
     "Od. nombre", "Reg." si hay, dirección · teléfono, recuadro D/M/A, título (**"RECETA"** a la izquierda,
     **"INDICACIONES"** a la derecha) y "CONFIDENCIAL"; la línea dorada; y el bloque del paciente (nombre,
     cédula/documento, edad, fecha de emisión, diagnóstico CIE-10 si hay). Se repiten si la receta pasa de página.
   - Una fila por ítem, alineada en ambas mitades (mismo número a la misma altura), que no se parte entre páginas:
     - Izquierda: `N. medicamento concentración (presentación)` + `Cant.: quantity` si hay. **Sin** dosis,
       frecuencia, duración ni indicaciones.
     - Derecha: `N. medicamento concentración` + `Dosis: … · Frecuencia: … · Duración: …` + `Indicaciones: …` si hay.
   - Encabezados de sección: "Rp." a la izquierda e "Indicaciones" a la derecha.
   - Solo a la derecha: "Indicaciones generales" (si hay) y "Presente esta receta en su próxima consulta.".
3. **Cierre** (anclado al pie como hoy, salta de página si no cabe): en **cada** mitad "Sello" y la línea de firma con
   "Od. nombre", "Reg." y "Firma". Solo a la derecha, debajo, "Los mejores tratamientos para ti y tu familia." y la
   lista de servicios (si hay).
4. **Decorador:**
   - Marca de agua: una por mitad, centrada en su mitad (≈ 200 pt), misma opacidad.
   - Línea de corte: vertical, discontinua, gris claro (`BLUE_200` o `SLATE_500`), en `x = ancho / 2`, desde el margen
     superior hasta la franja de contacto.
   - Franja de contacto: el fondo azul y la línea dorada siguen a todo el ancho; los canales se dibujan **dos veces**,
     una tabla por mitad (ancho de la mitad), para que cada mitad conserve los contactos tras el corte. Sin solaparse
     ni salirse de la franja (reducir fuente/ícono si hace falta).
5. Sin duplicación: un solo método construye la cabecera de una mitad (parámetro: título), otro el bloque del
   paciente y otro el cierre (parámetro: con o sin servicios); ambos lados los reutilizan.
6. Se mantienen las reglas actuales: solo ISSUED no anulada (409 con el mismo mensaje), endpoint con Bearer y
   enlace temporal sin cambios.
7. Plan (`docs/IMPLEMENTATION-PLAN.md`):
   - §4.7, en la viñeta de `GET /prescriptions/{id}/pdf`, añadir: «Formato (tarea 034, D-34): A4 horizontal en dos
     mitades con línea de corte; izquierda Rp. (medicamento, concentración, presentación, cantidad), derecha
     Indicaciones (posología, indicaciones generales); ambas con cabecera, paciente, CONFIDENCIAL, sello y firma.»
   - Fila `| 034 | Receta PDF en dos mitades: Rp. e Indicaciones | — | 011d | PrescriptionPdfIT (aserciones) |`
     tras la 033.
   - Decisión `| D-34 | Receta en A4 horizontal partida en dos mitades que valen solas tras el corte: Rp. (izquierda) e Indicaciones/posología (derecha); el diagnóstico CIE-10 va en ambas | tarea 034 |` tras D-33.
8. `./mvnw -q clean verify` pasa.

## Tests requeridos
- **Unit:** ninguno.
- **Integration:** `PrescriptionPdfIT`, solo el caso 1 (`downloadPdf_issuedPrescription_…`), conservando sus
  aserciones actuales y añadiendo:
  1. La página 1 es horizontal (`reader.getPageSize(1)`: ancho > alto).
  2. El texto contiene "Rp." e "INDICACIONES"; "CONFIDENCIAL" y el número de documento del paciente aparecen **al
     menos 2 veces** (una por mitad).
  3. El nombre del medicamento del ítem aparece al menos 2 veces y la dosis del ítem aparece en el texto.

  No crear casos nuevos ni cambiar los otros cuatro.

## Resultado
- **Qué se hizo:**
  - `src/main/java/com/bellidental/prescriptions/application/PrescriptionPdfPalette.java`: Se incorporaron las fuentes `FONT_INDICACIONES_TITLE` (11 pt bold) para el título de la cabecera derecha y `FONT_INDICACIONES_SECTION` (14 pt bold) para el encabezado de sección, y se ajustó `FONT_FOOTER_CHANNEL` a 7.5 pt para permitir que los canales de contacto quepan holgadamente en el ancho de cada mitad sin desbordarse.
  - `src/main/java/com/bellidental/prescriptions/application/PrescriptionPdfPageDecorator.java`: Se actualizó el decorador de página para dibujar dos marcas de agua (una en cada mitad, centradas y escaladas a 200 pt), se implementó la línea de corte vertical discontinua en `x = width / 2` (`BLUE_200`, trazo discontinuo 4x4) desde el margen superior hasta la franja de contacto, y se duplicó la tabla de canales de contacto con anchos de columna proporcionales a `text.length() + 4f` y `setNoWrap(true)`, evitando que el correo u otros canales se desborden o partan el ícono sobre el texto.
  - `src/main/java/com/bellidental/prescriptions/application/PrescriptionPdfService.java`:
    - Constantes extraídas: `PAGE_MARGIN = 28f`, `GUTTER_WIDTH = 28f`, `BOTTOM_MARGIN = 50f` y método auxiliar `halfWidth(Document)` para reutilizar el cálculo sin repetición de literales.
    - Cabecera: dirección y teléfono en dos líneas con espacio no separable (`"Tel.\u00A0" + phone`) asegurando que el identificador y el número telefónico nunca se separen al renderizar.
    - Cierre perfectamente alineado en 2 filas de la tabla de 3 columnas: fila 1 con `[createHalfClosing | gutter | createHalfClosing]` para que sello y firma queden exactamente a la misma altura en ambas mitades (`createHalfClosing` ya no recibe `withServices`), y fila 2 con `[vacía | gutter | servicios]` bajo la mitad derecha si existen servicios configurados.
    - Renderizado general en A4 horizontal (`PageSize.A4.rotate()` con rotación normalizada a 0), 3 filas de cabecera repetibles entre páginas (`setHeaderRows(3)`), ítems alineados fila a fila e indicaciones generales y aviso de próxima consulta solo a la derecha.
  - `src/test/java/com/bellidental/prescriptions/PrescriptionPdfIT.java`: Se añadieron las aserciones requeridas al caso 1 (página 1 horizontal con ancho > alto, presencia de "Rp." e "INDICACIONES", "CONFIDENCIAL" y documento del paciente al menos 2 veces, nombre del medicamento al menos 2 veces y dosis en el cuerpo). Renombrado `medName` a `medicationName` sin abreviaturas.
  - `docs/IMPLEMENTATION-PLAN.md`: Se actualizó §4.7 con el detalle del formato D-34, se añadió la tarea 034 tras la 033 y la decisión D-34 tras D-33.
  - Regenerado `target/prescription-sample.pdf` mediante `PrescriptionPdfIT` y verificado que el layout cumple con la alineación del cierre, la cabecera sin partición telefónica y la franja de contacto proporcionada.
- **Resultado de los tests:**
  - Surefire: Tests run: 103, Failures: 0, Errors: 0, Skipped: 0
  - Failsafe: Tests run: 101, Failures: 0, Errors: 0, Skipped: 0
- **Problemas encontrados y supuestos tomados:**
  - Ninguno. Todas las correcciones de la revisión fueron aplicadas y verificadas contra la suite completa de pruebas.

## Revisión
Estado: CORRECCIONES

Revisado el PDF de muestra (`target/prescription-sample.pdf` renderizado): la distribución, la línea de corte, las
dos marcas de agua, las cabeceras repetidas y el contenido de cada mitad cumplen. Totales 103/101 sin fallos. Faltan
detalles visuales y de limpieza:

1. **Cierre desalineado:** el sello y la firma de la izquierda quedan ~60 pt más abajo que los de la derecha, porque
   la mitad derecha lleva los servicios dentro de la misma celda. Armar el cierre como **dos filas** de la tabla de
   3 columnas: fila 1 = sello + firma en ambas mitades (misma altura); fila 2 = `[vacía | gutter | servicios]`.
   `createHalfClosing` deja de recibir `withServices`.
2. **Cabecera:** la línea de contacto se parte entre "Tel." y el número ("… Baños de Agua Santa · Tel." / "0962198909").
   Poner la dirección y "Tel. <número>" en dos líneas, o unir "Tel." y el número con espacio no separable para que
   nunca se separen.
3. **Franja de contacto:** en cada mitad el correo no cabe en su columna y el ícono queda arriba del texto. Anchos de
   columna proporcionales al largo del texto de cada canal (p. ej. `text.length() + 4`) para que todos queden en una
   línea con el ícono al lado.
4. **Constantes repetidas:** `28f` (margen y gutter) y el cálculo de `halfWidth` se repiten en `render`,
   `addMainTable` y `addClosingBlock`. Extraer `PAGE_MARGIN`, `GUTTER_WIDTH` y un método `halfWidth(Document)`.
5. **IT:** `medName` → `medicationName` (sin abreviaturas).

Tras corregir, regenerar `target/prescription-sample.pdf` con el IT de la tarea y anotar en Resultado que se revisó.

### Revisión 2
Estado: APROBADO

Revisado otra vez el PDF de muestra: el sello y la firma están a la misma altura en las dos mitades, «Tel. <número>»
va en su propia línea y en la franja cada canal ocupa una sola línea con el ícono al lado. Las constantes
`PAGE_MARGIN`/`GUTTER_WIDTH` y `halfWidth(Document)` reemplazan los literales y el IT usa `medicationName`. Totales
103/101 sin fallos.
