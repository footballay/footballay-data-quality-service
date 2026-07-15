package com.footballay.footballaydataqualityservice.inspection.apisports.fixturesingle

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiSportsFixtureSingleRootDto(
    val get: String? = null,
    val parameters: JsonNode? = null,
    val errors: JsonNode? = null,
    val results: Int? = null,
    val paging: JsonNode? = null,
    val response: JsonNode? = null,
)
