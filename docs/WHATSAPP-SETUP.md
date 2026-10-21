# WhatsApp Cloud API sin verificación de empresa — guía y tareas del desarrollador

Modalidad elegida (D-3, D-22): **Cloud API con número real, sin verificar la empresa ante Meta**. No se suben
documentos de la empresa. Desarrollo con el número de prueba de Meta; producción con un **chip prepago dedicado**.
Arquitectura del bot: `IMPLEMENTATION-PLAN.md §4.11`.

---

## 1. Límites que aplican (documentación de Meta, oct-2026)

| Qué | Límite sin verificar | ¿Nos afecta? |
|---|---|---|
| Mensajes que inicia la clínica (plantillas: recordatorios) | 250 números distintos por 24 h móviles, para todo el portafolio | No: ~26 recordatorios/día |
| Respuestas a pacientes que escribieron primero (ventana de 24 h) | No cuentan para el límite | No |
| Números registrados por portafolio | Máximo 2 | No: usamos 1 |
| Subir a 2 000 sin verificar | Enviar 2 000 plantillas de buena calidad a números distintos en 30 días | No hace falta |

Si muchos pacientes bloquean o reportan el número, Meta baja su calidad y puede restringirlo. Los recordatorios
salen solo a quienes aceptaron el aviso de privacidad (RF10).

## 2. Política de Meta sobre chatbots de IA (desde el 15-ene-2026)

- **Prohibido:** que proveedores de IA usen la API de WhatsApp Business cuando la IA **es el producto principal**
  (asistentes de propósito general: ChatGPT, Perplexity, Luzia…).
- **Permitido:** empresas que usan IA para atender a **sus propios clientes** (soporte, reservas). Es nuestro caso:
  la IA es solo el medio para entender mensajes del canal de citas de la clínica.
- **Riesgo residual:** Meta decide qué es "funcionalidad principal". Para quedar claramente del lado permitido:
  - El bot solo trata temas de la clínica (citas, horarios, precios, derivar). Fuera de tema → texto fijo
    "Solo puedo ayudarte con citas e información de Bellidental" + menú. Nunca conversación libre.
  - Menú y botones son la vía principal; el texto libre es un atajo.
  - Se presenta como "asistente de citas de Bellidental", no como "chatea con una IA".

---

## 3. El chip, paso a paso

No hace falta desinstalar tu WhatsApp personal: WhatsApp va ligado al **número**, no al celular ni a la ranura
del chip. Lo único que importa es que **el número nuevo no quede registrado en WhatsApp**.

1. **Pon el chip en un celular** (segunda ranura si el tuyo es dual SIM, o un celular viejo). Actívalo con la
   operadora (registro de la línea con cédula, como exige la operadora) y haz una recarga mínima.
2. **Comprueba que recibe SMS y llamadas**: envíale un SMS desde tu número y llámalo.
3. **Revisa si el número ya tuvo WhatsApp** (las operadoras reciclan números de prepago): en el navegador abre
   `https://wa.me/593XXXXXXXXX` (el número sin el 0 inicial) o guárdalo como contacto y búscalo en tu WhatsApp.
   - **No aparece en WhatsApp** → no hagas nada más. **No abras WhatsApp ni WhatsApp Business con ese número.**
   - **Sí aparece** (cuenta de un dueño anterior) → instala WhatsApp en un celular, regístralo con ese número
     (código por SMS), ve a *Ajustes → Cuenta → Eliminar cuenta*, espera unos minutos y desinstala. Ahora sí está libre.
4. **No lo registres en WhatsApp "para probar"**: si lo haces, tendrás que eliminar esa cuenta igual (paso 3).
5. **Ten el chip encendido y con señal** durante el registro en Meta (§4, paso 5): llega un código por SMS o llamada.
6. **Después del registro**, el celular no tiene que quedar encendido, pero **la línea debe seguir activa**:
   haz recargas periódicas. Si la operadora la da de baja por inactividad, puede reasignar el número a otra
   persona y el bot queda inutilizable. Al implantar, pasar la línea a nombre de la clínica (o a un plan).

---

## 4. Meta, paso a paso

**Cuentas (sin trámites de documentos)**
1. **Cuenta personal de Facebook** (la tuya por ahora; al implantar, agregar al director como administrador).
2. **Portafolio comercial** en `business.facebook.com` con el nombre de la clínica. Queda sin verificar.
3. **App en `developers.facebook.com`**: regístrate como desarrollador → *Crear app* → tipo **Business** (o el caso
   de uso "conectar con clientes por WhatsApp") → asígnala al portafolio → agrega el producto **WhatsApp**.
   Meta crea la cuenta de WhatsApp Business (WABA) y un **número de prueba** con token temporal.
4. **Número de prueba (desarrollo)**: en *WhatsApp → API Setup* agrega tu número personal como destinatario
   (máx. 5) y envía la plantilla `hello_world` para comprobar que funciona. Con este número se desarrolla todo.

**Número real (producción)**

5. *WhatsApp → API Setup → Add phone number*:
   - **Nombre visible**: "Bellidental" o "Centro Odontológico Bellidental" (debe corresponder al negocio; Meta lo
     revisa). Categoría: salud / medicina. Sin nombre aprobado no se puede registrar el número para la API.
   - Verificación del número por SMS o llamada (chip encendido, §3 paso 5).
   - **Registro con PIN de verificación en dos pasos (6 dígitos)**. Si la interfaz no lo hace, por API:
     ```
     POST https://graph.facebook.com/<versión>/<PHONE_NUMBER_ID>/register
     {"messaging_product": "whatsapp", "pin": "<6 dígitos>"}
     ```
     **Guarda el PIN**: Meta lo pide para cambiar o eliminar el número.
6. **Usuario del sistema y token permanente**: `business.facebook.com` → *Configuración → Usuarios → Usuarios del
   sistema* → agregar (rol administrador) → *Asignar activos*: la app y la cuenta de WhatsApp (control total) →
   *Generar token*: caducidad **nunca**, permisos `whatsapp_business_messaging` y `whatsapp_business_management`.
   Con tu propia WABA no hace falta *App Review*.

**Para que funcione el bot**

7. **Webhook**: *WhatsApp → Configuration* → *Callback URL* `https://bot.<dominio>/webhook` (en desarrollo, la URL
   del túnel `cloudflared tunnel --url http://localhost:8000`) y un *verify token* que tú inventas → *Verify and
   save* → suscribir el campo **`messages`**. El **App Secret** (*App settings → Basic*) valida la firma
   `X-Hub-Signature-256`.
8. **Plantilla del recordatorio**: *WhatsApp Manager → Plantillas → Crear* → categoría **Utility**, idioma español.
   Propuesta (sin datos clínicos, RNF03):
   - Nombre: `recordatorio_cita`
   - Cuerpo: `Hola {{1}}, le recordamos su cita en Centro Odontológico Bellidental el {{2}} a las {{3}} con {{4}}. Por favor confirme su asistencia.`
   - Ejemplos para la revisión: `Juan`, `martes 14 de octubre`, `10:30`, `Dr. Pérez`.
   - Botones de respuesta rápida: `Confirmar` · `Cancelar` · `Reprogramar`.
   Si la rechazan por parecer publicidad, quitar cualquier frase promocional y reenviar.
9. **Método de pago en la WABA** (*WhatsApp Manager → Configuración de pagos*): las plantillas *utility* fuera de
   la ventana de 24 h se cobran por mensaje (centavos). Temporalmente tu tarjeta; al implantar, la de la clínica.
10. **Política de privacidad y modo Live**: *App settings → Basic* → URL de política de privacidad (una página en
    el dominio del VPS con el aviso de RF10), ícono y categoría → pasar la app a **Live** si los webhooks del número
    real no llegan en modo desarrollo.

**Datos que guardar** (en `.env` del agente, nunca en el repositorio): `PHONE_NUMBER_ID`, ID de la WABA, token
permanente, App Secret, verify token, PIN, versión de la Graph API.

---

## 5. Tus tareas (fuera del código)

**Ahora (tardan días por las revisiones de Meta; en paralelo al código)**
- [ ] Chip: activar, comprobar SMS/llamadas, verificar que no tenga WhatsApp (§3).
- [ ] Meta pasos 1–4: portafolio, app, número de prueba funcionando.
- [ ] Meta paso 5: nombre visible y registro del número real (guardar el PIN).
- [ ] Meta paso 6: token permanente.
- [ ] Meta paso 8: enviar la plantilla `recordatorio_cita` a revisión.

**Pedir a la clínica (los necesita el bot)**
- [ ] Horarios de atención y formas de pago (`application.clinic.*`).
- [ ] Lista de tratamientos con precio público (códigos de `treatment_catalog`) para responder "¿cuánto cuesta…?".
- [ ] Número de WhatsApp de recepción (enlace de derivación `wa.me`).
- [ ] Teléfono para urgencias fuera de horario (la doctora titular).
- [ ] Especialidad que atiende urgencias (normalmente odontología general).
- [ ] Aprobación del texto del aviso de privacidad (RF10), que nombra a Meta, al proveedor del VPS y al de IA.

**Antes del piloto con el número real (tarea 018)**
- [ ] Contratar el VPS (2 vCPU, 4 GB RAM) y un dominio (`app.<dominio>`, `bot.<dominio>`).
- [ ] Cuenta de Google Drive de la clínica para los respaldos.
- [ ] Cuenta y clave del proveedor de IA (tarea 016e; proveedor por elegir, D-20b).
- [ ] Meta pasos 7, 9 y 10: webhook al VPS, método de pago, política de privacidad.

**Al implantar**
- [ ] Pasar VPS, dominio, portafolio de Meta, proveedor de IA y Drive al correo y medio de pago de la clínica;
      quedarte como colaborador. Pasar la línea del chip a nombre de la clínica.

## 6. Costos mensuales que asumirá la clínica (orientativos; verificar al contratar)

| Concepto | Orden de magnitud |
|---|---|
| VPS 2 vCPU / 4 GB | ~5–15 USD/mes |
| Dominio | ~10–15 USD/año |
| Plantillas de recordatorio (Meta) | centavos por mensaje; ~26/día |
| Proveedor de IA (modelo pequeño) | ~1–20 USD/mes según el modelo |
| Recarga del chip | la mínima de la operadora |

Fuentes:
[Meta — Messaging limits](https://developers.facebook.com/docs/whatsapp/messaging-limits) ·
[Meta — Phone numbers](https://developers.facebook.com/docs/whatsapp/cloud-api/phone-numbers) ·
[TechCrunch — WhatsApp bars general-purpose chatbots](https://techcrunch.com/2025/10/18/whatssapp-changes-its-terms-to-bar-general-purpose-chatbots-from-its-platform/)
