package com.footballay.dataquality.inspection.apisports.fixturesingle

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiSportsFixtureSingleRootDto(
    val get: String? = null,
    val parameters: ApiSportsFixtureSingleParametersDto? = null,
    val errors: JsonNode? = null,
    val results: Int? = null,
    val paging: ApiSportsFixtureSinglePagingDto? = null,
    val response: List<ApiSportsFixtureSingleResponseDto?>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiSportsFixtureSingleParametersDto(
    val id: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiSportsFixtureSinglePagingDto(
    val current: Long? = null,
    val total: Long? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiSportsFixtureSingleResponseDto(
    val fixture: ApiSportsFixtureSingleFixtureDto? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiSportsFixtureSingleFixtureDto(
    val id: Long? = null,
    val referee: String? = null,
    val timezone: String? = null,
    val date: String? = null,
    val timestamp: Long? = null,
    val periods: ApiSportsFixtureSinglePeriodsDto? = null,
    val venue: ApiSportsFixtureSingleVenueDto? = null,
    val status: ApiSportsFixtureSingleStatusDto? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiSportsFixtureSinglePeriodsDto(
    val first: Long? = null,
    val second: Long? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiSportsFixtureSingleVenueDto(
    val id: Long? = null,
    val name: String? = null,
    val city: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiSportsFixtureSingleStatusDto(
    val long: String? = null,
    val short: String? = null,
    val elapsed: Long? = null,
    val extra: Long? = null,
)
