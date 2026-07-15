package com.footballay.footballaydataqualityservice.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.footballaydataqualityservice.inspection.ProcessorRoutingQualityChecker
import com.footballay.footballaydataqualityservice.inspection.QualityInspectionProcessor
import com.footballay.footballaydataqualityservice.inspection.QualityInspector
import com.footballay.footballaydataqualityservice.inspection.apisports.fixturesingle.ApiSportsFixtureSingleInspectionProcessor
import com.footballay.footballaydataqualityservice.inspection.apisports.fixturesingle.ApiSportsFixtureSingleRootDto
import com.footballay.footballaydataqualityservice.kafka.KafkaQualityResultPublisher
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ResourceLoader
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.core.KafkaTemplate
import java.nio.file.Path
import java.time.Clock

@Configuration
@EnableKafka
@ConditionalOnProperty(prefix = "footballay.data-quality.pipeline", name = ["enabled"], havingValue = "true")
class DataQualityPipelineConfiguration {
    @Bean
    fun rawEventIdempotencyChecker(): RawEventIdempotencyChecker = NoopRawEventIdempotencyChecker()

    @Bean
    fun rawResponseReader(
        properties: DataQualityPipelineProperties,
        objectMapper: ObjectMapper,
        resourceLoader: ResourceLoader,
    ): RawResponseReader =
        when (properties.reader.type) {
            RawResponseReaderType.NOOP -> NoopRawResponseReader()
            RawResponseReaderType.MOCK_DATA ->
                MockDataRawResponseReader(
                    objectMapper = objectMapper,
                    resource = resourceLoader.getResource(properties.reader.mockDataResource),
                )
            RawResponseReaderType.LOCAL_FILE ->
                LocalFileRawResponseReader(
                    objectMapper = objectMapper,
                    baseDir = Path.of(requireLocalBaseDir(properties.reader.localBaseDir)),
                )
        }

    private fun requireLocalBaseDir(localBaseDir: String?): String =
        requireNotNull(localBaseDir?.takeIf { it.isNotBlank() }) {
            "footballay.data-quality.pipeline.reader.local-base-dir is required for LOCAL_FILE reader"
        }

    @Bean
    fun apiSportsFixtureSingleInspectionProcessor(
        objectMapper: ObjectMapper,
        inspectors: List<QualityInspector<ApiSportsFixtureSingleRootDto>>,
    ): QualityInspectionProcessor =
        ApiSportsFixtureSingleInspectionProcessor(
            objectMapper = objectMapper,
            inspectors = inspectors,
        )

    @Bean
    fun qualityChecker(processors: List<QualityInspectionProcessor>): QualityChecker = ProcessorRoutingQualityChecker(processors)

    @Bean
    fun qualityResultStore(): QualityResultStore = NoopQualityResultStore()

    @Bean
    fun qualityResultPublisher(
        kafkaTemplate: KafkaTemplate<String, String>,
        objectMapper: ObjectMapper,
        properties: DataQualityPipelineProperties,
    ): QualityResultPublisher =
        KafkaQualityResultPublisher(
            kafkaTemplate = kafkaTemplate,
            objectMapper = objectMapper,
            topic = properties.qualityResultTopic,
        )

    @Bean
    fun dataQualityPipelineService(
        idempotencyChecker: RawEventIdempotencyChecker,
        rawResponseReader: RawResponseReader,
        qualityChecker: QualityChecker,
        resultStore: QualityResultStore,
        resultPublisher: QualityResultPublisher,
    ): DataQualityPipelineService =
        DataQualityPipelineService(
            idempotencyChecker = idempotencyChecker,
            rawResponseReader = rawResponseReader,
            qualityChecker = qualityChecker,
            resultStore = resultStore,
            resultPublisher = resultPublisher,
            clock = Clock.systemUTC(),
        )
}
