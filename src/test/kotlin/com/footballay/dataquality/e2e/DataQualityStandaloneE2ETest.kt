package com.footballay.dataquality.e2e

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.footballay.dataquality.pipeline.input.DataProvider
import com.footballay.dataquality.pipeline.input.QualityParameter
import com.footballay.dataquality.pipeline.input.RawCollectedEvent
import com.footballay.dataquality.pipeline.result.QualityCheckStatus
import com.footballay.dataquality.pipeline.result.QualityIssueCheckStatus
import com.footballay.dataquality.pipeline.result.QualityIssueSeverity
import com.footballay.dataquality.pipeline.result.QualityMaxSeverity
import com.footballay.dataquality.pipeline.result.QualityResultEvent
import com.footballay.dataquality.pipeline.store.mongo.QualityResultArchiveStatus
import com.footballay.dataquality.pipeline.store.mongo.QualityResultDocument
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Properties
import java.util.UUID
import java.util.zip.GZIPOutputStream

/** Kafka 수신부터 Mongo 저장과 결과 Kafka 발행까지 실제 DQ 서비스 흐름을 검증한다. */
@Testcontainers
@SpringBootTest(
    properties = [
        "footballay.data-quality.pipeline.enabled=true",
        "footballay.data-quality.pipeline.reader.type=s3",
        "footballay.data-quality.pipeline.store.type=mongo",
    ],
)
class DataQualityStandaloneE2ETest {
    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    @Autowired
    private lateinit var kafkaListenerEndpointRegistry: KafkaListenerEndpointRegistry

    @BeforeEach
    fun clearQualityResults() {
        ContainerTestUtils.waitForAssignment(kafkaListenerEndpointRegistry.listenerContainers.single(), 1)
        mongoTemplate.getCollection(COLLECTION_NAME).deleteMany(org.bson.Document())
    }

    @Test
    fun `stores and publishes a result without issues from a local gzip fixture`() {
        val event = event(rawEventId = "01K1E2E0000000000000000001", objectKey = NO_ISSUE_OBJECT_KEY)
        writeFixture(NO_ISSUE_OBJECT_KEY)

        val resultEvent = publishAndConsume(event)
        val resultDocument = requireNotNull(mongoTemplate.findById(resultEvent.resultId, QualityResultDocument::class.java))

        assertThat(
            mongoTemplate.getCollection(COLLECTION_NAME).countDocuments(),
        ).isEqualTo(1)

        assertThat(resultDocument.rawEventId).isEqualTo(event.rawEventId)
        assertThat(resultDocument.collectedAt).isEqualTo(event.collectedAt)
        assertThat(resultDocument.inspectionVersion).isEqualTo(1)
        assertThat(resultDocument.issues).isEmpty()
        assertThat(resultDocument.hasIssue).isFalse()
        assertThat(resultDocument.issueCount).isZero()
        assertThat(resultDocument.maxSeverity).isEqualTo(QualityMaxSeverity.NONE)
        assertThat(resultDocument.checkStatus).isEqualTo(QualityCheckStatus.NO_ISSUE)
        assertDefaultResultAudit(resultDocument)

        assertThat(resultEvent.rawEventId).isEqualTo(event.rawEventId)
        assertThat(resultEvent.source.provider).isEqualTo(event.provider)
        assertThat(resultEvent.source.endpointKey).isEqualTo(event.endpointKey)
        assertThat(resultEvent.source.parameters).isEqualTo(event.parameters)
        assertThat(resultEvent.rawJsonObjectKey).isEqualTo(event.rawJsonObjectKey)
        assertThat(resultEvent.hasIssue).isFalse()
        assertThat(resultEvent.issueCount).isZero()
        assertThat(resultEvent.maxSeverity).isEqualTo(QualityMaxSeverity.NONE)
        assertThat(resultEvent.checkedAt.truncatedTo(ChronoUnit.MILLIS)).isEqualTo(resultDocument.checkedAt)
    }

    @Test
    fun `stores and publishes the venue identifier issue from a local gzip fixture`() {
        val event = event(rawEventId = "01K1E2E0000000000000000002", objectKey = VENUE_ID_ISSUE_OBJECT_KEY)
        writeFixture(VENUE_ID_ISSUE_OBJECT_KEY) { root ->
            ((root.path("response")[0].path("fixture").path("venue")) as ObjectNode).put("id", 0)
        }

        val resultEvent = publishAndConsume(event)
        val resultDocument = requireNotNull(mongoTemplate.findById(resultEvent.resultId, QualityResultDocument::class.java))
        val issue = resultDocument.issues.single()

        assertThat(
            mongoTemplate.getCollection(COLLECTION_NAME).countDocuments(),
        ).isEqualTo(1)

        assertThat(resultDocument.rawEventId).isEqualTo(event.rawEventId)
        assertThat(resultDocument.collectedAt).isEqualTo(event.collectedAt)
        assertThat(resultDocument.inspectionVersion).isEqualTo(1)
        assertThat(resultDocument.hasIssue).isTrue()
        assertThat(resultDocument.issueCount).isEqualTo(1)
        assertThat(resultDocument.maxSeverity).isEqualTo(QualityMaxSeverity.WARN)
        assertThat(resultDocument.checkStatus).isEqualTo(QualityCheckStatus.NEED_CHECK)
        assertThat(issue.inspectionCode).isEqualTo("APISPORTS:FIXTURE_SINGLE:FIXTURE:VENUE_ID_NON_POSITIVE")
        assertThat(issue.severity).isEqualTo(QualityIssueSeverity.WARN)
        assertThat(issue.checkStatus).isEqualTo(QualityIssueCheckStatus.NEED_CHECK)
        assertThat(issue.evidence?.values).containsExactlyEntriesOf(mapOf("venueId" to "0"))
        assertThat(issue.createdAt).isEqualTo(resultDocument.checkedAt)
        assertThat(issue.updatedAt).isEqualTo(resultDocument.checkedAt)
        assertDefaultResultAudit(resultDocument)

        assertThat(resultEvent.rawEventId).isEqualTo(event.rawEventId)
        assertThat(resultEvent.rawJsonObjectKey).isEqualTo(event.rawJsonObjectKey)
        assertThat(resultEvent.hasIssue).isTrue()
        assertThat(resultEvent.issueCount).isEqualTo(resultDocument.issueCount)
        assertThat(resultEvent.maxSeverity).isEqualTo(resultDocument.maxSeverity)
        assertThat(resultEvent.checkedAt.truncatedTo(ChronoUnit.MILLIS)).isEqualTo(resultDocument.checkedAt)
    }

    @Test
    fun `does not store or publish a completed raw event twice`() {
        val event = event(rawEventId = "01K1E2E0000000000000000003", objectKey = NO_ISSUE_OBJECT_KEY)
        writeFixture(NO_ISSUE_OBJECT_KEY)

        outputConsumer().use { consumer ->
            consumer.subscribe(listOf(OUTPUT_TOPIC))
            awaitAssignment(consumer)

            send(event)
            pollOutputRecord(consumer, event.rawEventId)

            send(event)
            assertNoOutputRecord(consumer, event.rawEventId, DUPLICATE_RESULT_WAIT)
        }

        assertThat(mongoTemplate.getCollection(COLLECTION_NAME).countDocuments()).isEqualTo(1)
    }

    @Test
    fun `reprocesses a raw event after local file reading fails`() {
        val event = event(rawEventId = "01K1E2E0000000000000000004", objectKey = RETRY_OBJECT_KEY)

        outputConsumer().use { consumer ->
            consumer.subscribe(listOf(OUTPUT_TOPIC))
            awaitAssignment(consumer)

            send(event)
            assertNoOutputRecord(consumer, event.rawEventId, FAILURE_RESULT_WAIT)

            writeFixture(RETRY_OBJECT_KEY)
            send(event)
            val resultEvent = pollOutputRecord(consumer, event.rawEventId)

            assertThat(objectMapper.readValue(resultEvent.value(), QualityResultEvent::class.java).rawEventId)
                .isEqualTo(event.rawEventId)
        }

        assertThat(mongoTemplate.getCollection(COLLECTION_NAME).countDocuments()).isEqualTo(1)
    }

    private fun publishAndConsume(event: RawCollectedEvent): QualityResultEvent =
        outputConsumer().use { consumer ->
            consumer.subscribe(listOf(OUTPUT_TOPIC))
            awaitAssignment(consumer)

            send(event)

            val outputRecord = pollOutputRecord(consumer, event.rawEventId)
            assertThat(outputRecord.key()).isNotBlank()
            objectMapper.readValue(outputRecord.value(), QualityResultEvent::class.java)
        }

    private fun send(event: RawCollectedEvent) {
        kafkaTemplate.send(INPUT_TOPIC, event.rawEventId, objectMapper.writeValueAsString(event)).get()
    }

    private fun outputConsumer(): KafkaConsumer<String, String> {
        val properties =
            Properties().apply {
                put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.bootstrapServers)
                put(ConsumerConfig.GROUP_ID_CONFIG, "data-quality-e2e-output-${UUID.randomUUID()}")
                put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
                put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
                put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
                put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
            }
        return KafkaConsumer(properties)
    }

    private fun awaitAssignment(consumer: KafkaConsumer<String, String>) {
        val deadline = System.nanoTime() + ASSIGNMENT_TIMEOUT.toNanos()
        while (consumer.assignment().isEmpty() && System.nanoTime() < deadline) {
            consumer.poll(POLL_DURATION)
        }
        check(consumer.assignment().isNotEmpty()) { "Output consumer was not assigned to $OUTPUT_TOPIC" }
    }

    private fun pollOutputRecord(
        consumer: KafkaConsumer<String, String>,
        rawEventId: String,
    ): ConsumerRecord<String, String> {
        val deadline = System.nanoTime() + RESULT_TIMEOUT.toNanos()
        while (System.nanoTime() < deadline) {
            val matchingRecord =
                consumer.poll(POLL_DURATION).records(OUTPUT_TOPIC).firstOrNull { record ->
                    objectMapper.readTree(record.value()).path("rawEventId").asText() == rawEventId
                }
            if (matchingRecord != null) {
                return matchingRecord
            }
        }
        error("No quality-result event was published for rawEventId=$rawEventId")
    }

    private fun assertNoOutputRecord(
        consumer: KafkaConsumer<String, String>,
        rawEventId: String,
        timeout: Duration,
    ) {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            val matchingRecord =
                consumer.poll(POLL_DURATION).records(OUTPUT_TOPIC).firstOrNull { record ->
                    objectMapper.readTree(record.value()).path("rawEventId").asText() == rawEventId
                }
            check(matchingRecord == null) { "Unexpected quality-result event for rawEventId=$rawEventId" }
        }
    }

    private fun writeFixture(
        objectKey: String,
        mutate: (ObjectNode) -> Unit = {},
    ) {
        val root =
            ClassPathResource(NO_ISSUE_FIXTURE_RESOURCE).inputStream.use { input ->
                objectMapper.readTree(input) as ObjectNode
            }
        mutate(root)

        val gzipBytes =
            ByteArrayOutputStream().use { output ->
                GZIPOutputStream(output).use { gzip -> objectMapper.writeValue(gzip, root) }
                output.toByteArray()
            }
        s3Client().putObject(
            PutObjectRequest.builder().bucket(MINIO_BUCKET).key(objectKey).build(),
            RequestBody.fromBytes(gzipBytes),
        )
    }

    private fun event(
        rawEventId: String,
        objectKey: String,
    ): RawCollectedEvent =
        RawCollectedEvent(
            rawEventId = rawEventId,
            provider = DataProvider.API_SPORTS,
            endpointKey = "fixtureSingle",
            parameters = listOf(QualityParameter("fixtureId", "1379192")),
            canonicalHash = "e2e-canonical-hash",
            rawJsonObjectKey = objectKey,
            collectedAt = Instant.parse("2026-01-25T00:10:00Z"),
        )

    private fun assertDefaultResultAudit(document: QualityResultDocument) {
        assertThat(document.archive.status).isEqualTo(QualityResultArchiveStatus.NONE)
        assertThat(document.createdAt).isEqualTo(document.checkedAt)
        assertThat(document.updatedAt).isEqualTo(document.checkedAt)
    }

    companion object {
        private const val COLLECTION_NAME = "quality_results"
        private const val INPUT_TOPIC = "football-data-raw-collected-e2e"
        private const val OUTPUT_TOPIC = "football-data-quality-result-e2e"
        private const val NO_ISSUE_FIXTURE_RESOURCE = "example/fixture-single/fixture-single-1379192-no-issue.json"
        private const val NO_ISSUE_OBJECT_KEY = "raw/fixture-single-1379192-no-issue.json.gz"
        private const val VENUE_ID_ISSUE_OBJECT_KEY = "raw/fixture-single-1379192-venue-id-zero.json.gz"
        private const val RETRY_OBJECT_KEY = "raw/fixture-single-1379192-retry.json.gz"
        private val POLL_DURATION = Duration.ofMillis(250)
        private val ASSIGNMENT_TIMEOUT = Duration.ofSeconds(10)
        private val RESULT_TIMEOUT = Duration.ofSeconds(15)
        private val DUPLICATE_RESULT_WAIT = Duration.ofSeconds(2)
        private val FAILURE_RESULT_WAIT = Duration.ofSeconds(1)
        private const val MINIO_BUCKET = "footballay-data-quality-raw-test"
        private const val MINIO_ACCESS_KEY = "minioadmin"
        private const val MINIO_SECRET_KEY = "minioadmin"

        @Container
        @JvmStatic
        private val kafka = ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.8.0"))

        @Container
        @JvmStatic
        private val mongo = MongoDBContainer(DockerImageName.parse("mongo:8.0"))

        @Container
        @JvmStatic
        private val minio = MinioContainer()

        @BeforeAll
        @JvmStatic
        fun createTopicsAndWaitForListener() {
            s3Client().use { it.createBucket(CreateBucketRequest.builder().bucket(MINIO_BUCKET).build()) }
            AdminClient.create(mapOf("bootstrap.servers" to kafka.bootstrapServers)).use { adminClient ->
                adminClient
                    .createTopics(
                        listOf(
                            NewTopic(INPUT_TOPIC, 1, 1),
                            NewTopic(OUTPUT_TOPIC, 1, 1),
                        ),
                    ).values()
                    .values
                    .forEach { topicCreation ->
                        runCatching { topicCreation.get() }
                            .onFailure { exception ->
                                check(exception.cause !is org.apache.kafka.common.errors.TopicExistsException) {
                                    "Cannot create E2E Kafka topic: ${exception.message}"
                                }
                            }
                    }
            }
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureInfrastructure(registry: DynamicPropertyRegistry) {
            registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers)
            registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl)
            registry.add("footballay.data-quality.pipeline.reader.bucket") { MINIO_BUCKET }
            registry.add("footballay.data-quality.pipeline.reader.region") { "ap-northeast-2" }
            registry.add("footballay.data-quality.pipeline.reader.endpoint") { minioEndpoint() }
            registry.add("footballay.data-quality.pipeline.reader.path-style-access-enabled") { true }
            registry.add("footballay.data-quality.pipeline.reader.access-key") { MINIO_ACCESS_KEY }
            registry.add("footballay.data-quality.pipeline.reader.secret-key") { MINIO_SECRET_KEY }
            registry.add("footballay.data-quality.pipeline.raw-collected-topic") { INPUT_TOPIC }
            registry.add("footballay.data-quality.pipeline.quality-result-topic") { OUTPUT_TOPIC }
            registry.add("footballay.data-quality.pipeline.consumer-group") { "data-quality-e2e-input" }
        }

        private fun s3Client(): S3Client =
            S3Client.builder()
                .region(Region.of("ap-northeast-2"))
                .credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(MINIO_ACCESS_KEY, MINIO_SECRET_KEY),
                    ),
                ).endpointOverride(java.net.URI.create(minioEndpoint()))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()

        private fun minioEndpoint(): String = "http://${minio.host}:${minio.getMappedPort(9000)}"
    }
}

private class MinioContainer : GenericContainer<MinioContainer>(
    DockerImageName.parse("minio/minio:RELEASE.2025-04-22T22-12-26Z"),
) {
    init {
        withEnv("MINIO_ROOT_USER", "minioadmin")
        withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
        withCommand("server", "/data")
        withExposedPorts(9000)
    }
}
