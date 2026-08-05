# footballay-data-quality-service

## 운영 환경변수

운영 환경에서는 `SPRING_PROFILES_ACTIVE=prod`를 설정한다.

필수 환경변수.

- `FOOTBALLAY_MONGODB_URI`.
- `AIVEN_KAFKA_BOOTSTRAP_SERVERS`.
- `AIVEN_KAFKA_CA_PATH`.
- `AIVEN_KAFKA_DQ_USERNAME`.
- `AIVEN_KAFKA_DQ_PASSWORD`.
- `FOOTBALLAY_DATA_QUALITY_RAW_S3_BUCKET`.

선택 환경변수와 기본값.

- `FOOTBALLAY_DATA_QUALITY_RAW_S3_REGION`: `ap-northeast-2`.
- `FOOTBALLAY_DATA_QUALITY_RAW_S3_ENDPOINT`: 비어 있으면 AWS S3 기본 endpoint를 사용한다.
- `FOOTBALLAY_DATA_QUALITY_RAW_S3_PATH_STYLE_ACCESS_ENABLED`: `false`.
- `FOOTBALLAY_DATA_QUALITY_FIXTURE_SINGLE_LINEUP_AVAILABILITY_NOT_STARTED_GRACE_MINUTES`: `3`.

prod 프로필에서는 `FOOTBALLAY_DATA_QUALITY_RAW_S3_ACCESS_KEY` 및
`FOOTBALLAY_DATA_QUALITY_RAW_S3_SECRET_KEY`를 사용하지 않는다. S3 인증은 AWS SDK의
`DefaultCredentialsProvider`가 제공하는 배포 환경의 credential source를 사용한다.

`AIVEN_KAFKA_CA_PATH`는 컨테이너 또는 실행 환경에 읽기 전용으로 mount한 Aiven CA PEM 파일을 가리켜야 한다.

## Production Docker 실행.

이미지는 Java 17 JRE runtime에서 UID/GID `10001`의 non-root 사용자로 실행한다. 운영 노트북은 `x86_64`이므로 Docker 이미지의 기본 build target은 Linux `amd64`이다.

`aws_signing_helper`는 AWS IAM Roles Anywhere 공식 Linux X86_64 artifact 1.8.4를 사용한다. 실제 helper binary를 `/usr/local/bin/aws_signing_helper`에 직접 설치하고 SHA-256 `b7568acd6e1517a4e1adaee68d52bfd6284a0e5305677166cd83d43a07c815c9`을 검증한다. 버전은 `aws_signing_helper version`으로 확인한다.

Gradle 테스트와 실행 JAR 생성은 GitHub Actions가 담당한다. Dockerfile은 CI artifact인 `build/docker/application.jar`와 `aws_signing_helper`만 runtime 이미지로 조립한다. Gradle 의존성 및 build cache는 `gradle/actions/setup-gradle@v4`와 Gradle의 `--build-cache`가 관리하고, Docker layer cache는 Buildx의 GitHub Actions cache(`type=gha`)가 관리한다. Dockerfile에는 Gradle build stage 또는 Gradle cache mount를 두지 않는다.

## GitHub Actions 배포 트리거.

`Build and Push Docker Image` workflow는 immutable GHCR 이미지를 push한 뒤 `footballay/footballay-data-quality-service-action` 저장소의 `deploy.yml` workflow를 `main` ref로 dispatch한다. 전달하는 `image` input은 push한 immutable 이미지 전체 주소다.

이 workflow를 실행하려면 이 저장소의 GitHub Actions repository secret으로 `DQ_DEPLOY_DISPATCH_TOKEN`을 설정한다. 토큰에는 대상 저장소의 workflow dispatch 권한이 필요하다.

로컬에서 Docker 이미지를 검증하려면 먼저 실행 가능한 non-plain bootJar를 고정 경로에 준비한다.

```sh
./gradlew test bootJar
mkdir -p build/docker
jar_file="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -print -quit)"
test -n "$jar_file"
cp "$jar_file" build/docker/application.jar
```

```sh
docker build --platform linux/amd64 \
  --build-arg AWS_SIGNING_HELPER_VERSION=1.8.4 \
  --build-arg AWS_SIGNING_HELPER_SHA256=b7568acd6e1517a4e1adaee68d52bfd6284a0e5305677166cd83d43a07c815c9 \
  --tag footballay-data-quality-service:local .
```

운영 시 AWS config, Roles Anywhere certificate/private key, Aiven CA는 이미지에 포함하지 않고 다음 경로로 read-only bind mount한다. host 파일은 컨테이너 UID `10001`이 읽을 수 있도록 권한 또는 그룹을 설정해야 한다.

- AWS config: `/run/secrets/aws/config`.
- Roles Anywhere certificate: `/run/secrets/aws/machine.crt`.
- Roles Anywhere private key: `/run/secrets/aws/machine.key`.
- Aiven CA PEM: `/run/secrets/aiven/ca.pem`.

AWS config의 `footballay-dq` profile은 `credential_process`에서 `/usr/local/bin/aws_signing_helper credential-process`를 사용하고, certificate 및 private key의 위 mount 경로를 참조해야 한다. `AWS_SHARED_CREDENTIALS_FILE`은 설정하지 않는다.

```sh
docker run --detach \
  --name footballay-data-quality-service \
  --restart unless-stopped \
  --env-file ./deployment/prod.env \
  --env SPRING_PROFILES_ACTIVE=prod \
  --env AWS_CONFIG_FILE=/run/secrets/aws/config \
  --env AWS_PROFILE=footballay-dq \
  --env AIVEN_KAFKA_CA_PATH=/run/secrets/aiven/ca.pem \
  --mount type=bind,src="${AWS_CONFIG_HOST_PATH:?set AWS_CONFIG_HOST_PATH}",dst=/run/secrets/aws/config,readonly \
  --mount type=bind,src="${AWS_CERTIFICATE_HOST_PATH:?set AWS_CERTIFICATE_HOST_PATH}",dst=/run/secrets/aws/machine.crt,readonly \
  --mount type=bind,src="${AWS_PRIVATE_KEY_HOST_PATH:?set AWS_PRIVATE_KEY_HOST_PATH}",dst=/run/secrets/aws/machine.key,readonly \
  --mount type=bind,src="${AIVEN_CA_HOST_PATH:?set AIVEN_CA_HOST_PATH}",dst=/run/secrets/aiven/ca.pem,readonly \
  --tmpfs /tmp:rw,noexec,nosuid,size=64m \
  footballay-data-quality-service:local
```

`deployment/prod.env`에는 MongoDB, Aiven Kafka, S3 bucket 등 앞서 열거한 필수 환경변수를 넣되 저장소에 commit하지 않는다. 애플리케이션은 외부 공개 HTTP port가 없으므로 `--publish` 또는 `EXPOSE`가 필요 없다. 컨테이너 파일시스템 전체 `--read-only`는 JVM과 Spring의 쓰기 경로를 별도로 검증하기 전에는 적용하지 않는다. 위 예시의 `/tmp` tmpfs는 임시 파일이 필요한 경우를 위한 선택 사항이다.
