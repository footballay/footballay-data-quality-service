package com.footballay.footballaydataqualityservice.pipeline.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("footballay.data-quality.pipeline")
data class DataQualityPipelineProperties(
    val enabled: Boolean = false,
    val consumerGroup: String = "footballay-data-quality-service",
    val rawCollectedTopic: String = "football-data-raw-collected",
    val qualityResultTopic: String = "football-data-quality-result",
    val reader: Reader = Reader(),
) {
    data class Reader(
        val type: RawResponseReaderType = RawResponseReaderType.NOOP,
        val mockDataResource: String = "classpath:mock/20260712T033403Z_Iq_XxagJ8LaiRo1Fy1M4oYRNxUhVIp8aOn9jJCmAGWY.json.gz",
        val localBaseDir: String? = null,
    )
}

enum class RawResponseReaderType {
    NOOP,
    MOCK_DATA,
    LOCAL_FILE,
}
