package com.footballay.dataquality.pipeline.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.dataquality.inspection.ProcessorRoutingQualityChecker
import com.footballay.dataquality.inspection.QualityInspectionProcessor
import com.footballay.dataquality.kafka.KafkaQualityResultPublisher
import com.footballay.dataquality.pipeline.idempotency.CaffeineRawEventProcessingRegistry
import com.footballay.dataquality.pipeline.QualityChecker
import com.footballay.dataquality.pipeline.idempotency.NoopRawEventProcessingRegistry
import com.footballay.dataquality.pipeline.idempotency.RawEventProcessingRegistry
import com.footballay.dataquality.pipeline.port.QualityResultPublisher
import com.footballay.dataquality.pipeline.port.QualityResultStore
import com.footballay.dataquality.pipeline.processing.DataQualityInspectionPipeline
import com.footballay.dataquality.pipeline.processing.DataQualityInspectionPipelineService
import com.footballay.dataquality.pipeline.processing.DefaultQualityResultWriteMapper
import com.footballay.dataquality.pipeline.processing.QualityResultWriteMapper
import com.footballay.dataquality.pipeline.rawresponse.MockDataRawResponseReader
import com.footballay.dataquality.pipeline.rawresponse.NoopRawResponseReader
import com.footballay.dataquality.pipeline.rawresponse.RawResponseReader
import com.footballay.dataquality.pipeline.rawresponse.S3CompatibleRawResponseReader
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ResourceLoader
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.core.KafkaTemplate
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import java.net.URI
import java.time.Clock

@Configuration
@EnableKafka
@ConditionalOnProperty(prefix = "footballay.data-quality.pipeline", name = ["enabled"], havingValue = "true")
class DataQualityPipelineConfiguration {
    @Bean
    fun rawEventProcessingRegistry(properties: DataQualityPipelineProperties): RawEventProcessingRegistry =
        if (properties.idempotency.enabled) {
            CaffeineRawEventProcessingRegistry(
                ttl = properties.idempotency.ttl,
                maximumSize = properties.idempotency.maximumSize,
            )
        } else {
            NoopRawEventProcessingRegistry()
        }

    @Bean
    fun rawResponseReader(
        properties: DataQualityPipelineProperties,
        objectMapper: ObjectMapper,
        resourceLoader: ResourceLoader,
        dataQualityRawResponseS3Client: ObjectProvider<S3Client>,
    ): RawResponseReader =
        when (properties.reader.type) {
            RawResponseReaderType.NOOP -> NoopRawResponseReader()
            RawResponseReaderType.MOCK_DATA ->
                MockDataRawResponseReader(
                    objectMapper = objectMapper,
                    resource = resourceLoader.getResource(properties.reader.mockDataResource),
                )
            RawResponseReaderType.S3 ->
                S3CompatibleRawResponseReader(
                    objectMapper = objectMapper,
                    s3Client = requireNotNull(dataQualityRawResponseS3Client.ifAvailable),
                    bucket = requireS3Bucket(properties.reader),
                )
        }

    @Bean
    @ConditionalOnProperty(
        prefix = "footballay.data-quality.pipeline.reader",
        name = ["type"],
        havingValue = "s3",
    )
    fun dataQualityRawResponseS3Client(properties: DataQualityPipelineProperties): S3Client {
        val reader = properties.reader
        requireS3Bucket(reader)
        require(reader.region.isNotBlank()) {
            "footballay.data-quality.pipeline.reader.region is required for S3 reader"
        }
        require((reader.accessKey.isNullOrBlank()) == (reader.secretKey.isNullOrBlank())) {
            "footballay.data-quality.pipeline.reader.access-key and secret-key must be configured together"
        }
        return S3Client.builder()
            .region(Region.of(reader.region))
            .credentialsProvider(s3CredentialsProvider(reader))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(reader.pathStyleAccessEnabled).build())
            .apply {
                reader.endpoint?.takeIf(String::isNotBlank)?.let { endpointOverride(URI.create(it)) }
            }
            .build()
    }

    private fun requireS3Bucket(reader: DataQualityPipelineProperties.Reader): String =
        requireNotNull(reader.bucket.takeIf { it.isNotBlank() }) {
            "footballay.data-quality.pipeline.reader.bucket is required for S3 reader"
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
        processingRegistry: RawEventProcessingRegistry,
        rawResponseReader: RawResponseReader,
        qualityChecker: QualityChecker,
        qualityResultWriteMapper: QualityResultWriteMapper,
        resultStore: QualityResultStore,
        resultPublisher: QualityResultPublisher,
    ): DataQualityInspectionPipeline =
        DataQualityInspectionPipelineService(
            processingRegistry = processingRegistry,
            rawResponseReader = rawResponseReader,
            qualityChecker = qualityChecker,
            qualityResultWriteMapper = qualityResultWriteMapper,
            resultStore = resultStore,
            resultPublisher = resultPublisher,
            clock = Clock.systemUTC(),
        )
}

internal fun s3CredentialsProvider(reader: DataQualityPipelineProperties.Reader): AwsCredentialsProvider =
    if (reader.accessKey.isNullOrBlank()) {
        DefaultCredentialsProvider.create()
    } else {
        StaticCredentialsProvider.create(AwsBasicCredentials.create(reader.accessKey, reader.secretKey))
    }
