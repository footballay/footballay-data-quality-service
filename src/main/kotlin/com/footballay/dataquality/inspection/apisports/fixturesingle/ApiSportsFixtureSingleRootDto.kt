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
    val league: ApiSportsFixtureSingleLeagueDto? = null,
    val teams: ApiSportsFixtureSingleTeamsDto? = null,
    val goals: ApiSportsFixtureSingleScorePairDto? = null,
    val score: ApiSportsFixtureSingleScoreDto? = null,
    val events: List<ApiSportsFixtureSingleEventDto?>? = null,
    val lineups: List<ApiSportsFixtureSingleLineupDto?>? = null,
    val statistics: List<ApiSportsFixtureSingleTeamStatisticsDto?>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiSportsFixtureSingleTeamStatisticsDto(
    val team: ApiSportsFixtureSingleEventTeamDto? = null,
    val statistics: List<ApiSportsFixtureSingleStatisticDto?>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiSportsFixtureSingleStatisticDto(
    val type: String? = null,
    val value: JsonNode? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiSportsFixtureSingleLeagueDto(
    val id: Long? = null,
    val name: String? = null,
    val season: Int? = null,
    val round: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiSportsFixtureSingleTeamsDto(
    val home: ApiSportsFixtureSingleTeamDto? = null,
    val away: ApiSportsFixtureSingleTeamDto? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiSportsFixtureSingleTeamDto(
    val id: Long? = null,
    val name: String? = null,
    val winner: Boolean? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiSportsFixtureSingleScorePairDto(
    val home: Long? = null,
    val away: Long? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiSportsFixtureSingleScoreDto(
    val halftime: ApiSportsFixtureSingleScorePairDto? = null,
    val fulltime: ApiSportsFixtureSingleScorePairDto? = null,
    val extratime: ApiSportsFixtureSingleScorePairDto? = null,
    val penalty: ApiSportsFixtureSingleScorePairDto? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiSportsFixtureSingleEventDto(
    val time: ApiSportsFixtureSingleEventTimeDto? = null,
    val team: ApiSportsFixtureSingleEventTeamDto? = null,
    val player: ApiSportsFixtureSingleEventPersonDto? = null,
    val assist: ApiSportsFixtureSingleEventPersonDto? = null,
    val type: String? = null,
    val detail: String? = null,
    val comments: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiSportsFixtureSingleEventTimeDto(
    val elapsed: Long? = null,
    val extra: Long? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiSportsFixtureSingleEventTeamDto(
    val id: Long? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiSportsFixtureSingleEventPersonDto(
    val id: Long? = null,
    val name: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiSportsFixtureSingleLineupDto(
    val team: ApiSportsFixtureSingleLineupTeamDto? = null,
    val formation: String? = null,
    val startXI: List<ApiSportsFixtureSingleLineupPlayerEntryDto?>? = null,
    val substitutes: List<ApiSportsFixtureSingleLineupPlayerEntryDto?>? = null,
    val coach: ApiSportsFixtureSingleLineupCoachDto? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiSportsFixtureSingleLineupTeamDto(
    val id: Long? = null,
    val colors: ApiSportsFixtureSingleLineupColorsDto? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiSportsFixtureSingleLineupColorsDto(
    val player: ApiSportsFixtureSingleLineupColorDto? = null,
    val goalkeeper: ApiSportsFixtureSingleLineupColorDto? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiSportsFixtureSingleLineupColorDto(
    val primary: String? = null,
    val number: String? = null,
    val border: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiSportsFixtureSingleLineupPlayerEntryDto(
    val player: ApiSportsFixtureSingleLineupPlayerDto? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiSportsFixtureSingleLineupPlayerDto(
    val id: Long? = null,
    val name: String? = null,
    val number: Long? = null,
    val pos: String? = null,
    val grid: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiSportsFixtureSingleLineupCoachDto(
    val id: Long? = null,
    val name: String? = null,
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
