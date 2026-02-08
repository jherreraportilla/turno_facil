# ============================
# Stage 1: Build con Maven
# ============================
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copiar solo pom.xml primero para cachear dependencias
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiar codigo fuente y compilar
COPY src ./src
RUN mvn package -DskipTests -B

# ============================
# Stage 2: Runtime ligero
# ============================
FROM eclipse-temurin:17-jre

WORKDIR /app

# Usuario no-root para seguridad
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Copiar JAR desde stage de build
COPY --from=build /app/target/*.jar app.jar

# Cambiar a usuario no-root
RUN chown appuser:appuser app.jar
USER appuser

# Puerto de la aplicacion
EXPOSE 8080

# Health check usando actuator
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Opciones JVM optimizadas para contenedores
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
