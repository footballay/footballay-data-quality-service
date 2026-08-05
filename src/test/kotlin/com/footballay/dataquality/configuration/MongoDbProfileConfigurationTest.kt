package com.footballay.dataquality.configuration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.core.io.ClassPathResource
import java.util.Properties

class MongoDbProfileConfigurationTest {

    @Test
    fun `production profile requires the shared MongoDB URI environment variable`() {
        assertThat(properties("application-prod.yml").getProperty("spring.data.mongodb.uri"))
            .isEqualTo("\${FOOTBALLAY_MONGODB_URI}")
    }

    @Test
    fun `local profile selects the footballay database`() {
        assertThat(properties("application-local.yml").getProperty("spring.data.mongodb.uri"))
            .isEqualTo("mongodb://localhost:27017/footballay")
    }

    @Test
    fun `production profile configures the S3 Mongo pipeline with first deployment defaults`() {
        val prodProperties = properties("application-prod.yml")

        assertThat(prodProperties.getProperty("footballay.data-quality.pipeline.enabled"))
            .isEqualTo("\${FOOTBALLAY_DATA_QUALITY_PIPELINE_ENABLED:false}")
        assertThat(prodProperties.getProperty("footballay.data-quality.pipeline.reader.type")).isEqualTo("s3")
        assertThat(prodProperties.getProperty("footballay.data-quality.pipeline.store.type")).isEqualTo("mongo")
        assertThat(prodProperties.getProperty("footballay.data-quality.pipeline.reader.bucket"))
            .isEqualTo("\${FOOTBALLAY_DATA_QUALITY_RAW_S3_BUCKET:footballay-data-quality}")
        assertThat(prodProperties.getProperty("footballay.data-quality.pipeline.reader.region"))
            .isEqualTo("\${FOOTBALLAY_DATA_QUALITY_RAW_S3_REGION:ap-northeast-2}")
        assertThat(prodProperties.getProperty("footballay.data-quality.pipeline.reader.endpoint"))
            .isEqualTo("\${FOOTBALLAY_DATA_QUALITY_RAW_S3_ENDPOINT:}")
        assertThat(prodProperties.getProperty("footballay.data-quality.pipeline.reader.path-style-access-enabled"))
            .isEqualTo("\${FOOTBALLAY_DATA_QUALITY_RAW_S3_PATH_STYLE_ACCESS_ENABLED:false}")
        assertThat(prodProperties.getProperty("spring.kafka.ssl.trust-store-location"))
            .isEqualTo("file:\${AIVEN_KAFKA_CA_PATH:/run/secrets/aiven-kafka-ca.pem}")
        assertThat(prodProperties.getProperty("footballay.data-quality.pipeline.reader.access-key")).isNull()
        assertThat(prodProperties.getProperty("footballay.data-quality.pipeline.reader.secret-key")).isNull()
    }

    @Test
    fun `production profile uses DQ Kafka credentials instead of Core credentials`() {
        val jaasConfig = requireNotNull(properties("application-prod.yml").getProperty("spring.kafka.properties.sasl.jaas.config"))

        assertThat(jaasConfig).contains("\${AIVEN_KAFKA_DQ_USERNAME}", "\${AIVEN_KAFKA_DQ_PASSWORD}")
        assertThat(jaasConfig).doesNotContain("AIVEN_KAFKA_CORE_USERNAME", "AIVEN_KAFKA_CORE_PASSWORD")
    }

    @Test
    fun `local profile retains static S3-compatible credential defaults`() {
        val localProperties = properties("application-local.yml")

        assertThat(localProperties.getProperty("footballay.data-quality.pipeline.reader.access-key")).isNotBlank()
        assertThat(localProperties.getProperty("footballay.data-quality.pipeline.reader.secret-key")).isNotBlank()
    }

    private fun properties(resourceName: String): Properties {
        val yamlProperties = YamlPropertiesFactoryBean().apply {
            setResources(ClassPathResource(resourceName))
        }

        return requireNotNull(yamlProperties.`object`)
    }
}
