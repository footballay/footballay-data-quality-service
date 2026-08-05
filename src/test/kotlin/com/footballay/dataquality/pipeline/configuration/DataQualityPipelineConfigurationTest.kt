package com.footballay.dataquality.pipeline.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.dataquality.pipeline.idempotency.CaffeineRawEventProcessingRegistry
import com.footballay.dataquality.pipeline.idempotency.NoopRawEventProcessingRegistry
import com.footballay.dataquality.pipeline.idempotency.RawEventProcessingRegistry
import com.footballay.dataquality.pipeline.port.NoopQualityResultStore
import com.footballay.dataquality.pipeline.port.QualityResultPublisher
import com.footballay.dataquality.pipeline.port.QualityResultStore
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.kafka.core.KafkaTemplate
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.services.s3.S3Client
import org.mockito.Mockito.mock
import java.time.Duration

class DataQualityPipelineConfigurationTest {
    private val contextRunner =
        ApplicationContextRunner()
            .withUserConfiguration(DataQualityPipelineConfiguration::class.java, PipelineTestConfiguration::class.java)
            .withPropertyValues(
                "footballay.data-quality.pipeline.enabled=true",
                "footballay.data-quality.pipeline.store.type=noop",
            )

    @Test
    fun `uses Caffeine processing registry by default`() {
        contextRunner.run { context ->
            assertThat(context.getBean(RawEventProcessingRegistry::class.java))
                .isInstanceOf(CaffeineRawEventProcessingRegistry::class.java)
        }
    }

    @Test
    fun `uses Noop processing registry when disabled`() {
        contextRunner
            .withPropertyValues("footballay.data-quality.pipeline.idempotency.enabled=false")
            .run { context ->
                assertThat(context.getBean(RawEventProcessingRegistry::class.java))
                    .isInstanceOf(NoopRawEventProcessingRegistry::class.java)
            }
    }

    @Test
    fun `rejects non-positive idempotency cache settings`() {
        assertThatIllegalArgumentException()
            .isThrownBy { DataQualityPipelineProperties.Idempotency(ttl = Duration.ZERO) }
            .withMessage("Data quality pipeline idempotency ttl must be positive: PT0S")

        assertThatIllegalArgumentException()
            .isThrownBy { DataQualityPipelineProperties.Idempotency(maximumSize = 0) }
            .withMessage("Data quality pipeline idempotency maximumSize must be positive: 0")
    }

    @Test
    fun `creates a Kafka quality result publisher without publication executors`() {
        contextRunner.run { context ->
            assertThat(context.getBean(QualityResultPublisher::class.java)).isNotNull
            assertThat(context.containsBean("qualityResultPublicationExecutor")).isFalse()
            assertThat(context.containsBean("qualityResultPublicationRetryTaskScheduler")).isFalse()
        }
    }

    @Test
    fun `starts the production S3 pipeline configuration with required properties`() {
        ApplicationContextRunner()
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withUserConfiguration(DataQualityPipelineConfiguration::class.java, PipelineTestConfiguration::class.java)
            .withPropertyValues(
                "spring.profiles.active=prod",
                "FOOTBALLAY_MONGODB_URI=mongodb://test-host/test-database",
                "AIVEN_KAFKA_BOOTSTRAP_SERVERS=test-host:9092",
                "AIVEN_KAFKA_CA_PATH=/tmp/aiven-ca.pem",
                "AIVEN_KAFKA_DQ_USERNAME=test-user",
                "AIVEN_KAFKA_DQ_PASSWORD=test-password",
                "FOOTBALLAY_DATA_QUALITY_PIPELINE_ENABLED=true",
                "FOOTBALLAY_DATA_QUALITY_RAW_S3_BUCKET=production-raw-bucket",
            ).run { context ->
                val properties = context.getBean(DataQualityPipelineProperties::class.java)

                assertThat(context.startupFailure).isNull()
                assertThat(context).hasSingleBean(S3Client::class.java)
                assertThat(properties.enabled).isTrue()
                assertThat(properties.reader.type).isEqualTo(RawResponseReaderType.S3)
                assertThat(properties.store.type).isEqualTo(QualityResultStoreType.MONGO)
                assertThat(properties.reader.bucket).isEqualTo("production-raw-bucket")
                assertThat(properties.reader.region).isEqualTo("ap-northeast-2")
            }
    }

    @Test
    fun `uses default credentials when S3 static credentials are absent`() {
        assertThat(s3CredentialsProvider(DataQualityPipelineProperties.Reader()))
            .isInstanceOf(DefaultCredentialsProvider::class.java)
    }

    @Test
    fun `uses static credentials for local S3-compatible storage`() {
        assertThat(
            s3CredentialsProvider(
                DataQualityPipelineProperties.Reader(
                    accessKey = "local-access-key",
                    secretKey = "local-secret-key",
                ),
            ),
        ).isInstanceOf(StaticCredentialsProvider::class.java)
    }
}

@TestConfiguration(proxyBeanMethods = false)
@EnableConfigurationProperties(DataQualityPipelineProperties::class)
private class PipelineTestConfiguration {
    @Bean
    fun objectMapper(): ObjectMapper = ObjectMapper().findAndRegisterModules()

    @Bean
    @Suppress("UNCHECKED_CAST")
    fun kafkaTemplate(): KafkaTemplate<String, String> = mock(KafkaTemplate::class.java) as KafkaTemplate<String, String>

    @Bean
    fun qualityResultStore(): QualityResultStore = NoopQualityResultStore()
}
