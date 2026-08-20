# Backend — Restaurante El Patio

API y canal de tiempo real del sistema de sala. Spring Boot 3.3, Java 21,
PostgreSQL con Flyway.

El despliegue en Railway, el respaldo de la base y la creación de usuarios en
producción se documentan en el README de la raíz cuando se ejecute la fase 4.

## Arquitectura

```
dominio/          Las reglas del negocio. No conoce Spring, JPA ni HTTP.
  comanda/          Orden es el agregado: aquí viven las reglas de la comanda.
  cobro/            CalculadoraCuenta: INC, propina y división de la cuenta.
  puertos/          Interfaces de lo que el dominio necesita del exterior.
aplicacion/       Orquesta casos de uso y traduce a DTOs. Sin reglas propias.
infraestructura/  Todo lo que se puede reemplazar sin tocar el negocio.
  persistencia/     Entidades JPA y adaptadores de los puertos.
  web/              Controladores REST.
  seguridad/        JWT, BCrypt y las reglas de acceso por área.
  tiemporeal/       WebSocket con STOMP.
```

La regla que lo sostiene: si una decisión de negocio aparece en un controlador
o en un servicio, es que le faltaba sitio en `dominio/`.

## Correr en local

Hace falta JDK 21 y Docker.

```bash
# 1. La base
docker compose up -d

# 2. Las variables
cp .env.ejemplo .env    # y edite ELPATIO_JWT_SECRETO

# 3. El backend
JAVA_HOME=/ruta/al/jdk-21 \
DATABASE_URL=jdbc:postgresql://localhost:5433/elpatio \
DATABASE_USUARIO=elpatio DATABASE_CLAVE=elpatio \
ELPATIO_JWT_SECRETO="$(openssl rand -base64 48)" \
ELPATIO_CORS_ORIGENES=http://localhost:5173 \
mvn spring-boot:run
```

Queda escuchando en `http://localhost:8080`. El pulso está en `/salud`.

> El contenedor publica PostgreSQL en el **5433** y no en el 5432 porque muchas
> máquinas ya tienen un PostgreSQL propio en el puerto estándar. Si los dos
> compiten, el error que aparece es «autenticación fallida», que no dice nada
> sobre el conflicto.

## Las claves del personal

La primera vez que arranca contra una base vacía se crea el personal con claves
**aleatorias**, y se imprimen una sola vez en la salida estándar:

```
===========================================================
  PERSONAL CREADO — ESTAS CLAVES NO SE VUELVEN A MOSTRAR
===========================================================
  mesero         mesero     5XXR35DTJ8
  ...
```

Anótelas en ese momento. No se guardan en claro en ninguna parte: la base solo
tiene el hash BCrypt. Si se pierden, se cambian desde `/admin/configuracion` con
un administrador, o se borran las filas de `usuarios` para que el sembrador
vuelva a correr.

Si ya hay usuarios, el sembrador no hace nada: el arranque es repetible y no
pisa las claves que alguien ya cambió.

## Pruebas

```bash
mvn test
```

Las de `CalculadoraCuentaTest` son las que importan: verifican el INC del 8 %,
la propina voluntaria y la división de la cuenta contra los mismos casos que
resolvía `src/compartido/calculos.ts`. Si un cambio las rompe, el total que ve
el cliente dejó de ser el que era.

## Migraciones

Están en `src/main/resources/db/migration` y las corre Flyway al arrancar.
Hibernate está en `ddl-auto: validate`: no toca el esquema, solo comprueba que
coincide con las entidades. Tener dos fuentes de verdad del esquema termina en
una diferencia que se descubre en producción.

`V2__semilla_salon_y_carta.sql` se generó desde `src/compartido/datosSemilla.ts`,
que era la fuente de verdad del prototipo. No lleva usuarios ni claves.

## Notas del API

- Una ruta por cada función exportada de `src/compartido/mockApi.ts`, con la
  misma firma y el mismo tipo de retorno. La única operación añadida es
  `POST /api/comandera/ordenes/{id}/anular`, que no existía y sin la cual la
  regla «una comanda cobrada no se edita» no tendría cómo ejecutarse.
- Los errores salen siempre como `{ "mensaje": "..." }`. Lo que llegue en
  `mensaje` es lo que el mesero ve en pantalla, así que está en español.
- `409` es una regla del negocio que dijo que no; `404`, algo que ya no existe;
  `403`, un rol que no alcanza; `401`, sesión ausente o vencida.
- Las fechas viajan en ISO-8601. Los montos son enteros en pesos: el peso
  colombiano no tiene centavos.

## Tiempo real

WebSocket STOMP en `/ws`, con cuatro tópicos: `/topic/comandas`, `/topic/mesas`,
`/topic/pedidos` (se llena en la fase 3) y `/topic/general` para lo demás.

Por el canal **no viaja ningún dato del negocio**: el evento solo dice que algo
cambió y en qué área, y la pantalla que lo recibe vuelve a pedir los datos por
el API, que sí exige token. Por eso el handshake no pide credencial. Si algún
día el evento empieza a llevar la comanda adentro, esa decisión deja de valer.
