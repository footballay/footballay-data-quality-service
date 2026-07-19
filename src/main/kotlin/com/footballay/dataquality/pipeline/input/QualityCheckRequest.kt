package com.footballay.dataquality.pipeline.input

data class QualityCheckRequest(
    val provider: DataProvider,
    val endpointKey: String,
    val parameters: Map<String, List<String>>,
)
