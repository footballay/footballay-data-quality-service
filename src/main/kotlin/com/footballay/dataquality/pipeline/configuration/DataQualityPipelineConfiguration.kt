package com.footballay.dataquality.pipeline.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.dataquality.inspection.ProcessorRoutingQualityChecker
import com.footballay.dataquality.inspection.QualityInspectionProcessor
import com.footballay.dataquality.kafka.KafkaQualityResultPublisher
import com.footballay.dataquality.pipeline.QualityChecker
import com.footballay.dataquality.pipeline.idempotency.NoopRawEventIdempotencyChecker
import com.footballay.dataquality.pipeline.idempotency.RawEventIdempotencyChecker
import com.footballay.dataquality.pipeline.port.QualityResultPublisher
import com.footballay.dataquality.pipeline.port.QualityResultStore
import com.footballay.dataquality.pipeline.processing.DataQualityInspectionPipeline
import com.footballay.dataquality.pipeline.processing.DataQualityInspectionPipelineService
import com.footballay.dataquality.pipeline.processing.DefaultQualityResultWriteMapper
import com.footballay.dataquality.pipeline.processing.QualityResultWriteMapper
import com.footballay.dataquality.pipeline.rawresponse.LocalFileRawResponseReader
import com.footballay.dataquality.pipeline.rawresponse.MockDataRawResponseReader
import com.footballay.dataquality.pipeline.rawresponse.NoopRawResponseReader
import com.footballay.dataquality.pipeline.rawresponse.RawResponseReader
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
    fun qualityResultWriteMapper(): QualityResultWriteMapper = DefaultQualityResultWriteMapper()

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
        qualityResultWriteMapper: QualityResultWriteMapper,
        resultStore: QualityResultStore,
        resultPublisher: QualityResultPublisher,
    ): DataQualityInspectionPipeline =
        DataQualityInspectionPipelineService(
            idempotencyChecker = idempotencyChecker,
            rawResponseReader = rawResponseReader,
            qualityChecker = qualityChecker,
            qualityResultWriteMapper = qualityResultWriteMapper,
            resultStore = resultStore,
            resultPublisher = resultPublisher,
            clock = Clock.systemUTC(),
        )
}
