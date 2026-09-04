# Restaurante El Patio

Sistema de sala para el Restaurante El Patio, en Turbaco, Bolívar. Cubre el
sitio público, la toma de pedidos en el salón, la pantalla de cocina, la
recepción de domicilios y para llevar, el cobro y el panel administrativo.

> El mismo dueño tiene un segundo restaurante,
> [La Carreta](https://github.com/jquinteroh2-ops/La_Carreta), que corre este
> mismo software con otra marca y otra base de datos. Lo único que los une es
> un botón en el panel administrativo: ver
> [El otro restaurante](#el-otro-restaurante).

```
Frontend   React 18 · TypeScript · Vite · Tailwind
Backend    Spring Boot 3.3 · Java 21 · arquitectura hexagonal
Base       PostgreSQL 16 con migraciones de Flyway
Tiempo real WebSocket con STOMP
```

El detalle del backend está en [`backend/LEEME.md`](backend/LEEME.md).

---

## Índice

- [El otro restaurante](#el-otro-restaurante)
- [Cómo desplegar en Railway](#cómo-desplegar-en-railway)
- [Variables de entorno](#variables-de-entorno)
- [Cómo crear usuarios](#cómo-crear-usuarios)
- [Modo demostración](#modo-demostración)
- [Aparecer en Google](#aparecer-en-google)
- [Por qué las pantallas no hay que recargarlas](#por-qué-las-pantallas-no-hay-que-recargarlas)
- [Respaldos y restauración](#respaldos-y-restauración)
- [Pedidos y reservas que llegan por WhatsApp](#pedidos-y-reservas-que-llegan-por-whatsapp)
- [Qué se edita sin desplegar](#qué-se-edita-sin-desplegar)
- [Las fotos de los platos](#las-fotos-de-los-platos)
- [Ubicación exacta de los domicilios](#ubicación-exacta-de-los-domicilios)
- [Impresión en caja](#impresión-en-caja)
- [Desarrollo local](#desarrollo-local)

Aparte: [análisis de facturación electrónica ante la DIAN](FACTURACION-DIAN.md).

---

## El otro restaurante

El dueño tiene dos locales: **El Patio** y **La Carreta**. Los dos corren este
mismo software, y aun así son **dos despliegues completamente separados**: cada
uno con su base de datos, su carta, su personal, su caja y su dominio.

```
    elpatio.co                       lacarreta.co
 ┌──────────────┐                  ┌──────────────┐
 │  Público     │                  │  Público     │
 │  Salón       │                  │  Salón       │
 │  Admin  ─────┼───── salta ─────▶│  Admin       │
 └──────┬───────┘                  └──────┬───────┘
   BD el_patio                       BD la_carreta
```

Separados, y no una sola base con una columna «restaurante», porque la venta de
dos restaurantes en una sola base es un problema contable esperando el cierre de
mes: son dos NIT, dos cajas y dos nóminas, y una consulta a la que se le olvide
filtrar no da un error sino una cifra creíble y equivocada. El precio es que el
código está duplicado en dos repositorios: un arreglo hay que aplicarlo dos
veces, y la forma barata de hacerlo es `git cherry-pick`, no reescribirlo.

### El botón que los une

En `/admin` aparece arriba a la derecha un selector con el nombre del
restaurante en el que se está; al desplegarlo, un enlace lleva **a la misma
sección** del otro local. Solo lo ve el administrador: a un cajero no le sirve,
trabaja en un local y el otro no lo conoce.

Se enciende con una variable en el frontend:

```bash
VITE_URL_HERMANO=https://carreta-frontend-production.up.railway.app
```

Sin ella el selector **no se pinta**, que es lo que se quiere en desarrollo:
allí casi nunca están los dos sistemas levantados.

### La cuenta del dueño es una sola

El dueño tiene una cuenta de administrador en cada local, y eran dos cuentas de
verdad: cambiar la clave en La Carreta no la cambiaba en El Patio, y había que
acordarse de hacerlo dos veces y de cuál era cuál.

Ahora **el cambio que hace en un panel viaja al otro**. Se activa con la
dirección del backend hermano:

```bash
ELPATIO_CRUCE_API_HERMANA=https://carreta-backend-production.up.railway.app
```

Vacía = no se replica nada y guardar un usuario funciona como siempre.

**Qué se copia y qué no.** Se copia lo que ES la persona: nombre, correo, clave
y nombre de usuario. **No** se copian el rol ni si la cuenta está activa, porque
esos dicen qué puede hacer *aquí*, y son decisión de cada restaurante:
suspenderle el acceso a un local no tiene por qué cerrarle el otro.

Solo viaja para cuentas de **administrador**, y solo se aplica allá si existe una
cuenta de administrador con ese mismo nombre de usuario. Para cualquier otra
persona no pasa nada.

**Viaja el hash, no la clave.** Los dos sistemas cifran igual, así que el hash
calculado de un lado sirve tal cual en el otro. La clave en limpio no sale nunca
del servidor donde se escribió.

#### Por qué es síncrono y por qué avisa cuando falla

Sincronizar el personal entero se descartó justamente por esto: dos bases que se
escriben la una a la otra se desfasan en silencio cuando una llamada se pierde, y
nadie se entera hasta que algo no cuadra.

Aquí el riesgo se acota de tres maneras. Es **una** cuenta, no un directorio. El
cambio lo hace una persona que está mirando la pantalla, así que se le puede
decir la verdad —«se guardó aquí pero no allá, vuelva a guardar»— en vez de
esconderlo en una cola. Y aplicar el mismo cambio dos veces no hace daño, así que
**reintentar converge**.

Por eso no hay bandeja de salida ni reintentos automáticos. Un fallo se ve en
pantalla, se vuelve a pulsar guardar, y listo.

La llamada al hermano se hace en el controlador y no en el servicio, a propósito:
el servicio guarda dentro de una transacción, y meter ahí una petición HTTP de
hasta cinco segundos es tener una conexión de base bloqueada esperando a otro
servidor.

#### Si el nombre de usuario cambia

El sobre lleva el nombre de usuario **anterior** además del nuevo, y el destino
busca por el anterior. Sin eso, cambiarse el nombre de usuario rompería el
vínculo para siempre: el destino buscaría un usuario que allá todavía no existe,
no encontraría nada, y las dos cuentas quedarían separadas sin que nadie lo note.

---

### El pase: por qué no vuelve a pedir la clave

Son dos servidores con sus propios usuarios y sus propios tokens, así que la
credencial de uno no vale en el otro. Antes de saltar, el selector le pide a su
propio servidor un **pase**: un token de **30 segundos y un solo uso** que dice
quién es el dueño, firmado con un secreto que comparten los dos restaurantes.

El destino exige las tres cosas: firma válida, pase sin usar, y **una cuenta de
administrador activa allá con ese mismo nombre de usuario**. El pase no abre nada
por sí mismo —dice quién es la persona—; quien decide es el destino.

Se enciende con `ELPATIO_CRUCE_SECRETO`, **el mismo valor en los dos
restaurantes** y distinto del de las sesiones. Sin esa variable el cruce queda
apagado en los dos sentidos y el selector sigue saltando: el otro sistema pedirá
la clave, como antes.

Compartir el secreto de las sesiones habría sido más simple y habría significado
que **cualquier** token de un restaurante vale en el otro. Con un secreto propio,
lo que se comparte es la capacidad de decir «esta persona ya se identificó ante
mí», y nada más.

El pase viaja en el **fragmento** de la URL (`#pase=…`) y no en la consulta,
porque el navegador no manda el fragmento a ningún servidor: no queda en
registros de acceso ni en la cabecera `Referer`. El frontend lo borra de la barra
de direcciones antes de canjearlo.

El detalle completo está en el
[README de La Carreta](https://github.com/jquinteroh2-ops/La_Carreta#el-pase-por-qué-no-vuelve-a-pedir-la-clave).

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

## El volumen de datos

El backend de cada restaurante tiene un **volumen montado en `/aplicacion/datos`**
(servicio `backend`). Ahí van las hojas de vida de quien se postula y los
adjuntos de las PQR.

Sin volumen, el sistema de archivos del contenedor se borra en cada despliegue y
esos archivos desaparecen sin que nadie se entere: la fila de la postulación
sigue en la base, pero el PDF que la persona subió ya no está.

### Lo que costó montarlo

**El volumen llega con dueño `root` y el proceso corre como `elpatio`.** La
imagen crea `/aplicacion/datos` y se lo da al usuario que ejecuta, pero eso pasa
al CONSTRUIR; el volumen se monta después, encima de esa carpeta y con sus
propios permisos. El resultado era un `AccessDeniedException` al arrancar, y las
hojas de vida sin poder guardarse.

La salida fácil era correr todo como root. Lo que hace `backend/arranque.sh` es
lo contrario: entra como root, corrige el dueño de la carpeta ya montada, y
**cambia a `elpatio` con `setpriv` antes de ejecutar Java**. Root vive unos
milisegundos y solo para un `chown`; el proceso que atiende peticiones nunca
tiene privilegios.

Se usa `setpriv` y no `su` porque `su` deja una sesión intermedia de PID 1: la
señal de apagado le llegaría a ella y no a Java, y el contenedor tardaría medio
minuto en morir cortando peticiones en curso. `setpriv` reemplaza el proceso, así
que Java queda de PID 1 y recibe las señales directamente.

### Ojo el día del respaldo

`respaldar.sh` vuelca la base, y **estos archivos no están en la base**. Un
respaldo completo tiene que llevarse además el contenido del volumen.

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
| `ELPATIO_LATITUD` | no | `10.3390034` | Latitud del local, para medir distancias |
| `ELPATIO_LONGITUD` | no | `-75.4225372` | Longitud del local |
| `ELPATIO_JWT_MINUTOS` | no | `20` | Vida del token de acceso |
| `ELPATIO_JWT_DIAS_REFRESCO` | no | `30` | Vida del token de refresco |
| `ELPATIO_CLAVE_DEMO` | no | *(vacía)* | Enciende el [modo demostración](#modo-demostración) |
| `PORT` | no | `8080` | Lo inyecta Railway |

### Frontend

| Variable | Obligatoria | Qué hace |
|---|---|---|
| `VITE_URL_API` | sí | URL del backend, sin barra final |
| `VITE_URL_WS` | no | Solo si el WebSocket vive en otro dominio |
| `VITE_URL_HERMANO` | no | URL del sitio de La Carreta. Enciende el selector de restaurante en `/admin` |

Ningún secreto va en las variables del frontend: **todas quedan escritas en el
paquete compilado** y cualquiera puede leerlas.

---

## Cómo crear usuarios

### Desde la aplicación

Entre como administrador a **`/admin/configuracion` → Personal**.

- **«Nueva cuenta»** abre la ficha en blanco: nombre, usuario, correo, rol y
  clave.
- **Tocar un nombre** de la lista abre esa misma ficha para cambiarle cualquier
  cosa, la clave incluida.
- **El interruptor de la derecha** suspende o devuelve el acceso sin abrir nada.

Cuatro comportamientos que conviene conocer:

- **Cambiar la clave de alguien cierra todas sus sesiones abiertas.** Es a
  propósito: si se cambia porque la persona se fue, el token viejo no puede
  seguir sirviendo justo cuando se quiso cortar el acceso.
- **Desactivar a alguien también lo saca de inmediato.** No hay que esperar a
  que expire nada.
- Al editar un usuario, **dejar la clave en blanco significa «no la cambie»**.
  La pantalla nunca recibe el hash, así que no puede reenviarlo.
- **El correo es opcional y no sirve para entrar.** Se entra con el usuario, que
  es corto y se teclea de pie y con prisa. El correo es por donde administración
  avisa un cambio de clave o de turno.

### Los seis roles

| Rol | Entra a | Para qué |
|---|---|---|
| `mesero` | `/comandera` | Abrir mesas, tomar y enviar comandas |
| `cocina` | `/cocina` y `/cocina/bar` | Ver y despachar lo que está en producción |
| `recepcion` | `/recepcion` | Recibir domicilios, para llevar y las solicitudes de reserva; anotar las que llegan por WhatsApp o teléfono; editar el horario y el contacto del sitio |
| `repartidor` | `/reparto` | Ver los domicilios que salieron a su nombre y confirmar la entrega |
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

## Modo demostración

Para enseñar el sistema hace falta lo contrario que para operarlo: seis cuentas
con una clave que quepa en la cabeza y que esté escrita en la pantalla, para
poder saltar de la comandera a la cocina y a la caja delante de quien mira.

Eso se enciende con **una sola variable en el servicio del backend**:

```
ELPATIO_CLAVE_DEMO=elpatio2026
```

Al arrancar, el backend deja estas seis cuentas listas —todas con esa misma
clave— y la pantalla de acceso las muestra en una lista donde se toca una fila
y el formulario se llena solo:

| Usuario | Rol | Lleva a |
|---|---|---|
| `mesero` | Mesero | Comandera |
| `mesero2` | Mesero | Comandera |
| `cocina` | Cocina | Pantalla de cocina |
| `recepcion` | Recepción | Domicilios, para llevar y reservas |
| `repartidor` | Repartidor | Sus entregas en la calle |
| `cajero` | Cajero | Caja y cierre |
| `admin` | Administrador | Panel completo |

### Los datos de prueba

La misma variable siembra un mes de servicio: ventas cerradas día por día,
mesas abiertas con platos en distintos momentos, pedidos esperando en
recepción, reservas y cierres de caja. Es lo que hace que el mapa, la pantalla
de cocina y los reportes tengan algo que enseñar en vez de estar en cero.

Cada fila sembrada lleva el identificador marcado con `demo_`, y de ahí sale la
propiedad que importa: **al quitar la variable y redesplegar, esas filas se
borran solas**. Lo que se haya creado durante la demostración —una comanda de
verdad, un pedido real— no lleva la marca y no se toca.

> Si la base ya traía pedidos de prueba de antes (los que se crearon probando
> el sitio a mano), esos **no** llevan la marca y se quedan. Se ven como mesas
> ocupadas hace horas y alertas de demora absurdas. Para dejar la base en
> blanco antes de la demostración, con el salón cerrado:
>
> ```bash
> psql "$DATABASE_URL" -c "delete from pagos; delete from ordenes; delete from reservas; delete from cierres_caja; update mesas set estado='libre', mesero_id=null, orden_activa_id=null;"
> ```
>
> Esto sí borra ventas: úselo solo mientras el sistema no esté operando de
> verdad.

Detalles que conviene saber antes de usarlo:

- **Las claves se reescriben en cada arranque.** Es a propósito: así el modo
  funciona igual sobre una base recién creada que sobre una que ya lleva
  semanas de uso, y nunca hay que ir a buscar qué clave quedó.
- **Pisa las cuentas que ya existan con esos nombres de acceso.** Si `admin` ya
  tenía una clave real, pasa a tener la de demostración.
- **No toca ningún otro dato.** Mesas, carta, ventas y comandas quedan igual.
- **Con la variable vacía o ausente el modo no existe:** el sembrador vuelve a
  generar claves al azar la primera vez, y `/api/acceso/demostracion` responde
  una lista vacía. Un despliegue no puede caer en demostración por descuido.

Para volver a producción: **quite la variable, redespliegue, y cámbiele la clave
a cada persona** desde `/admin/configuracion` → Personal. Mientras la variable
siga puesta, cualquiera que abra la pantalla de acceso ve las claves escritas.

### Si el celular sigue mostrando una versión vieja

La aplicación guarda su propio código para poder abrir sin WiFi. Cada
compilación estrena caché y borra la anterior, así que un despliegue nuevo llega
solo. Si un aparato quedó atascado en una versión anterior a este cambio, se
suelta una única vez borrando los datos del sitio en el navegador de ese
aparato, o abriéndolo en una pestaña de incógnito.

---

## Aparecer en Google

Lo que trae el sitio de fábrica:

| Qué | Dónde |
|---|---|
| Título y descripción por página | `src/compartido/seo.tsx` |
| Ficha del negocio para Google (JSON-LD `Restaurant`) | `index.html` |
| Dirección, coordenadas, teléfono y horario | `index.html` y `src/compartido/config.ts` |
| Vista previa al pegar el enlace en WhatsApp (Open Graph) | `index.html` |
| `robots.txt` | `public/robots.txt` |
| Ícono del navegador | `public/favicon.svg` |

Dos decisiones que conviene conocer:

- **Las pantallas del personal no se indexan.** `/acceso`, `/comandera`,
  `/cocina`, `/recepcion` y `/admin` salen con `noindex` y bloqueadas en
  `robots.txt`. No es solo que no le sirvan a nadie desde un buscador: llevan
  nombres de clientes, teléfonos y movimientos de caja.
- **La URL canónica se arma con el dominio desde el que se abre el sitio.** Así
  no hay un dominio escrito a mano que apunte al despliegue equivocado cuando se
  cambie de dirección.

### Lo que falta, y no es código

Nada de lo anterior mete el restaurante en el mapa. Para eso hacen falta tres
cosas que se hacen fuera del repositorio, y en este orden:

1. **Reclamar la ficha de Google Business.** Es la que pone el local en el mapa
   y en el recuadro lateral, y es de lejos lo que más pesa para «restaurante en
   Turbaco». La ficha del local ya existe en Maps; hay que reclamarla desde
   [business.google.com](https://business.google.com) con la dirección,
   `+57 304 403 2936`, el horario y fotos del sitio.
2. **Registrar el sitio en Google Search Console** y pedir la indexación de la
   portada. Sin esto, Google llega solo, pero tarda semanas.
3. **Un dominio propio.** Un `.up.railway.app` posiciona mal y se ve provisional
   cuando alguien lo lee en un resultado. Un `.com.co` cuesta poco y se apunta a
   Railway desde *Settings → Networking → Custom domain*.

Cuando haya dominio, descomente la línea `Sitemap:` de `public/robots.txt` con
la dirección real.

> **Falta la imagen de vista previa.** Al pegar el enlace en WhatsApp aparece el
> título y la descripción, pero sin foto: no hay `og:image`. Con una foto del
> local en `public/` y dos líneas en `index.html` queda completo.

---

## Por qué las pantallas no hay que recargarlas

El backend avisa por WebSocket cada vez que algo cambia, y la pantalla que
escucha ese aviso vuelve a pedir los datos. Por el canal no viaja nada del
negocio: solo el aviso de que hubo un movimiento y en qué área.

Ese canal es lo primero que se cae en un restaurante. Un punto de acceso que se
reinicia, un proxy que corta lo que lleva rato callado, un celular que suspende
la pestaña al bloquear la pantalla: el socket se muere sin avisar y **la
pantalla se queda congelada en la foto que tenía al abrirse**, sin ninguna señal
de que ya no es la verdad. La única salida era recargar a mano.

Por eso hay una red de seguridad debajo, en `useSyncedState`:

| Situación | Qué pasa |
|---|---|
| El canal está vivo | El aviso llega al instante. Además se revisa cada 60 s por si se perdió alguno |
| El canal está caído | Se vuelve a consultar cada 12 s |
| Se vuelve a la pestaña, o el aparato recupera el WiFi | Se consulta de inmediato |

En la práctica: **nunca hace falta recargar**, ni siquiera con el canal muerto.
Lo peor que puede pasar es que el cambio tarde unos segundos más en verse.

Si el canal no levanta, la consola del navegador lo dice: `se cayó el canal de
tiempo real`. Cuando eso pase en un despliegue, lo que hay que revisar es que
`ELPATIO_CORS_ORIGENES` traiga el dominio exacto del frontend —con `https://` y
sin barra final—, porque el mismo valor autoriza el WebSocket.

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

## Pedidos y reservas que llegan por WhatsApp

La mayoría de los pedidos y de las reservas no nacen en el formulario del sitio:
nacen en un WhatsApp o en una llamada. Antes se anotaban en una libreta del
mostrador y no existían para el sistema —cocina no los veía, la caja no los
sumaba, al cliente no había cómo avisarle—. Ahora se anotan dentro:

| Qué | Dónde | Quién |
|---|---|---|
| Pedido a domicilio o para llevar | `/recepcion` → **Nuevo pedido** | recepción, cajero, administrador |
| Reserva | `/recepcion/reservas` o `/admin/reservas` → **Nueva reserva** | recepción, cajero, administrador |

Cada uno guarda **por dónde pidió el cliente** (WhatsApp, teléfono o en el
mostrador) y esa etiqueta se ve en la tarjeta. Solo se pinta cuando no es del
sitio público: si saliera siempre, dejaría de significar algo.

Dos diferencias frente a la puerta pública, y las dos a propósito:

- **El pedido del mostrador no mira el horario ni la pausa del canal.** Esos dos
  frenos existen para que el sitio no acepte lo que la cocina no puede sacar;
  quien está en el mostrador ya tiene ese juicio delante y a veces decide
  recibir uno más con la persiana a medio bajar.
- **La reserva puede nacer confirmada**, con la mesa separada en el mismo gesto,
  porque quien la anota ya le dijo que sí al cliente. Si solo se está tomando
  nota, se guarda como solicitud y cae en «por responder» igual que las del
  sitio.

> El bot de WhatsApp **no volvió**. Esto es una persona escribiendo lo que le
> dictan; `canal` solo deja constancia de por dónde entró la conversación.

---

## Qué se edita sin desplegar

El horario de atención y los datos de contacto estaban escritos a mano en
`src/compartido/config.ts`: corregir un dígito del teléfono o publicar el
horario de una temporada costaba un despliegue. Ahora viven en la base —tablas
`ficha_sitio` y `franja_horario`— y se editan desde el panel:

| Qué | Dónde | Quién |
|---|---|---|
| Horario de atención, dirección, ciudad, teléfono, WhatsApp, Instagram | `/recepcion/ajustes` o `/admin/configuracion` | recepción, cajero, administrador |
| Pausar el canal, franja de domicilios, zonas y tarifas | `/recepcion/ajustes` o `/admin/configuracion` | recepción, cajero, administrador |
| Textos institucionales (quiénes somos, misión, visión) | `/recepcion/ajustes` o `/admin/institucional` | recepción, cajero, administrador |
| Impuesto al consumo, cuentas del personal, mesas | `/admin/configuracion` | solo administrador |

Recepción entra por su propia pestaña **Ajustes** y no por `/admin`: es quien
contesta «¿hasta qué hora abren hoy?» y quien primero se entera de que el
horario publicado quedó viejo, pero no tiene por qué ver las cuentas del
personal ni el impuesto al consumo.

Lo que sigue en `config.ts` son los **valores de reserva**: lo que se pinta
mientras el servidor contesta. Cambiarlos ahí no cambia lo que ve el cliente.
Las coordenadas del local también se quedan en el código a propósito: no
cambian, y una coordenada mal escrita desde un formulario manda al repartidor a
otro barrio.

---

## Las fotos de los platos

Cada plato lleva una **portada** —la que sale en el listado de la carta— y
tantas fotos más como se quiera. Se suben desde `/admin/carta`, entrando al
plato: son una sola lista y la primera manda. Para cambiar cuál va de portada
hay un botón en cada foto, y el cliente las ve todas al tocar el plato.

Lo mismo para la galería de la portada del sitio, en `/admin/publicaciones`
con tipo *galería*. Ahí el **título importa**: se lee encima de cada foto en el
mosaico y es lo que oye quien navega con lector de pantalla.

### Por dónde pasa una foto

1. Entra por `POST /api/carta/imagenes`. El tope de la petición son **25 MB**;
   más que eso no es una foto de plato.
2. Si hay Cloudinary configurado va allá; si no, al volumen del servidor.
3. **Cloudinary corta en 10 MB** las imágenes del plan gratuito. Una cámara
   entrega archivos de 12 a 18, así que por encima de ese peso el backend la
   encoge a 2600 px de lado mayor antes de subirla. Por debajo sube el original
   intacto, y entonces la CDN le sirve a cada dispositivo la versión que le
   toca (`f_auto,q_auto,w_600` en la propia dirección).
4. En el volumen en disco siempre se reduce, a 1600 px: ahí se sirve un solo
   archivo para todo el mundo.

> Si algún día el plan de Cloudinary acepta imágenes más grandes, el número a
> subir es `MAXIMO_DE_CLOUDINARY` en `AlmacenCloudinary`. Encogerlas no se nota
> en pantalla —2600 px es más de lo que muestra cualquier monitor— pero deja el
> archivo guardado a menor resolución de la que se pagó.

### Cuándo se borra una foto

Solo al **guardar el plato**: en ese momento se comparan las fotos que tenía
con las que quedan, y las que ya no están se borran del almacén. No hay forma
de borrar una imagen suelta, así que una foto subida y nunca asignada a un
plato se queda ahí ocupando espacio. Si hay que limpiarlas, se hace desde el
panel de Cloudinary, carpeta `elpatio`.

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

> Las coordenadas del local ya son las reales: `10.3390034, -75.4225372`,
> tomadas de la ficha del restaurante en Google Maps. Son el mismo par que usa
> el mapa del sitio público (`RESTAURANTE.coordenadas` en `config.ts`). Si el
> restaurante se muda, hay que cambiar los dos sitios: `ELPATIO_LATITUD` y
> `ELPATIO_LONGITUD` en el backend, y `config.ts` en el frontend.

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

El 5434 es el de La Carreta, el otro restaurante del dueño. Cada uno tiene su
puerto y su nombre de proyecto de Docker —`elpatio` y `lacarreta`— para poder
levantar los dos a la vez, que es lo que hace falta para probar el salto entre
los dos paneles.

### Construir la imagen del backend

El contexto es la **raíz del repositorio**, no `backend/`:

```bash
docker build -f backend/Dockerfile -t elpatio-backend .
```

Es lo que permite que Railway la construya apuntándole
`RAILWAY_DOCKERFILE_PATH=backend/Dockerfile`, sin configurar a mano el
directorio raíz del servicio.

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
