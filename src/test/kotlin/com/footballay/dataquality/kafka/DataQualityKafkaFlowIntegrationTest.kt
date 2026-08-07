package com.footballay.dataquality.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.dataquality.TestcontainersConfiguration
import com.footballay.dataquality.pipeline.input.DataProvider
import com.footballay.dataquality.pipeline.input.QualityParameter
import com.footballay.dataquality.pipeline.input.RawCollectedEvent
import com.footballay.dataquality.pipeline.processing.DataQualityInspectionPipeline
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.utils.ContainerTestUtils
import java.time.Instant

/**
 * Kafka 수신 어댑터 경계만 검증한다.
 *
 * 실제 Kafka에서 받은 payload가 RawCollectedEvent로 역직렬화되어
 * DataQualityInspectionPipeline에 전달되는지만 확인하며, pipeline 하위의
 * raw reader, Inspector, 저장소, 결과 발행 구현은 이 테스트 범위에 포함하지 않는다.
 */
@Import(TestcontainersConfiguration::class, KafkaListenerTestConfiguration::class)
@SpringBootTest(
    properties = [
        "footballay.data-quality.pipeline.enabled=true",
        "footballay.data-quality.pipeline.reader.type=noop",
        "footballay.data-quality.pipeline.store.type=noop",
    ],
)
class DataQualityKafkaFlowIntegrationTest {
    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var inspectionPipeline: DataQualityInspectionPipeline

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private lateinit var kafkaListenerEndpointRegistry: KafkaListenerEndpointRegistry

    @Test
    fun `deserializes a raw collected Kafka message and delegates it to the inspection pipeline`() {
        val listenerContainer = kafkaListenerEndpointRegistry.listenerContainers.single()
        ContainerTestUtils.waitForAssignment(listenerContainer, 1)
        val event =
            RawCollectedEvent(
                rawEventId = "01JZK8T9CJ4S9ZZ9G0E0D7YQ9M",
                provider = DataProvider.API_SPORTS,
                endpointKey = "fixtureSingle",
                parameters = listOf(QualityParameter("fixtureId", "1582681")),
                canonicalHash = "sha256-base64url",
                rawJsonObjectKey = "football-data/raw/fixture.json.gz",
                collectedAt = Instant.parse("2026-07-12T00:00:00Z"),
            )

        kafkaTemplate.send("football-data-raw-collected", event.rawEventId, objectMapper.writeValueAsString(event)).get()

        verify(inspectionPipeline, timeout(5_000)).process(event)

        assertThat(meterRegistry.meters.map { it.id.name })
            .anyMatch { it.startsWith("kafka.consumer") }
    }
}

@TestConfiguration(proxyBeanMethods = false)
class KafkaListenerTestConfiguration {
    @Bean
    @Primary
    fun kafkaListenerInspectionPipeline(): DataQualityInspectionPipeline = mock(DataQualityInspectionPipeline::class.java)
}
