package com.footballay.dataquality.inspection.apisports.fixtureschedule

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiSportsFixtureScheduleRootDto(
    val response: List<ApiSportsFixtureScheduleResponseDto?>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
class ApiSportsFixtureScheduleResponseDto
