# Data Quality Flow Frame

## 목적

`footballay-data-quality-service`의 최소 실행 흐름을 제공한다.

```text
football-data-raw-collected consume
  -> rawEventId 중복 확인
  -> raw response gzip read
  -> quality check
  -> result persist
  -> football-data-quality-result publish
```

입출력 Kafka event의 source of truth는 core의 `plan/data-quality-check/data-model` 문서다. 현재 core producer가 계약 외 필드를 추가로 전송하더라도 consumer는 이를 무시하고 계약 필드만 사용한다.

## 현재 구현

* Kafka listener는 기본 비활성이다. `footballay.data-quality.pipeline.enabled=true`에서만 consume한다.
* 초기 idempotency checker는 NoOp이며 항상 처리하도록 반환한다.
* raw response reader는 아래 두 구현만 둔다.
  - NoOp: 빈 JSON object 반환.
  - MockData: `src/main/resources/mock/20260712T033403Z_Iq_XxagJ8LaiRo1Fy1M4oYRNxUhVIp8aOn9jJCmAGWY.json.gz`를 읽고 gzip 해제.
* quality checker는 AlwaysHappy이며 항상 issue 없는 결과를 반환한다.
  `scannerVersion=always-happy-v1`, `hasIssue=false`, `issueCount=0`, `maxSeverity=NONE`, `checkStatus=NO_ISSUE`를 사용한다.
* result store는 MongoDB type에 의존하지 않는 interface이며, 초기 NoOp 구현은 입력 result를 성공적으로 저장한 것처럼 반환한다.
* result ID는 검증된 `ulid-creator` 라이브러리의 monotonic ULID로 생성하고, `resultId`를 Kafka key와 quality-result event의 논리 식별자로 사용한다.
* producer는 동기적으로 broker ack를 기다린다. listener 점유 시간과 비동기 처리 전환은 이후 검토한다.

## 제외 범위

* MongoDB adapter 및 실제 `rawEventId` 중복 저장소.
* LocalFile reader, URL downloader, cloud storage reader.
* 실제 ApiSports 데이터 품질 규칙.
* outbox, DLT, 복잡한 retry orchestration, 비동기 worker 분리.

## 이후 구현 원칙

실제 idempotency 구현은 `rawEventId`를 영속적으로 조회해 중복 event를 건너뛰도록 한다. 의도적 재검사는 새 `rawEventId`를 가진 event로 표현한다.

실제 persistence 구현은 동일 interface 뒤에 MongoDB adapter를 추가한다. `quality_results` schema, enum, index는 core의 `mongodb-document-quality_results.md`와 `QualityResultMongoIndexDefinitions`를 그대로 따른다.

reader 추가 구현은 기존 reader interface에 LocalFile 또는 URL downloader를 추가한다. 검사 규칙이 복잡해질 때만 AlwaysHappy 내부를 endpoint/rule 단위로 분리한다.

## 검증

* MockData gzip fixture 해제 단위 테스트.
* NoOp pipeline 순서와 no-issue result 생성 단위 테스트.
* idempotency checker가 처리 거부 시 이후 단계를 실행하지 않는 단위 테스트.
* Testcontainers Kafka 통합 테스트: raw-collected input이 no-issue quality-result output으로 이어지는지 확인.
* 기본 Spring context에서 Kafka listener가 비활성 상태로 시작하는지 확인.
