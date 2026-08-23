# Bot de WhatsApp en n8n — estado al 2026-08-23

## Funciona todo menos un paso, y ese paso lo bloquea un fallo de Meta

Instancia: `josequinteroh0000.app.n8n.cloud`
Workflow: **El Patio - WhatsApp Bot** (`yWOcWVl7Mbx2NSR4`), publicado.

### Comprobado extremo a extremo

| Pieza | Estado |
|---|---|
| Meta entrega el webhook a n8n | ✓ verificado en ejecuciones |
| Filtro deja pasar el mensaje | ✓ |
| Switch enruta por botón, con rama de texto libre | ✓ |
| n8n llama a la API de Meta | ✓ |
| Meta acepta la peticion | ✓ |
| Numero del destinatario autorizado | ✗ **unico bloqueo** |

Error final: `(#131030) Recipient phone number not in allowed list`.

## El bloqueo

El numero de prueba solo puede escribirle a numeros de una lista de
autorizados (maximo 5). Esa lista **solo** se administra en:

developers.facebook.com → la app → Casos de uso → Conectar en WhatsApp →
Paso 1. Pruebalo → seccion "Envia un mensaje desde tu numero de prueba"

Esa pantalla dice **"No hay números de teléfono disponibles para esta app"**
aunque el numero exista, este registrado y responda por API.

### Descartado (no perder tiempo repitiendolo)

- No es el bloqueador de anuncios ni Brave Shields: se desactivaron y sigue igual.
- No es la suscripcion de la app a la WABA: se hizo por API, `subscribed_apps`
  devuelve la app correctamente.
- No es el registro del numero: `/register` devolvio `{"success": true}`.
- No hay API publica: `allowed_recipients` y `whatsapp_business_accounts` no
  existen como campos (error 100).
- Volver a pulsar "Solicitar numero de prueba" no lo arregla.

### Que probar cuando se retome

1. **Esperar unas horas y recargar.** Los desfases de esa consola suelen
   resolverse solos; el numero tardo en aparecer tambien la primera vez.
2. **Borrar el numero de prueba** en Administrador de WhatsApp → Numeros de
   telefono (icono de papelera) y volver a pedirlo desde el Paso 1. Al crearlo
   desde ahi deberia quedar vinculado a la app.
3. **Numero real** (Paso 2: Configuracion de produccion). Sin limite de
   destinatarios, pero exige Verificacion del Negocio, que tarda dias.

## Detalles tecnicos que costaron encontrar

### El webhook de n8n llega desenvuelto

El nodo *WhatsApp Trigger* NO entrega el sobre completo de Meta. Entrega
directamente el objeto `value`. La ruta correcta es `$json.messages[0]`, no
`$json.entry[0].changes[0].value.messages[0]`.

### El campo se llama `from_user_id`, no `from`

Esta cuenta usa el sistema nuevo de identidad de WhatsApp (nombres de usuario).
El mensaje entrante trae:

```
messages[0]
  from_user_id : CO.1050723671144282   <- identificador opaco, NO un telefono
  id           : wamid....
  text.body    : Hola
  type         : text
contacts[0]
  profile.name     : Jose
  profile.username : Jose101_
  user_id          : CO.1050723671144282
```

> **Esto afecta tambien al backend en Java.** Si `MensajeEntrante` lee `from`,
> no lo va a encontrar con mensajes de este formato. Hay que revisar
> `ControladorWebhookWhatsApp` / `OrquestadorWhatsApp` antes de conectar el
> tablero de recepcion.

## Credenciales en n8n

- **WhatsApp OAuth account** (para el Trigger): Client ID = App ID
  `1733793524569168`, Client Secret = clave secreta de la app.
- **WhatsApp account** (para los nodos HTTP): Access Token permanente +
  Business Account ID `2287452685365239`.

Ambas dieron "Connection tested successfully".

## Ojo con esto

El webhook de Meta apunta a **un solo sitio**. Ahora apunta a n8n, asi que el
backend en Railway ya no recibe mensajes de WhatsApp. Los dos no pueden
escuchar a la vez.
