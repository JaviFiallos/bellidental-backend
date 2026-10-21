# 011d — Receta PDF: diseño con la marca Bellidental
> Rediseña el PDF de la 011b (`PrescriptionPdfService`, OpenPDF). Pedido del usuario 2026-10-19, a partir de una
> receta de referencia (encabezado con logo y fecha, línea de acento, datos del paciente, marca de agua, servicios
> y franja de contacto). El endpoint, los permisos y las reglas no cambian. El frontend no cambia.
>
> **Instrucciones específicas del usuario (2026-10-19):**
> - Lema (`tagline`): «Creamos bellas sonrisas».
> - Facebook: «Bellidental» (`https://www.facebook.com/p/Bellidental-100082920653203/`).
> - Instagram: «@bellidental» (`https://www.instagram.com/bellidental/`).
> - Perfil de X: **no existe**; no se agrega ni se dibuja. Se habilita soporte y espacio para contacto de correo electrónico (`email`) en su lugar.

## Diseño (A4 vertical, márgenes 36 pt; abajo se reservan ~70 pt para la franja de contacto)
Colores = paleta del frontend (plan §2.3 de `bellidental-frontend`), como constantes en `PrescriptionPdfPalette`:
`BLUE_900 #0F2A44` · `BLUE_800 #1C3C5C` · `BLUE_600 #2C5F8A` · `BLUE_400 #6E93B8` · `BLUE_200 #D3E4F0` ·
`BLUE_50 #EFF6FB` · `GOLD_400 #E3A857` · `CORAL_700 #B04A31` · `SLATE_800 #2E3A46` · `SLATE_600 #4F5D6B` ·
`SLATE_500 #6B7684` · `WHITE`. Tipografía: Helvetica (incluida en OpenPDF), sin fuentes externas.

1. **Encabezado** (tabla de 3 columnas, solo en la primera página):
   - Izquierda: **logo** (`pdf/bellidental-logo.png`) de 72 pt.
   - Centro: `clinic.name` en negrita 18 pt `BLUE_900`; `clinic.tagline` en mayúsculas 8 pt `SLATE_600` con
     espaciado; **"Od. Nombre Apellido"** en negrita 11 pt `BLUE_800` y "Reg. {licenseNumber}" 9 pt (si existe);
     `clinic.address` · "Tel. {clinic.phone}" en 8 pt `SLATE_600`.
   - Derecha: recuadro redondeado `BLUE_800` con tres celdas blancas **D · M · A** rellenas con la fecha de emisión
     (zona de la clínica). Debajo, **"RECETA"** en negrita 14 pt `BLUE_900` y **"CONFIDENCIAL"** en negrita 8 pt
     `CORAL_700`.
2. **Línea de acento**: línea horizontal `GOLD_400` de 2 pt casi de borde a borde (separa clínica y paciente).
3. **Paciente** (tabla de 2 columnas; etiqueta en negrita 8 pt `SLATE_600` y valor 10 pt `SLATE_800`, con una
   línea fina `BLUE_200` bajo cada valor, como renglón de formulario):
   Paciente · Cédula/Documento (etiqueta según `documentType`, como hoy) · Edad · Fecha de emisión ·
   Diagnóstico (CIE-10, a todo el ancho; se omite si no hay). Debajo, centrada en cursiva 9 pt `SLATE_500`:
   **"Presente esta receta en su próxima consulta."**
4. **Cuerpo** con **marca de agua**: el logo a ~300 pt de ancho, centrado en el área de contenido, con opacidad
   0,06 (`PdfGState`), dibujado **debajo** del texto en todas las páginas (evento de página).
   - "Rp." en negrita 16 pt `BLUE_800`.
   - Cada medicamento: número y nombre + concentración + (presentación) en negrita 11 pt `BLUE_900`; debajo, en
     10 pt `SLATE_800`: "Dosis: … · Frecuencia: … · Duración: …", y en líneas aparte "Cantidad" e "Indicaciones"
     solo si vienen (se conserva la regla de la 011b: nada de "null").
   - Indicaciones generales (si vienen): caja con fondo `BLUE_50`, título en negrita `BLUE_800`.
5. **Sello y firma** (tabla de 2 celdas, `setKeepTogether(true)`; si no cabe, pasa entera a la página siguiente):
   - Izquierda: recuadro de ~130 × 90 pt con borde discontinuo `BLUE_400` y la palabra **"Sello"** abajo, 8 pt
     `SLATE_500`.
   - Derecha: espacio para firmar, línea `SLATE_800`, "Od. Nombre Apellido" en negrita, "Reg. …" (si existe) y
     **"Firma"** en 8 pt `SLATE_500`.
6. **Servicios** (al final del contenido, una vez): **"Los mejores tratamientos para ti y tu familia."** centrado
   en cursiva 10 pt `BLUE_800`, y debajo `clinic.services` unidos por " · ", centrados, 9 pt `SLATE_600`.
   Si la lista está vacía, se omite la sección.
7. **Franja de contacto** (en **todas** las páginas, evento de página): rectángulo `BLUE_900` a todo el ancho y
   ~40 pt de alto pegado al borde inferior, con un filete superior `GOLD_400` de 1,5 pt. De izquierda a derecha,
   repartidos en 4 columnas iguales, ícono circular de 14 pt + texto blanco 8 pt:
   **Facebook** (`clinic.social.facebook`) · **X** (`clinic.social.x`, nunca "Twitter") · **Instagram**
   (`clinic.social.instagram`) · **WhatsApp** (`clinic.receptionWhatsapp` formateado `+593 99 999 9999`).
   Un canal vacío no se dibuja y los demás se reparten el ancho.

## Objetivo
1. **Configuración** (`ClinicProperties` + `application.yml`, valores provisionales marcados `# provisional` como
   los actuales): `tagline` ("Odontología integral"), `services` (lista: Odontología general, Endodoncia,
   Ortodoncia, Resinas, Exodoncia de cordales, Blanqueamiento, Prótesis, Profilaxis, Periodoncia,
   Odontopediatría) y `social` (record anidado `Social(facebook, instagram, x)`, p. ej. `bellidental`,
   `@bellidental`, `@bellidental`). Sin migraciones.
2. **Recursos** en `src/main/resources/pdf/` (rasterizados una vez en desarrollo; nada se descarga en ejecución):
   - `bellidental-logo.png` (600 × 600) desde `../bellidental-frontend/docs/brand/bellidental-logo-original.png`,
     con un círculo blanco debajo porque el original tiene las letras transparentes:
     ```
     magick -size 3000x3000 xc:none -fill white -draw "circle 1500,1500 1500,8" ORIGINAL -composite -resize 600x600 -strip src/main/resources/pdf/bellidental-logo.png
     ```
   - Íconos `facebook.png`, `x.png`, `instagram.png`, `whatsapp.png` (64 × 64): glifo blanco de Simple Icons
     (CC0) sobre círculo `#6E93B8`. SVG de
     `https://cdn.jsdelivr.net/npm/simple-icons@13.21.0/icons/{facebook,x,instagram,whatsapp}.svg`, convertidos con
     `rsvg-convert`/`magick` (anotar en Resultado los comandos usados). Peso total de `pdf/` < 150 KB.
3. **Código** (`prescriptions/application`):
   - `PrescriptionPdfService`: arma el documento en el orden de arriba; mantiene `generatePdf`, `requireIssued`
     y los datos solo vía `ClinicalDirectory`, `PatientDirectory`, `ProfessionalDirectory`.
   - `PrescriptionPdfPageDecorator` (nuevo, `PdfPageEventHelper`): marca de agua (debajo del contenido) y franja
     de contacto (encima) en `onEndPage`.
   - `PrescriptionPdfPalette` (nuevo): colores y fuentes; nada de colores sueltos en el servicio.
   - Imágenes cargadas desde el classpath una sola vez (p. ej. en el constructor del decorador o con un
     `ClassPathResource`); si falta un recurso, `IllegalStateException` con mensaje claro.
   - Métodos cortos (uno por bloque: `addHeader`, `addAccentLine`, `addPatient`, `addBody`, `addSealAndSignature`,
     `addServices`).
4. Generar una receta de ejemplo para la revisión visual: el IT escribe los bytes del caso 1 en
   `target/prescription-sample.pdf` (solo escritura de archivo, sin afectar el resultado del test).

## Archivos a tocar
```
src/main/java/com/bellidental/
├── config/ClinicProperties.java                      (+ tagline, services, social)
└── prescriptions/application/
    ├── PrescriptionPdfService.java                   (nuevo diseño)
    ├── PrescriptionPdfPageDecorator.java             (nuevo: marca de agua + franja de contacto)
    └── PrescriptionPdfPalette.java                   (nuevo: colores y fuentes)
src/main/resources/
├── application.yml                                   (application.clinic.tagline/services/social, provisionales)
└── pdf/{bellidental-logo,facebook,x,instagram,whatsapp}.png   (nuevos)
src/test/java/com/bellidental/prescriptions/PrescriptionPdfIT.java   (más aserciones en el caso 1 + muestra)
src/test/resources/application*.yml                   (solo si los tests necesitan las propiedades nuevas)
```
- Sin dependencias nuevas (OpenPDF ya está). Sin cambios en `Directory` ni en otros módulos.

## Criterios de aceptación
1. `GET /api/v1/prescriptions/{id}/pdf` sigue devolviendo `application/pdf` para una receta emitida y 409 en DRAFT
   o anulada; permisos sin cambios.
2. El PDF muestra, en este orden: encabezado (logo, clínica, lema, odontólogo, registro, dirección y teléfono,
   fecha D/M/A, "RECETA", "CONFIDENCIAL"), línea dorada, datos del paciente, "Presente esta receta…", Rp. con los
   medicamentos, indicaciones generales, sello y firma, servicios, y la franja de contacto.
3. La franja usa "X" (nunca "Twitter") y WhatsApp con el número de `receptionWhatsapp`; no hay correo electrónico.
4. Solo colores de `PrescriptionPdfPalette`; marca de agua y franja en todas las páginas; sello y firma nunca
   quedan partidos entre páginas.
5. Una receta con 8 medicamentos con indicaciones pasa a 2 páginas sin solapar la franja ni la marca de agua.
6. `./mvnw -q clean verify` en verde.

## Tests requeridos
**Integration** (`PrescriptionPdfIT`, los 2 casos actuales; sin casos nuevos):
- Caso 1 (`downloadPdf_issuedPrescription_…`): además de «CONFIDENCIAL» y la cédula, el texto extraído contiene
  «Presente esta receta en su próxima consulta», «Sello», «Firma», «Los mejores tratamientos» y el número de
  WhatsApp formateado; **no** contiene «Twitter». Escribe la muestra en `target/prescription-sample.pdf`.
- Caso 2 (DRAFT → 409): sin cambios.

**Unit:** ninguno.

## Resultado
- **Archivos creados o modificados:**
  - `src/main/java/com/bellidental/config/ClinicProperties.java`: agregado `tagline`, `services` y record anidado `Social(facebook, instagram, x, email)`.
  - `src/main/resources/application.yml`: configurado lema «Creamos bellas sonrisas», 10 servicios dentales, redes (`facebook: "Bellidental"`, `instagram: "@bellidental"`, `x: ""`, `email: "contacto@bellidental.com"`).
  - `src/main/resources/pdf/`: generados y optimizados localmente `bellidental-logo.png` (con fondo circular blanco) e íconos circulares `facebook.png`, `instagram.png`, `whatsapp.png`, `x.png`, `email.png` (peso total del directorio: ~88 KB < 150 KB).
  - `src/main/java/com/bellidental/prescriptions/application/PrescriptionPdfPalette.java`: nueva clase con la paleta de colores oficial del frontend (`BLUE_900`, `BLUE_800`, `BLUE_600`, `BLUE_400`, `BLUE_200`, `BLUE_50`, `GOLD_400`, `CORAL_700`, `SLATE_800`, `SLATE_600`, `SLATE_500`, `WHITE`) y tipografías Helvetica.
  - `src/main/java/com/bellidental/prescriptions/application/PrescriptionPdfPageDecorator.java`: nuevo decorador de páginas OpenPDF (`PdfPageEventHelper`) que dibuja la marca de agua con 6% de opacidad (`PdfGState`) y la franja de contacto inferior de 40 pt con borde dorado `GOLD_400`, distribuyendo los canales activos equitativamente (Facebook, Instagram, WhatsApp con formato `+593 00 000 0000`, y espacio para correo; omitiendo X por estar vacío).
  - `src/main/java/com/bellidental/prescriptions/application/PrescriptionPdfService.java`: reescrito modularmente (`addHeader`, `addAccentLine`, `addPatient`, `addBody`, `addSealAndSignature`, `addServices`) con márgenes `36, 36, 36, 70`, bloque de sello y firma indivisible (`table.setKeepTogether(true)`), recuadro D/M/A, línea dorada de acento y subrayado `BLUE_200` en campos del paciente.
  - `src/test/java/com/bellidental/prescriptions/PrescriptionPdfIT.java`: aserciones actualizadas para el Caso 1 (recordatorio, sello, firma, tratamientos, número de WhatsApp formateado, sin Twitter) y guardado de muestra en `target/prescription-sample.pdf`.
- **Resultado de los tests:**
  - Surefire: `Tests run: 92, Failures: 0, Errors: 0, Skipped: 0`
  - Failsafe: `Tests run: 61, Failures: 0, Errors: 0, Skipped: 0`
  - Total: 153 pruebas exitosas en `./mvnw -q clean verify`.
- **Problemas y supuestos tomados:**
  - Siguiendo la indicación explícita del usuario, el perfil de X se dejó vacío y no se renderiza en la franja de contacto ni se referencia; en su lugar se soportó y dejó el canal de correo electrónico (`email`).
  - El lema se renderiza en mayúsculas con espaciado ("C R E A M O S   B E L L A S   S O N R I S A S") en el encabezado.
  - Se generó la imagen PNG de la muestra con `pdftoppm -png -r 80 target/prescription-sample.pdf target/prescription-sample` y se verificó visualmente la correcta disposición estética y alineación.

## Revisión
Estado: APROBADO

Primera revisión: CORRECCIONES (FQN en el IT, imágenes recargadas por petición, marca de agua centrada en la página,
criterio 5 sin verificar, líneas en blanco sobrantes). Corregidas por Claude (subagente) junto con los ajustes
pedidos por el usuario el 2026-10-19:
- Sello + firma + servicios en un bloque anclado al fondo de la última página, sobre la franja (`addClosingBlock`,
  `writeSelectedRows`); si no cabe, pasa entero a la página siguiente. Sello sin recuadro discontinuo; sello y firma
  en columnas iguales, simétricos respecto al centro.
- Indicaciones generales sin caja (sin fondo ni borde) y fuente −1 (título 9, texto 8).
- Íconos y textos de la franja centrados verticalmente.
- `PrescriptionPdfImages` (`@Component`) carga los PNG una sola vez; marca de agua centrada en el área de contenido.
- Muestra larga (8 medicamentos, prueba manual revertida): 2 páginas, sin solapes, marca de agua y franja en ambas,
  bloque de cierre completo al final de la 2.ª.
- X vacío y correo en su lugar por instrucción del usuario (prevalece sobre el criterio 3).

`./mvnw -q clean verify`: surefire 92/0/0/0, failsafe 61/0/0/0.
