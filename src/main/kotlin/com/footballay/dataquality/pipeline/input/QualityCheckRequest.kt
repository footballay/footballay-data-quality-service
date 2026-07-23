package com.footballay.dataquality.pipeline.input

import java.time.Instant

data class QualityCheckRequest(
    val provider: DataProvider,
    val endpointKey: String,
    val parameters: Map<String, List<String>>,
    val collectedAt: Instant? = null,
)
