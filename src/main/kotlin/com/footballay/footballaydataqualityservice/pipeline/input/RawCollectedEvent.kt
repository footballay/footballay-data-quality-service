package com.footballay.footballaydataqualityservice.pipeline.input

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class RawCollectedEvent(
    val rawEventId: String,
    val provider: DataProvider,
    val endpointKey: String,
    val parameters: List<QualityParameter>,
    val canonicalHash: String,
    val rawJsonObjectKey: String,
    val collectedAt: Instant,
)

data class QualityParameter(
    val name: String,
    val value: String,
)
