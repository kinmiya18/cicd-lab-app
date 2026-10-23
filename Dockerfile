FROM eclipse-temurin:17-jre
WORKDIR /app

# Jenkins builds and tests the jar first, then copies it into the image.
COPY target/demo-app-*.jar app.jar

ARG GIT_SHA=dev
ENV APP_VERSION=${GIT_SHA}

RUN useradd --system --no-create-home appuser
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
