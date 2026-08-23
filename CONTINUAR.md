# Estado del proyecto: integración WhatsApp + pagos

Este archivo es el punto de retoma si seguimos en otra máquina/sesión. Bórralo cuando el trabajo pendiente esté hecho.

## Backend: completo (Fases 1-4), todo compila y los 82 tests pasan

- **Fase 1** — Dominio: `Canal`, `MetadataOrigen`, `Conversacion`/`EstadoConversacion`, `EstadoPedido` extendido con el tramo de anticipo (`BORRADOR → PENDIENTE_VERIFICACION → ESPERANDO_ANTICIPO → ANTICIPO_PAGADO → NUEVO...`), migración `V14`.
- **Fase 2** — Wompi: `PagoOnline`, `ServicioAnticipos`, `AdaptadorWompi`, `ControladorWebhookWompi`, job de expiración, migración `V15`. Config: `ELPATIO_WOMPI_URL_BASE/LLAVE_PRIVADA/SECRETO_EVENTOS` (vacíos = sandbox sin llaves reales).
- **Fase 3** — WhatsApp: `OrquestadorWhatsApp` (botones, sin IA), `ClienteGraphApi`, `ControladorWebhookWhatsApp`, `ValidadorFirmaMeta`, migración `V16`. Config: `ELPATIO_WHATSAPP_URL_BASE/PHONE_NUMBER_ID/TOKEN_ACCESO/VERIFY_TOKEN/APP_SECRET`.
  - Simplificación: el bot solo ofrece "para llevar" (no domicilio — falta selección de zona vía WhatsApp Flow).
- **Fase 4** — IA: `InterpreteClaude` (SDK Java de Anthropic, modelo `claude-opus-5` por defecto), interpreta texto libre contra el menú real, nunca inventa productos. Config: `ELPATIO_ANTHROPIC_LLAVE/MODELO`.

## Frontend: completo

- Columna "Esperando pago" en el tablero de recepción (`src/compartido/config.ts`, `estados.ts`, `tipos.ts`). `tsc -b` y `npm run build` pasan limpio.

## Pendiente: configurar credenciales reales de Meta/WhatsApp (en progreso)

Se creó en Meta for Developers:
- **App**: "El Patio Restaurante Bot"
- **Portfolio comercial**: "El Patio Gourmet"
- **Cuenta de WhatsApp**: "Test WhatsApp Business Account" (número de prueba gratis, aún sin generar)

**Bloqueado en**: Meta exige, antes de emitir el número de prueba:
1. ~~Aceptar Términos de Servicio de WhatsApp Business~~
2. **Método de pago** — ya se agregó una tarjeta Visa •••2759 al portfolio "El Patio Gourmet".
3. Falta terminar el formulario de **Información fiscal** en business.facebook.com/billing_hub/payment_methods (nombre del negocio "El patio gourmet" ya puesto; dirección "Calle 26 #31-2", ciudad "Turbaco", estado "Bolívar" en proceso de llenarse — falta código postal y guardar).
4. Pendiente: **Verificación del Negocio** (Business Verification) — Meta puede seguir pidiéndola después del método de pago; puede tardar días. Se puede posponer si Meta lo permite tras completar el pago.

**Siguiente paso concreto**: volver a `business.facebook.com/billing_hub/payment_methods`, terminar "Información fiscal" (falta código postal), guardar, y reintentar "Solicitar número de prueba" en developers.facebook.com → la app → Casos de uso → Conectar en WhatsApp → Paso 1. Pruébalo.

Una vez se genere el número de prueba, ahí se sacan:
- `ELPATIO_WHATSAPP_PHONE_NUMBER_ID` (ID del número, visible en esa pantalla)
- `ELPATIO_WHATSAPP_TOKEN_ACCESO` (token temporal de 24h que se muestra ahí mismo; para producción hace falta un token permanente vía Usuario del Sistema)

Después falta:
- Configurar el webhook en Meta (`https://<dominio>/webhooks/whatsapp`, con `ELPATIO_WHATSAPP_VERIFY_TOKEN` inventado por nosotros, y `ELPATIO_WHATSAPP_APP_SECRET` de Configuración de la app → Básica).
- Repetir un proceso similar para Wompi (llaves de sandbox) y Anthropic (llave de API) cuando se quiera probar de punta a punta.

## Referencia rápida de variables de entorno nuevas (todas con default vacío/sandbox)

```
ELPATIO_WOMPI_URL_BASE, ELPATIO_WOMPI_LLAVE_PRIVADA, ELPATIO_WOMPI_SECRETO_EVENTOS
ELPATIO_WHATSAPP_URL_BASE, ELPATIO_WHATSAPP_PHONE_NUMBER_ID, ELPATIO_WHATSAPP_TOKEN_ACCESO, ELPATIO_WHATSAPP_VERIFY_TOKEN, ELPATIO_WHATSAPP_APP_SECRET
ELPATIO_ANTHROPIC_LLAVE, ELPATIO_ANTHROPIC_MODELO
```
