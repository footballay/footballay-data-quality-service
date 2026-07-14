package com.footballay.footballaydataqualityservice.pipeline

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class RawCollectedEvent(
    val rawEventId: String,
    val provider: String,
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
