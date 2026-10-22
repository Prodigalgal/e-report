ARG GRADLE_BASE_IMAGE=docker.m.daocloud.io/library/gradle:9.6.1-jdk25-alpine@sha256:4d50266538c871adbcd8ece20d61d10e42b6c8c7dec1f10498d9508d45759c35
ARG TEMURIN_BASE_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:25-jre-alpine@sha256:3c0a9084927a221ccd1d007fcaf614465672c0af37aaa834c5184483afe56d61
ARG ALPINE_REPOSITORY=https://mirrors.aliyun.com/alpine

FROM ${GRADLE_BASE_IMAGE} AS builder
ARG ALPINE_REPOSITORY
RUN sed -i "s#https://dl-cdn.alpinelinux.org/alpine#${ALPINE_REPOSITORY}#g" /etc/apk/repositories
RUN apk upgrade --no-cache
WORKDIR /app
COPY gradlew .
RUN chmod +x gradlew
COPY gradle gradle

COPY build.gradle .
COPY settings.gradle .
RUN ./gradlew dependencies --no-daemon >/dev/null
COPY src src
RUN ./gradlew bootJar --no-daemon

FROM ${TEMURIN_BASE_IMAGE}
ARG ALPINE_REPOSITORY
RUN sed -i "s#https://dl-cdn.alpinelinux.org/alpine#${ALPINE_REPOSITORY}#g" /etc/apk/repositories
RUN apk upgrade --no-cache
ARG APP_NAME=e-report-backen
ARG APP_VERSION=6.9.1
ARG JAR_FILE_PATH_IN_BUILDER=/app/build/libs/${APP_NAME}-${APP_VERSION}.jar
WORKDIR /app
COPY --from=builder ${JAR_FILE_PATH_IN_BUILDER} app.jar
EXPOSE 8083
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE}", "-jar", "/app/app.jar"]
