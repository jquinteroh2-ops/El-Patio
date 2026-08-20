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
RUN chown elpatio:elpatio aplicacion.jar
USER elpatio

# Railway inyecta PORT; el application.yml ya lo lee.
EXPOSE 8080

# `MaxRAMPercentage` en vez de un -Xmx fijo: el contenedor puede cambiar de
# tamaño en Railway y la JVM debe acomodarse sin que nadie edite esto.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar aplicacion.jar"]
