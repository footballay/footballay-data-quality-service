package com.footballay.footballaydataqualityservice.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.footballaydataqualityservice.TestcontainersConfiguration
import com.footballay.footballaydataqualityservice.pipeline.QualityParameter
import com.footballay.footballaydataqualityservice.pipeline.QualityResultEvent
import com.footballay.footballaydataqualityservice.pipeline.RawCollectedEvent
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.kafka.KafkaContainer
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream

@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "footballay.data-quality.pipeline.enabled=true",
        "footballay.data-quality.pipeline.reader.type=local-file",
    ],
)
class DataQualityKafkaFlowIntegrationTest {
    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var kafkaContainer: KafkaContainer

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private lateinit var kafkaListenerEndpointRegistry: KafkaListenerEndpointRegistry

    @Test
    fun `consumes local gzip and JSON raw files and publishes no-issue results`() {
        val listenerContainer = kafkaListenerEndpointRegistry.listenerContainers.single()
        ContainerTestUtils.waitForAssignment(listenerContainer, 1)

        newResultConsumer().use { consumer ->
            consumer.subscribe(listOf("football-data-quality-result"))

            val gzipObjectKey = "football-data/raw/compressed-response.data"
            val plainFile = localRawStorageDir.resolve("plain-response.json.gz")
            writeGzip(localRawStorageDir.resolve(gzipObjectKey), """{"get":"fixtures"}""")
            Files.writeString(plainFile, """{"get":"leagues"}""")

            val inputs =
                listOf(
                    rawEvent("01JZK8T9CJ4S9ZZ9G0E0D7YQ9M", gzipObjectKey),
                    rawEvent("01JZK8T9CJ4S9ZZ9G0E0D7YQ9N", plainFile.toString()),
                )
            inputs.forEach { input ->
                kafkaTemplate.send("football-data-raw-collected", input.rawEventId, objectMapper.writeValueAsString(input)).get()
            }

            val results = pollUntilRecords(consumer, inputs.size).map { record ->
                objectMapper.readValue(record, QualityResultEvent::class.java)
            }
            assertThat(results.map { it.rawEventId }).containsExactlyInAnyOrderElementsOf(inputs.map { it.rawEventId })
            results.forEach { result ->
                assertThat(result.resultId).matches("[0-9A-HJKMNP-TV-Z]{26}")
                assertThat(result.hasIssue).isFalse()
                assertThat(result.issueCount).isZero()
                assertThat(result.maxSeverity.name).isEqualTo("NONE")
                assertThat(result.source.parameters).containsExactly(QualityParameter("fixtureId", "1582681"))
            }
        }
    }

    private fun pollUntilRecords(
        consumer: KafkaConsumer<String, String>,
        expectedCount: Int,
    ): List<String> {
        val values = mutableListOf<String>()
        repeat(20) {
            values += consumer.poll(Duration.ofMillis(500)).map { it.value() }
            if (values.size >= expectedCount) return values
        }
        error("Timed out waiting for $expectedCount quality-result events")
    }

    private fun rawEvent(rawEventId: String, rawJsonObjectKey: String) =
        RawCollectedEvent(
            rawEventId = rawEventId,
            provider = "API_SPORTS",
            endpointKey = "fixtureSingle",
            parameters = listOf(QualityParameter("fixtureId", "1582681")),
            canonicalHash = "sha256-base64url",
            rawJsonObjectKey = rawJsonObjectKey,
            collectedAt = Instant.parse("2026-07-12T00:00:00Z"),
        )

    private fun newResultConsumer(): KafkaConsumer<String, String> =
        KafkaConsumer(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaContainer.bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to "quality-result-test-${UUID.randomUUID()}",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ),
        )

    private companion object {
        val localRawStorageDir: Path = Files.createTempDirectory("data-quality-local-reader")

        @JvmStatic
        @DynamicPropertySource
        fun localReaderProperties(registry: DynamicPropertyRegistry) {
            registry.add("footballay.data-quality.pipeline.reader.local-base-dir") { localRawStorageDir.toString() }
        }

        fun writeGzip(path: Path, content: String) {
            Files.createDirectories(path.parent)
            GZIPOutputStream(Files.newOutputStream(path)).use { gzip ->
                gzip.write(content.toByteArray(StandardCharsets.UTF_8))
            }
        }
    }
}
