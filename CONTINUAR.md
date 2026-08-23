# Estado del proyecto: integración WhatsApp + pagos

Este archivo es el punto de retoma si seguimos en otra máquina/sesión. Bórralo cuando el trabajo pendiente esté hecho.

## Backend: completo (Fases 1-4). Verificado el 2026-08-23: `mvn test` → 82 tests, 0 fallos, BUILD SUCCESS

- **Fase 1** — Dominio: `Canal`, `MetadataOrigen`, `Conversacion`/`EstadoConversacion`, `EstadoPedido` extendido con el tramo de anticipo (`BORRADOR → PENDIENTE_VERIFICACION → ESPERANDO_ANTICIPO → ANTICIPO_PAGADO → NUEVO...`), migración `V14`.
- **Fase 2** — Wompi: `PagoOnline`, `ServicioAnticipos`, `AdaptadorWompi`, `ControladorWebhookWompi`, job de expiración, migración `V15`.
- **Fase 3** — WhatsApp: `OrquestadorWhatsApp` (botones, sin IA), `ClienteGraphApi`, `ControladorWebhookWhatsApp`, `ValidadorFirmaMeta`, migración `V16`.
  - Simplificación deliberada: el bot solo ofrece "para llevar". Domicilio necesita elegir zona y capturar dirección con un WhatsApp Flow (`OrquestadorWhatsApp.java:189`).
- **Fase 4** — IA: `InterpreteClaude` (SDK Java de Anthropic, modelo `claude-opus-5` por defecto), interpreta texto libre contra el menú real, nunca inventa productos.

## Frontend: completo

Columna "Esperando pago" en el tablero de recepción (`src/compartido/config.ts`, `estados.ts`, `tipos.ts`). `tsc -b` y `npm run build` pasan limpio.

## Meta / WhatsApp: número de prueba YA GENERADO (2026-08-23)

El bloqueo anterior (método de pago) quedó resuelto: la Visa •••2759 está
conectada al portfolio y marcada como predeterminada en la cuenta de WhatsApp.
Con eso, "Solicitar número de prueba" funcionó.

> Ojo: la pantalla **Paso 1. Pruébalo** del panel de desarrolladores sigue
> mostrando "No hay números de teléfono disponibles para esta app" aunque el
> número exista. Es un desfase de esa vista, no un error. El estado real se ve en
> Administrador de WhatsApp → Números de teléfono.

### Identificadores (no son secretos)

| Qué | Valor |
|---|---|
| App "El Patio Restaurante Bot" | `1733793524569168` |
| Portfolio comercial "El Patio Gourmet" | `4479587035613615` |
| WABA "Test WhatsApp Business Account" | `2287452685365239` |
| Número de prueba | `+1 555-198-8701` (estado "No verificado": normal en pruebas) |
| **PHONE_NUMBER_ID** | `1316101424914419` |

Ya escritos en `backend/.env` (que git ignora). El `VERIFY_TOKEN` también quedó
generado ahí.

## Pendiente

### 1. Los dos secretos que faltan — necesitan tus manos

Ambos pasos los bloquea Meta (o el clasificador de permisos) para que los haga
una persona. Están en orden; son ~5 minutos.

#### ✅ Token — LISTO (24 h, vence el 2026-08-24)

Ya está en `backend/.env` y **verificado contra la API de Meta**:

```
GET /v21.0/1316101424914419
→ verified_name "Test Number", display_phone_number "+1 555-198-8701"
```

Consultar `/2287452685365239/phone_numbers` sí falla (código 100): el token del
Paso 1 no trae `whatsapp_business_management`. No importa — enviar mensajes solo
necesita `whatsapp_business_messaging`, que sí tiene.

#### a) Cuando venza: Usuario del Sistema → token que no expira

1. business.facebook.com → Configuración → **Usuarios del sistema** → *Agregar*.
2. Nombre `elpatio-bot`, rol **Admin** → *Create system user*.
3. Ya creado, botón **Agregar activos** → pestaña *Cuentas de WhatsApp* →
   marca "Test WhatsApp Business Account" → activa **Control total**.
4. Botón **Generar token nuevo** → app "El Patio Restaurante Bot" →
   caducidad **Nunca** → marca los permisos:
   - `whatsapp_business_messaging`
   - `whatsapp_business_management`
5. Copia el token (**se muestra una sola vez**) a `ELPATIO_WHATSAPP_TOKEN_ACCESO`
   en `backend/.env`.

#### b) Clave secreta de la app — OBLIGATORIA, es el bloqueo actual

developers.facebook.com → la app → Configuración de la app → Básica →
"Clave secreta de la app" → **Mostrar** (pide tu contraseña de Facebook) →
cópiala a `ELPATIO_WHATSAPP_APP_SECRET` en `backend/.env`.

> No es opcional pese a que el default sea vacío. Comprobado el 2026-08-23:
> con el secreto vacío, `new SecretKeySpec("".getBytes(), "HmacSHA256")` lanza
> `IllegalArgumentException: Empty key`; `ValidadorFirmaMeta` la relanza como
> `IllegalStateException` y el webhook responde **500**, con lo que Meta
> reintenta en bucle. Sin este valor no se recibe ni un mensaje.
>
> Mejora pendiente opcional: que `ValidadorFirmaMeta.esValida` devuelva `false`
> cuando el secreto venga en blanco, para rechazar limpio (401) en vez de
> reventar. El caso no está cubierto por los tests.

#### c) Comprobación del token (ya pasó, se deja para repetirla)

```bash
cd backend && set -a && . ./.env && set +a
curl -s "https://graph.facebook.com/v21.0/$ELPATIO_WHATSAPP_PHONE_NUMBER_ID"   -H "Authorization: Bearer $ELPATIO_WHATSAPP_TOKEN_ACCESO"
```

Devuelve los datos del número si el token sirve; `code: 190` si expiró.

### 2. Webhook

Meta necesita una URL pública HTTPS: `https://<dominio>/webhooks/whatsapp`, con
el `VERIFY_TOKEN` que ya está en `backend/.env`. En local hace falta un túnel
(ngrok/cloudflared) porque el backend escucha en `localhost:8080`.

### 3. Verificación del Negocio

Es el "Paso 3" del panel. Puede tardar días y hace falta para salir de pruebas;
para el número de prueba **no** es necesaria.

### 4. Las otras dos integraciones

Wompi (llaves de sandbox) y Anthropic (llave de API) cuando se quiera probar de
punta a punta. Sin ellas el bot funciona igual, solo con botones y sin anticipos.

## Variables de entorno

Todas documentadas en `backend/.env.ejemplo`, con default vacío o de sandbox.
Vacías = la función queda inactiva, el backend arranca igual.
