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
import org.testcontainers.kafka.KafkaContainer
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "footballay.data-quality.pipeline.enabled=true",
        "footballay.data-quality.pipeline.reader.type=mock-data",
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
    fun `consumes a raw event and publishes a no-issue result`() {
        val listenerContainer = kafkaListenerEndpointRegistry.listenerContainers.single()
        ContainerTestUtils.waitForAssignment(listenerContainer, 1)

        newResultConsumer().use { consumer ->
            consumer.subscribe(listOf("football-data-quality-result"))

            val input =
                RawCollectedEvent(
                    rawEventId = "01JZK8T9CJ4S9ZZ9G0E0D7YQ9M",
                    provider = "API_SPORTS",
                    endpointKey = "fixtureSingle",
                    parameters = listOf(QualityParameter("fixtureId", "1582681")),
                    canonicalHash = "sha256-base64url",
                    rawJsonObjectKey = "football-data/raw/example.json.gz",
                    collectedAt = Instant.parse("2026-07-12T00:00:00Z"),
                )
            kafkaTemplate.send("football-data-raw-collected", input.rawEventId, objectMapper.writeValueAsString(input)).get()

            val records = pollUntilRecord(consumer)
            assertThat(records.count()).isEqualTo(1)
            val result = objectMapper.readValue(records.iterator().next().value(), QualityResultEvent::class.java)
            assertThat(result.rawEventId).isEqualTo(input.rawEventId)
            assertThat(result.resultId).matches("[0-9A-HJKMNP-TV-Z]{26}")
            assertThat(result.hasIssue).isFalse()
            assertThat(result.issueCount).isZero()
            assertThat(result.maxSeverity.name).isEqualTo("NONE")
            assertThat(result.source.parameters).containsExactly(QualityParameter("fixtureId", "1582681"))
        }
    }

    private fun pollUntilRecord(consumer: KafkaConsumer<String, String>) =
        generateSequence { consumer.poll(Duration.ofMillis(500)) }
            .take(20)
            .firstOrNull { !it.isEmpty }
            ?: error("Timed out waiting for quality-result event")

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
}
