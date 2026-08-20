# Restaurante El Patio

Sistema de sala para el Restaurante El Patio, en Turbaco, Bolívar. Cubre el
sitio público, la toma de pedidos en el salón, la pantalla de cocina, la
recepción de domicilios y para llevar, el cobro y el panel administrativo.

```
Frontend   React 18 · TypeScript · Vite · Tailwind
Backend    Spring Boot 3.3 · Java 21 · arquitectura hexagonal
Base       PostgreSQL 16 con migraciones de Flyway
Tiempo real WebSocket con STOMP
```

El detalle del backend está en [`backend/LEEME.md`](backend/LEEME.md).

---

## Índice

- [Cómo desplegar en Railway](#cómo-desplegar-en-railway)
- [Variables de entorno](#variables-de-entorno)
- [Cómo crear usuarios](#cómo-crear-usuarios)
- [Respaldos y restauración](#respaldos-y-restauración)
- [Ubicación exacta de los domicilios](#ubicación-exacta-de-los-domicilios)
- [Impresión en caja](#impresión-en-caja)
- [Desarrollo local](#desarrollo-local)

---

## Cómo desplegar en Railway

El proyecto son **tres servicios** dentro de un mismo proyecto de Railway. Se
despliegan desde el mismo repositorio cambiando el directorio raíz de cada uno.

### 1. La base de datos

En el proyecto de Railway: **New → Database → PostgreSQL**.

Railway crea la instancia y expone `DATABASE_URL`. No hay que correr ninguna
migración a mano: Flyway las aplica solas cuando el backend arranca por primera
vez.

### 2. El backend

**New → GitHub Repo →** este repositorio. Después, en *Settings*:

| Ajuste | Valor |
|---|---|
| Root Directory | `backend` |
| Builder | Dockerfile (lo detecta por `backend/railway.json`) |
| Health Check Path | `/salud` |

Variables (*Variables* del servicio):

```
DATABASE_URL          ${{Postgres.DATABASE_URL}}
DATABASE_USUARIO      ${{Postgres.PGUSER}}
DATABASE_CLAVE        ${{Postgres.PGPASSWORD}}
ELPATIO_JWT_SECRETO   <genérelo, ver abajo>
ELPATIO_CORS_ORIGENES https://el-dominio-del-frontend
ELPATIO_PERFIL        produccion
ELPATIO_ZONA          America/Bogota
ELPATIO_LATITUD       <latitud del local>
ELPATIO_LONGITUD      <longitud del local>
```

`${{Postgres.DATABASE_URL}}` es la sintaxis de Railway para referenciar otro
servicio; ajústela al nombre que le haya puesto a la base.

> **El secreto del JWT no tiene valor por defecto a propósito.** Si falta, el
> backend no arranca. Un secreto de ejemplo funcionando en producción es peor
> que un fallo visible. Genérelo con:
>
> ```bash
> openssl rand -base64 48
> ```
>
> Mínimo 32 caracteres, o el arranque falla con un mensaje explícito.

Genere el dominio del servicio en *Settings → Networking → Generate Domain* y
anótelo: lo necesita el frontend.

### 3. El frontend

**New → GitHub Repo →** el mismo repositorio. En *Settings*:

| Ajuste | Valor |
|---|---|
| Root Directory | `/` (la raíz) |

Variables:

```
VITE_URL_API   https://el-dominio-del-backend
```

> **Ojo con esto:** Vite incrusta las variables `VITE_*` **en el momento de
> compilar**, no al arrancar. Si cambia `VITE_URL_API`, hay que **redesplegar**
> el frontend; reiniciarlo no basta. Es el error que más tiempo cuesta
> encontrar, porque la aplicación arranca bien y falla solo al pedir datos.

La URL del WebSocket se deduce sola de la del API (`https` → `wss`), así que no
hace falta declararla salvo que el canal viva en otro dominio.

### 4. Cerrar el círculo del CORS

Con el dominio del frontend ya generado, vuelva al backend y ponga
`ELPATIO_CORS_ORIGENES` con ese dominio exacto. Acepta varios separados por
coma, por si hay un dominio propio además del de Railway:

```
ELPATIO_CORS_ORIGENES=https://elpatio.up.railway.app,https://elpatio.com.co
```

**Nunca ponga `*`.** El navegador del mesero envía el token en cada petición, y
un origen abierto dejaría que cualquier página lo usara en su nombre.

### 5. Primer arranque

Mire los registros del backend. La primera vez imprime el personal creado con
sus claves:

```
===========================================================
  PERSONAL CREADO — ESTAS CLAVES NO SE VUELVEN A MOSTRAR
===========================================================
  mesero         mesero     5XXR35DTJ8
  ...
```

**Anótelas en ese momento.** No se guardan en claro en ninguna parte.

---

## Variables de entorno

### Backend

| Variable | Obligatoria | Por defecto | Qué hace |
|---|---|---|---|
| `DATABASE_URL` | sí | `jdbc:postgresql://localhost:5432/elpatio` | Conexión a PostgreSQL |
| `DATABASE_USUARIO` | sí | `elpatio` | Usuario de la base |
| `DATABASE_CLAVE` | sí | `elpatio` | Clave de la base |
| `ELPATIO_JWT_SECRETO` | **sí** | *(ninguno)* | Firma de los tokens. Mínimo 32 caracteres |
| `ELPATIO_CORS_ORIGENES` | sí | `http://localhost:5173` | Dominios autorizados, separados por coma |
| `ELPATIO_PERFIL` | no | `desarrollo` | `produccion` cambia los registros a JSON |
| `ELPATIO_ZONA` | no | `America/Bogota` | Zona del día operativo y del turno |
| `ELPATIO_LATITUD` | no | `10.3403` | Latitud del local, para medir distancias |
| `ELPATIO_LONGITUD` | no | `-75.4136` | Longitud del local |
| `ELPATIO_JWT_MINUTOS` | no | `20` | Vida del token de acceso |
| `ELPATIO_JWT_DIAS_REFRESCO` | no | `30` | Vida del token de refresco |
| `PORT` | no | `8080` | Lo inyecta Railway |

### Frontend

| Variable | Obligatoria | Qué hace |
|---|---|---|
| `VITE_URL_API` | sí | URL del backend, sin barra final |
| `VITE_URL_WS` | no | Solo si el WebSocket vive en otro dominio |

Ningún secreto va en las variables del frontend: **todas quedan escritas en el
paquete compilado** y cualquiera puede leerlas.

---

## Cómo crear usuarios

### Desde la aplicación

Entre como administrador a **`/admin/configuracion` → Personal**. Ahí se crean
usuarios, se cambian claves y se desactivan personas que ya no trabajan.

Dos comportamientos que conviene conocer:

- **Cambiar la clave de alguien cierra todas sus sesiones abiertas.** Es a
  propósito: si se cambia porque la persona se fue, el token viejo no puede
  seguir sirviendo justo cuando se quiso cortar el acceso.
- **Desactivar a alguien también lo saca de inmediato.** No hay que esperar a
  que expire nada.
- Al editar un usuario, **dejar la clave en blanco significa «no la cambie»**.
  La pantalla nunca recibe el hash, así que no puede reenviarlo.

### Los cinco roles

| Rol | Entra a | Para qué |
|---|---|---|
| `mesero` | `/comandera` | Abrir mesas, tomar y enviar comandas |
| `cocina` | `/cocina` y `/cocina/bar` | Ver y despachar lo que está en producción |
| `recepcion` | `/recepcion` | Recibir domicilios y para llevar |
| `cajero` | `/admin`, `/recepcion`, cobrar | Caja, cierre y recepción |
| `administrador` | todo | Además: carta, reportes, configuración, anulaciones |

### Si se perdieron todas las claves de administrador

El sembrador solo corre contra una tabla de usuarios vacía. Para volver a
generarlas, borre las filas y reinicie el backend:

```bash
psql "$DATABASE_URL" -c "delete from sesiones_refresh; delete from usuarios;"
```

> Esto **no** borra ventas ni comandas. Sí desasigna el mesero de las órdenes
> abiertas, porque la referencia queda en nulo. Hágalo con el salón cerrado.

---

## Respaldos y restauración

Los guiones están en `backend/scripts/` y necesitan `pg_dump` y `psql`.

### Respaldo manual

```bash
cd backend
DATABASE_URL="postgresql://usuario:clave@servidor:5432/elpatio" ./scripts/respaldar.sh
```

Deja un `.sql.gz` con fecha en `backend/respaldos/`, comprueba que no salió
vacío ni dañado, y borra los de más de 30 días (`DIAS_RETENCION` lo cambia).

### Respaldo automático diario

En Railway: **New → Cron Job**, apuntando a este repositorio con directorio raíz
`backend`, y como comando:

```
./scripts/respaldar.sh
```

Con la programación `0 8 * * *` (las 3 a. m. en Colombia, con el salón cerrado).
Necesita las mismas variables de base que el backend.

> **Un respaldo guardado en el mismo proveedor que la base no es un respaldo.**
> Si se pierde la cuenta, se pierden los dos. Railway hace sus propias copias y
> eso cubre el fallo de un disco, pero no un borrado hecho desde adentro ni la
> pérdida del acceso. Para una copia fuera del proveedor, defina también:
>
> ```
> RESPALDO_S3_BUCKET=elpatio-respaldos
> AWS_ACCESS_KEY_ID=...
> AWS_SECRET_ACCESS_KEY=...
> ```
>
> Sirve cualquier almacén compatible con S3: Cloudflare R2 y Backblaze B2 son
> los más baratos para este volumen (el respaldo pesa unos pocos megabytes).

### Restaurar

```bash
cd backend
DATABASE_URL="postgresql://..." ./scripts/restaurar.sh respaldos/elpatio-20260820T213140Z.sql.gz
```

**Borra lo que haya en la base de destino.** Por eso pide escribir el nombre
exacto de la base para confirmar: no es algo que deba poder hacerse con una
flecha arriba en la terminal.

Al terminar imprime los conteos por tabla. Después, arranque el backend contra
esa base y entre a `/admin/cierre`: si el turno cuadra, la restauración sirvió.

### Comprobar que los respaldos sirven

```bash
cd backend
DATABASE_URL="postgresql://..." ./scripts/probar-respaldo.sh
```

Toma el respaldo más reciente, lo restaura en una base **nueva y temporal**,
compara los conteos contra el origen y borra la base de prueba. No toca la de
producción en ningún momento.

Córralo cada tanto —el primer lunes de cada mes es un buen hábito—. Un volcado
que nunca se restauró no es una copia de seguridad: es una suposición.

Salida esperada:

```
Origen:      5,18,59,19,12,2,7
Restaurado:  5,18,59,19,12,2,7

CORRECTO: el respaldo restaura la misma cantidad de filas en todas las tablas.
```

---

## Ubicación exacta de los domicilios

Cuando un cliente pide a domicilio, el formulario le ofrece compartir su
ubicación. Si acepta, la tarjeta de recepción muestra botones de **Waze** y
**Mapa** que abren el punto exacto; si no, muestra un enlace de búsqueda armado
con la dirección escrita.

Es **opcional en todo momento**: si el cliente niega el permiso, el pedido entra
igual y la dirección escrita es la que manda.

Recepción ve dos advertencias cuando corresponde:

- **«Ubicación aproximada (±N m)»** cuando el navegador informó poca precisión.
  Un GPS da unos quince metros; una posición deducida de la IP da kilómetros y
  *parece* precisa sin serlo.
- **«Pidió a N km del local»** cuando el punto está lejos, que casi siempre
  significa que el cliente estaba en otro sitio —el trabajo, por ejemplo— y la
  coordenada no es la de la entrega.

> **Ponga las coordenadas reales del local.** `ELPATIO_LATITUD` y
> `ELPATIO_LONGITUD` traen por defecto el centro de Turbaco, no la puerta del
> restaurante. Sirven para que la cuenta de distancia no falle, no para navegar.
> Se sacan abriendo Google Maps sobre el local y copiando el par de números que
> aparece en la URL.

La ubicación es dato personal preciso. Solo se captura con un toque explícito
del cliente, solo se guarda en domicilios, y solo la ve quien despacha —que ya
son los únicos roles con acceso a `/api/pedidos`—.

---

## Impresión en caja

El sistema imprime en térmica de **80 mm**: comprobante de venta, precuenta,
comanda de cocina y de barra, y el corte de caja.

### Activar `--kiosk-printing` en Chrome

Sin esto, cada impresión abre el diálogo de Chrome y alguien tiene que darle
«Imprimir». En una caja con fila eso no funciona. Con la bandera, el documento
sale directo a la impresora predeterminada.

**Windows.** Clic derecho en el acceso directo de Chrome → *Propiedades* → en
*Destino*, al final de la línea y **después** de las comillas:

```
"C:\Program Files\Google\Chrome\Application\chrome.exe" --kiosk-printing --app=https://el-dominio-del-frontend/comandera
```

**Linux:**

```bash
google-chrome --kiosk-printing --app=https://el-dominio-del-frontend/comandera
```

**macOS:**

```bash
open -a "Google Chrome" --args --kiosk-printing
```

Tres cosas que hay que dejar listas en el equipo de caja:

1. **La térmica tiene que ser la impresora predeterminada del sistema.**
   `--kiosk-printing` manda a la predeterminada sin preguntar.
2. **Tamaño de papel en 80 mm** en las preferencias del controlador. El
   documento declara `@page { size: 80mm auto }`, pero si el controlador está
   en Carta, el rollo sale con márgenes enormes.
3. **Márgenes en cero** y *Escala: 100 %*. Cualquier escalado corta las
   columnas de precios.

### Sobre el comprobante y la DIAN

**El comprobante que imprime este sistema NO es una factura electrónica de
venta.** Es un comprobante interno, y el pie lo dice textualmente. Por eso:

- no se titula «Factura»,
- no lleva número de resolución (`DATOS_FISCALES.resolucion` está vacío a
  propósito, y mientras lo esté no se imprime nada en ese renglón),
- el espacio para la resolución y el prefijo está previsto y se llena solo
  cuando el restaurante quede habilitado ante la DIAN.

El análisis de lo que exige la Resolución 000165 de 2023 para tiquete POS
electrónico está en `FACTURACION-DIAN.md`.

Los datos del establecimiento (razón social, NIT, régimen) se editan en
`src/compartido/config.ts`, en `DATOS_FISCALES`. **Cámbielos antes de imprimir
nada de verdad:** los que están son de ejemplo.

---

## Desarrollo local

Hace falta Node 20+, JDK 21 y Docker.

```bash
# 1. La base
cd backend && docker compose up -d

# 2. El backend
JAVA_HOME=/ruta/al/jdk-21 \
DATABASE_URL=jdbc:postgresql://localhost:5433/elpatio \
DATABASE_USUARIO=elpatio DATABASE_CLAVE=elpatio \
ELPATIO_JWT_SECRETO="$(openssl rand -base64 48)" \
ELPATIO_CORS_ORIGENES=http://localhost:5173 \
mvn spring-boot:run

# 3. El frontend, en otra terminal
npm install
VITE_URL_API=http://localhost:8080 npm run dev
```

El contenedor publica PostgreSQL en el **5433** y no en el 5432, porque muchas
máquinas ya tienen un PostgreSQL propio en el puerto estándar. Si los dos
compiten, el error que aparece es «autenticación fallida», que no dice nada
sobre el conflicto.

### Comprobaciones

```bash
npm run typecheck            # tipos del frontend
npm run build                # paquete de producción
cd backend && mvn test       # reglas de INC, propina y domicilio
```

### Probar entre dos dispositivos

`npm run dev` ya escucha en toda la red. Abra `http://<ip-del-equipo>:5173`
desde el celular, agregue esa IP a `ELPATIO_CORS_ORIGENES` y compile el frontend
con `VITE_URL_API=http://<ip-del-equipo>:8080`.
