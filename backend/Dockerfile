# Imagen del backend.
#
# Se usa Dockerfile y no la detección automática de Nixpacks para que la
# versión de Java quede escrita y no dependa de lo que el constructor adivine:
# el proyecto compila contra 21 y un salto silencioso a otra versión es la clase
# de sorpresa que aparece en el despliegue y no en la máquina de nadie.
#
# OJO CON EL CONTEXTO: las rutas son relativas a la RAÍZ del repositorio, no a
# esta carpeta. Es lo que permite que Railway lo construya con solo apuntarle
# RAILWAY_DOCKERFILE_PATH=backend/Dockerfile, sin tener que configurar a mano el
# directorio raíz del servicio en el panel.
#
#   docker build -f backend/Dockerfile .        <- desde la raíz del repo

# ---------------------------------------------------------------------------
# Compilación
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS construccion
WORKDIR /obra

# Las dependencias se resuelven en una capa aparte: mientras el pom no cambie,
# Docker reutiliza la caché y no vuelve a bajar medio Maven Central en cada
# despliegue.
COPY backend/pom.xml .
RUN mvn -B -q dependency:go-offline

COPY backend/src ./src
RUN mvn -B -q clean package -DskipTests

# ---------------------------------------------------------------------------
# Ejecución
# ---------------------------------------------------------------------------
# JRE y no JDK: la imagen final no necesita compilador, y quitarlo reduce el
# tamaño y la superficie expuesta.
FROM eclipse-temurin:21-jre AS ejecucion
WORKDIR /aplicacion

# No se corre como root. Si algún día alguien encuentra cómo ejecutar código en
# el proceso, que no sea con todos los permisos del contenedor.
RUN groupadd --system elpatio && useradd --system --gid elpatio elpatio

COPY --from=construccion /obra/target/*.jar aplicacion.jar

# Las carpetas de datos se crean AQUÍ y se le dan al usuario que ejecuta.
#
# Sin esto la aplicación no arranca: `/aplicacion` es de root, el proceso corre
# como `elpatio`, y el almacén de documentos crea su carpeta al construirse. La
# excepción sube durante el arranque de Spring y el contenedor entra en ciclo de
# reinicio, con un error que no menciona los permisos por ningún lado.
#
# EN RAILWAY, EL VOLUMEN SE MONTA EN /aplicacion/datos. Si se monta en otra
# ruta, hay que apuntar ahí ELPATIO_RUTA_DOCUMENTOS, ELPATIO_RUTA_IMAGENES y
# ELPATIO_ERP_CARPETA.
#
# Este chown NO alcanza para el volumen: el montaje ocurre DESPUÉS de
# construir la imagen y llega con sus propios permisos, encima de esta
# carpeta. De eso se encarga `arranque.sh`, que corrige el dueño ya montado y
# baja privilegios antes de arrancar la máquina virtual.
RUN mkdir -p /aplicacion/datos/documentos \
             /aplicacion/datos/imagenes \
             /aplicacion/datos/erp \
    && chown -R elpatio:elpatio /aplicacion

# NO hay `USER elpatio` aquí, y es deliberado.
#
# El contenedor entra como root para poder ajustar el dueño del volumen
# montado, y `arranque.sh` cambia a `elpatio` con `setpriv` antes de ejecutar
# Java. Root vive unos milisegundos y solo para un `chown`; el proceso que
# atiende peticiones nunca tiene privilegios, que es lo que de verdad importa.
COPY backend/arranque.sh arranque.sh
RUN chmod +x arranque.sh

# Railway inyecta PORT; el application.yml ya lo lee.
EXPOSE 8080

# `MaxRAMPercentage` en vez de un -Xmx fijo: el contenedor puede cambiar de
# tamaño en Railway y la JVM debe acomodarse sin que nadie edite esto.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

ENTRYPOINT ["./arranque.sh"]
