package com.footballay.dataquality.pipeline.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("footballay.data-quality.pipeline")
data class DataQualityPipelineProperties(
    val enabled: Boolean = false,
    val consumerGroup: String = "footballay-data-quality-service",
    val rawCollectedTopic: String = "football-data-raw-collected",
    val qualityResultTopic: String = "football-data-quality-result",
    val idempotency: Idempotency = Idempotency(),
    val reader: Reader = Reader(),
    val store: Store = Store(),
) {
    data class Idempotency(
        val enabled: Boolean = true,
        val ttl: Duration = Duration.ofHours(24),
        val maximumSize: Long = 100_000,
    ) {
        init {
            require(!ttl.isNegative && !ttl.isZero) {
                "Data quality pipeline idempotency ttl must be positive: $ttl"
            }
            require(maximumSize > 0) {
                "Data quality pipeline idempotency maximumSize must be positive: $maximumSize"
            }
        }
    }

    data class Reader(
        val type: RawResponseReaderType = RawResponseReaderType.NOOP,
        val mockDataResource: String = "classpath:mock/20260712T033403Z_Iq_XxagJ8LaiRo1Fy1M4oYRNxUhVIp8aOn9jJCmAGWY.json.gz",
        val bucket: String = "",
        val region: String = "",
        val endpoint: String? = null,
        val pathStyleAccessEnabled: Boolean = false,
        val accessKey: String? = null,
        val secretKey: String? = null,
    )

    data class Store(
        val type: QualityResultStoreType = QualityResultStoreType.MONGO,
    )

}

enum class RawResponseReaderType {
    NOOP,
    MOCK_DATA,
    S3,
}

enum class QualityResultStoreType {
    MONGO,
    NOOP,
}
