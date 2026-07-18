package com.footballay.footballaydataqualityservice.pipeline.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.footballaydataqualityservice.inspection.ProcessorRoutingQualityChecker
import com.footballay.footballaydataqualityservice.inspection.QualityInspectionProcessor
import com.footballay.footballaydataqualityservice.kafka.KafkaQualityResultPublisher
import com.footballay.footballaydataqualityservice.pipeline.QualityChecker
import com.footballay.footballaydataqualityservice.pipeline.idempotency.NoopRawEventIdempotencyChecker
import com.footballay.footballaydataqualityservice.pipeline.idempotency.RawEventIdempotencyChecker
import com.footballay.footballaydataqualityservice.pipeline.port.NoopQualityResultStore
import com.footballay.footballaydataqualityservice.pipeline.port.QualityResultPublisher
import com.footballay.footballaydataqualityservice.pipeline.port.QualityResultStore
import com.footballay.footballaydataqualityservice.pipeline.processing.DataQualityInspectionPipeline
import com.footballay.footballaydataqualityservice.pipeline.processing.DataQualityInspectionPipelineService
import com.footballay.footballaydataqualityservice.pipeline.rawresponse.LocalFileRawResponseReader
import com.footballay.footballaydataqualityservice.pipeline.rawresponse.MockDataRawResponseReader
import com.footballay.footballaydataqualityservice.pipeline.rawresponse.NoopRawResponseReader
import com.footballay.footballaydataqualityservice.pipeline.rawresponse.RawResponseReader
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
    fun dataQualityInspectionPipeline(
        idempotencyChecker: RawEventIdempotencyChecker,
        rawResponseReader: RawResponseReader,
        qualityChecker: QualityChecker,
        resultStore: QualityResultStore,
        resultPublisher: QualityResultPublisher,
    ): DataQualityInspectionPipeline =
        DataQualityInspectionPipelineService(
            idempotencyChecker = idempotencyChecker,
            rawResponseReader = rawResponseReader,
            qualityChecker = qualityChecker,
            resultStore = resultStore,
            resultPublisher = resultPublisher,
            clock = Clock.systemUTC(),
        )
}
