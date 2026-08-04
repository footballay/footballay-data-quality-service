# syntax=docker/dockerfile:1

ARG IMAGE_PLATFORM=linux/amd64

FROM --platform=${IMAGE_PLATFORM} amazonlinux:2023 AS aws-signing-helper

ARG AWS_SIGNING_HELPER_VERSION=1.8.4
ARG AWS_SIGNING_HELPER_SHA256=b7568acd6e1517a4e1adaee68d52bfd6284a0e5305677166cd83d43a07c815c9

RUN curl --fail --location --silent --show-error \
        "https://rolesanywhere.amazonaws.com/releases/${AWS_SIGNING_HELPER_VERSION}/X86_64/Linux/Amzn2023/aws_signing_helper" \
        --output /usr/local/bin/aws_signing_helper \
    && echo "${AWS_SIGNING_HELPER_SHA256}  /usr/local/bin/aws_signing_helper" | sha256sum --check \
    && chmod 0755 /usr/local/bin/aws_signing_helper

FROM --platform=${IMAGE_PLATFORM} eclipse-temurin:17-jre-noble AS runtime

RUN groupadd --system --gid 10001 footballay \
    && useradd --system --uid 10001 --gid footballay --home-dir /app --shell /usr/sbin/nologin footballay \
    && mkdir -p /app \
    && chown footballay:footballay /app

WORKDIR /app

COPY --from=aws-signing-helper /usr/local/bin/aws_signing_helper /usr/local/bin/aws_signing_helper

USER footballay

COPY build/docker/application.jar /app/application.jar

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
