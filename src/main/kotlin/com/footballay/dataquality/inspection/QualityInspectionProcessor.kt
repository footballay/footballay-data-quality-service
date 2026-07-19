package com.footballay.dataquality.inspection

import com.fasterxml.jackson.databind.JsonNode
import com.footballay.dataquality.pipeline.input.DataProvider
import com.footballay.dataquality.pipeline.input.QualityCheckRequest

interface QualityInspectionProcessor {
    fun inspectors(): List<QualityInspector<*>>

    fun supports(
        provider: DataProvider,
        endpointKey: String,
    ): Boolean

    fun inspect(
        request: QualityCheckRequest,
        rawJson: JsonNode,
    ): List<FoundIssue>
}
