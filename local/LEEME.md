# El sistema dentro del restaurante

Esto instala El Patio en una máquina del local en vez de en la nube, para que
**el salón siga trabajando cuando se cae el internet**.

Mientras la WiFi del restaurante esté viva, las tablets tienen con quién hablar:
el servidor está en el mismo edificio y ninguna petición sale a la calle.

---

## Qué sigue funcionando sin internet y qué no

| | Sin internet |
|---|---|
| Abrir mesas, tomar y enviar comandas | **Sí** |
| Pantalla de cocina y de barra | **Sí** |
| Cobrar, imprimir, cerrar caja | **Sí** |
| Reservas y pedidos ya recibidos | **Sí** |
| Reportes, carta, personal, configuración | **Sí** |
| Enlaces de WhatsApp al cliente | No |
| Reservas y pedidos **nuevos** desde el sitio público | No — entran al volver la señal |
| Facturación electrónica ante la DIAN | No — se acumula y sale después |

Lo que se pierde es lo que por naturaleza vive en la calle. El salón, que es lo
que no puede parar, queda entero.

> **Ojo:** el sitio público (`restauranteelpatio.com`) sigue en la nube con su
> propia base. Una reserva que un cliente manda desde la web **no aparece sola**
> en el servidor del local: eso lo resuelve el puente entre los dos, que es un
> trabajo aparte y todavía no está hecho.

---

## Qué hace falta

- Una máquina encendida en el restaurante, conectada por **cable** al router.
  No hace falta que sea potente: sirve un mini PC. Si ya hay una máquina
  encendida —la del ERP, por ejemplo— puede ser la misma.
- **Docker** instalado.
- Que esa máquina tenga **IP fija** en la red del local. Se configura en el
  router, buscando «reserva de DHCP» o «IP estática», y se le asigna una
  dirección que no cambie, por ejemplo `192.168.1.50`.

---

## Instalación

Desde la raíz del repositorio, en la máquina del restaurante:

```bash
# 1. Los secretos de esta instalación
cp local/.env.ejemplo local/.env
```

Abra `local/.env` y rellene los dos campos:

```bash
DATABASE_CLAVE=          # invéntela larga; nadie la teclea nunca
ELPATIO_JWT_SECRETO=     # openssl rand -base64 48
```

```bash
# 2. Levantarlo
docker compose -f local/docker-compose.yml --env-file local/.env up -d --build
```

La primera vez tarda unos minutos: compila el backend y la aplicación. Flyway
crea las tablas solo, no hay que correr ninguna migración a mano.

```bash
# 3. Comprobar que quedó vivo
curl http://localhost/salud
```

---

## Cómo entran las tablets

Se abre el navegador en **`http://LA-IP-DEL-EQUIPO/`** —por ejemplo
`http://192.168.1.50/`— y se guarda como marcador o como acceso en la pantalla
de inicio.

No hay que escribir ningún puerto ni ninguna dirección dentro de la aplicación:
el mismo servidor entrega la pantalla, el API y el canal de tiempo real, todo
por el puerto 80. Por eso **cambiar la IP del equipo no obliga a recompilar
nada**; solo a escribir la nueva en las tablets.

### Una advertencia sobre el «sin señal» de la tablet

En la nube la aplicación va por HTTPS y el navegador le permite guardar su
esqueleto para abrir aunque no haya WiFi. En la red local va por HTTP simple, y
el navegador no lo permite: son las reglas de contexto seguro y no hay forma de
esquivarlas sin certificados.

En la práctica da igual: si la tablet se queda sin WiFi tampoco alcanza el
servidor del local, que es lo que necesita para trabajar. La red del
restaurante es la que tiene que estar sana, no el internet.

---

## Operación

```bash
# Ver que está corriendo
docker compose -f local/docker-compose.yml ps

# Bitácora del backend
docker compose -f local/docker-compose.yml logs -f backend

# Apagar
docker compose -f local/docker-compose.yml down

# Actualizar a la última versión del sistema
git pull
docker compose -f local/docker-compose.yml --env-file local/.env up -d --build
```

### Respaldos

Los datos viven en dos volúmenes de Docker: `elpatio-local_datos-base` (la base)
y `elpatio-local_datos-aplicacion` (documentos, imágenes de la carta y la
carpeta del ERP). **Los dos hay que respaldarlos**, y un respaldo que nadie ha
restaurado nunca no es un respaldo.

```bash
# Copia de la base, con la fecha en el nombre
docker exec elpatio-local-base pg_dump -U elpatio elpatio > respaldo-$(date +%F).sql
```

---

## Seguridad

La base **no publica ningún puerto**: solo la alcanza el backend por la red
interna de Docker. Un PostgreSQL escuchando en la red del restaurante es una
puerta que nadie vigila.

El único puerto abierto es el 80, y solo debe estarlo hacia la red del local.
**No abra ese puerto en el router hacia internet.** Si algún día hace falta
entrar desde fuera, se hace con una VPN, no publicando el puerto.
