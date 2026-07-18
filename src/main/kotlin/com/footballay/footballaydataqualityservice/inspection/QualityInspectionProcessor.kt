package com.footballay.footballaydataqualityservice.inspection

import com.fasterxml.jackson.databind.JsonNode
import com.footballay.footballaydataqualityservice.pipeline.input.DataProvider
import com.footballay.footballaydataqualityservice.pipeline.input.QualityCheckRequest

interface QualityInspectionProcessor {
    fun supports(
        provider: DataProvider,
        endpointKey: String,
    ): Boolean

    fun inspect(
        request: QualityCheckRequest,
        rawJson: JsonNode,
    ): List<FoundIssue>
}
