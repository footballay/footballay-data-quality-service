package com.footballay.dataquality.inspection.apisports.fixturesingle

data class FixtureSingleRequest(
    val fixtureId: Long?,
)

data class FixtureSingleRequestResponse(
    val request: FixtureSingleRequest,
    val response: ApiSportsFixtureSingleRootDto,
)
