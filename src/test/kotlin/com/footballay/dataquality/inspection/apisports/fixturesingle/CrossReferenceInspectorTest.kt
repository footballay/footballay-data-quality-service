package com.footballay.dataquality.inspection.apisports.fixturesingle

import com.footballay.dataquality.inspection.InspectionSeverity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CrossReferenceInspectorTest {
    private val inspector = CrossReferenceInspector()

    @Test
    fun `does not report matching lineup player statistics events and substitutions in either API Sports field order`() {
        val result =
            inspector.inspect(
                root(
                    lineups = listOf(lineup(1, start = startingEleven(11), bench = listOf(22)), lineup(2, start = startingEleven(31), bench = listOf(42))),
                    events = listOf(event(1, "Goal", 11), event(1, "subst", 11, 22), event(2, "subst", 42, 31)),
                ),
            )

        assertThat(result).isEmpty()
    }

    @Test
    fun `reports lineup and player statistics membership and substitute conflicts`() {
        val teamOneStatistics =
            statisticsForIds(
                teamId = 1,
                playerIds = startingEleven(11) + listOf(30, 99),
                substituteByPlayerId = mapOf(11L to true, 30L to false, 99L to true),
            )
        val result =
            inspector.inspect(
                root(
                    lineups = listOf(lineup(1, start = startingEleven(11), bench = listOf(30, 31)), lineup(2, start = listOf(21), bench = listOf(22))),
                    players = listOf(teamOneStatistics, statistics(2, 21 to false, 22 to true)),
                ),
            )

        assertThat(result.map { it.inspectionRuleCode.value })
            .containsExactlyInAnyOrder(
                "LINEUP_PLAYER_MISSING_FROM_PLAYER_STATISTICS",
                "PLAYER_STATISTICS_PLAYER_NOT_IN_LINEUP",
                "STARTER_SUBSTITUTE_FLAG_CONFLICT",
                "BENCH_STARTER_FLAG_CONFLICT",
            )
        assertThat(result).allSatisfy {
            assertThat(it.severity).isEqualTo(InspectionSeverity.WARN)
        }
    }

    @Test
    fun `reports only substitution players that are absent from the lineup`() {
        val result =
            inspector.inspect(
                root(
                    lineups = listOf(lineup(1, start = startingEleven(11), bench = listOf(22)), lineup(2, start = startingEleven(31), bench = listOf(42))),
                    events = listOf(event(1, "Goal", 99, 98), event(1, "subst", 99, 11), event(1, "subst", 11, 99)),
                ),
            )

        assertThat(result.map { it.inspectionRuleCode.value })
            .containsExactlyInAnyOrder(
                "EVENT_PLAYER_NOT_IN_LINEUP",
                "EVENT_ASSIST_NOT_IN_LINEUP",
                "SUBSTITUTION_PLAYER_NOT_IN_LINEUP",
                "SUBSTITUTION_PLAYER_NOT_IN_LINEUP",
            )
    }

    @Test
    fun `stops only failed team active set checks and continues other team`() {
        val result =
            inspector.inspect(
                root(
                    lineups = listOf(lineup(1, start = startingEleven(11), bench = listOf(30, 31)), lineup(2, start = startingEleven(101), bench = listOf(120))),
                    events = listOf(event(1, "subst", 99, 30), event(1, "subst", 11, 31), event(2, "subst", 102, 101)),
                ),
            )

        assertThat(result.map { it.inspectionRuleCode.value })
            .containsExactlyInAnyOrder(
                "SUBSTITUTION_PLAYER_NOT_IN_LINEUP",
                "SUBSTITUTION_PLAYERS_CANNOT_BE_APPLIED",
                "SUBSTITUTION_PLAYERS_CANNOT_BE_APPLIED",
            )
    }

    @Test
    fun `skips cross reference when player sets or team groups are ambiguous`() {
        val duplicateTeamGroups = listOf(statistics(1, 11 to false), statistics(1, 12 to true))
        val duplicateLineupPlayers = listOf(lineup(1, start = listOf(11), bench = listOf(11)), lineup(2, start = listOf(21), bench = listOf(22)))

        val result =
            inspector.inspect(
                root(
                    lineups = duplicateLineupPlayers,
                    players = duplicateTeamGroups,
                    events = listOf(event(1, "Goal", 99)),
                ),
            )

        assertThat(result).isEmpty()
    }

    @Test
    fun `skips null IDs and unknown team groups`() {
        val result =
            inspector.inspect(
                root(
                    lineups = listOf(lineup(1, start = listOf(11), bench = listOf(12)), lineup(2, start = listOf(21), bench = listOf(22))),
                    players = listOf(statistics(3, 99 to false)),
                    events = listOf(ApiSportsFixtureSingleEventDto(team = ApiSportsFixtureSingleEventTeamDto(1), type = "Goal", player = ApiSportsFixtureSingleEventPersonDto(id = null))),
                ),
            )

        assertThat(result).isEmpty()
    }

    @Test
    fun `does not infer lineup membership from a lineup containing null player ID`() {
        val result =
            inspector.inspect(
                root(
                    lineups = listOf(lineupWithEntries(1, start = listOf(11, null), bench = listOf(12)), lineup(2, start = listOf(21), bench = listOf(22))),
                    players = listOf(statistics(1, 11 to false, 12 to true, 13 to false), statistics(2, 21 to false, 22 to true)),
                ),
            )

        assertThat(result.map { it.inspectionRuleCode.value }).doesNotContain("PLAYER_STATISTICS_PLAYER_NOT_IN_LINEUP")
    }

    @Test
    fun `does not infer player statistics membership from statistics containing null player ID`() {
        val result =
            inspector.inspect(
                root(
                    lineups = listOf(lineup(1, start = listOf(11), bench = listOf(12, 13)), lineup(2, start = listOf(21), bench = listOf(22))),
                    players = listOf(statisticsWithEntries(1, 11L to false, 12L to true, null to null), statistics(2, 21 to false, 22 to true)),
                ),
            )

        assertThat(result.map { it.inspectionRuleCode.value }).doesNotContain("LINEUP_PLAYER_MISSING_FROM_PLAYER_STATISTICS")
    }

    @Test
    fun `skips active set checks for incomplete starting eleven`() {
        val cases =
            listOf(
                lineupWithEntries(1, start = null, bench = listOf(30)),
                lineupWithEntries(1, start = emptyList(), bench = listOf(30)),
                lineup(1, start = listOf(11, 12), bench = listOf(30)),
                lineup(1, start = listOf(11L, 11L) + (12L..20L).toList(), bench = listOf(30)),
            )

        cases.forEach { homeLineup ->
            val result =
                inspector.inspect(
                    root(
                        lineups = listOf(homeLineup, lineup(2, start = listOf(21), bench = listOf(22))),
                        events = listOf(event(1, "subst", 99, 30)),
                    ),
                )

            assertThat(result.map { it.inspectionRuleCode.value }).doesNotContain("SUBSTITUTION_PLAYERS_CANNOT_BE_APPLIED")
        }
    }

    @Test
    fun `does not infer bench absence when substitutes contain null player ID`() {
        val result =
            inspector.inspect(
                root(
                    lineups = listOf(lineupWithEntries(1, start = listOf(11), bench = listOf(12, null)), lineup(2, start = listOf(21), bench = listOf(22))),
                    events = listOf(event(1, "subst", 11, 13)),
                ),
            )

        assertThat(result.map { it.inspectionRuleCode.value }).doesNotContain("SUBSTITUTION_PLAYER_NOT_IN_LINEUP")
    }

    @Test
    fun `does not use empty or incomplete starting eleven for lineup membership rules`() {
        val incompleteLineups =
            listOf(
                lineupWithEntries(1, start = emptyList(), bench = listOf(30)),
                lineup(1, start = listOf(11, 12), bench = listOf(30)),
            )

        incompleteLineups.forEach { homeLineup ->
            val result =
                inspector.inspect(
                    root(
                        lineups = listOf(homeLineup, lineup(2, start = listOf(21), bench = listOf(22))),
                        players = listOf(statisticsForIds(1, listOf(99), emptyMap())),
                        events = listOf(event(1, "Goal", 99, 98), event(1, "subst", 99, 98)),
                    ),
                )

            assertThat(result.map { it.inspectionRuleCode.value })
                .doesNotContain(
                    "PLAYER_STATISTICS_PLAYER_NOT_IN_LINEUP",
                    "EVENT_PLAYER_NOT_IN_LINEUP",
                    "EVENT_ASSIST_NOT_IN_LINEUP",
                    "SUBSTITUTION_PLAYER_NOT_IN_LINEUP",
                )
        }
    }

    @Test
    fun `compares event assist only for goal events`() {
        val result =
            inspector.inspect(
                root(
                    lineups = listOf(lineup(1, start = listOf(11), bench = listOf(12)), lineup(2, start = listOf(21), bench = listOf(22))),
                    events = listOf(event(1, "Card", 11, 99)),
                ),
            )

        assertThat(result.map { it.inspectionRuleCode.value }).doesNotContain("EVENT_ASSIST_NOT_IN_LINEUP")
    }

    @Test
    fun `does not compare scores or statistic totals`() {
        val response =
            response(
                lineups = listOf(lineup(1, start = listOf(11), bench = listOf(12)), lineup(2, start = listOf(21), bench = listOf(22))),
                players = listOf(statistics(1, 11 to false, 12 to true), statistics(2, 21 to false, 22 to true)),
                events = listOf(event(1, "Goal", 11)),
            ).copy(
                goals = ApiSportsFixtureSingleScorePairDto(home = 5, away = 0),
                score = ApiSportsFixtureSingleScoreDto(fulltime = ApiSportsFixtureSingleScorePairDto(home = 0, away = 0)),
            )

        assertThat(inspector.inspect(ApiSportsFixtureSingleRootDto(response = listOf(response)))).isEmpty()
    }

    private fun root(
        lineups: List<ApiSportsFixtureSingleLineupDto?>,
        players: List<ApiSportsFixtureSinglePlayerStatisticsTeamDto?>? = null,
        events: List<ApiSportsFixtureSingleEventDto?>? = null,
    ): ApiSportsFixtureSingleRootDto = ApiSportsFixtureSingleRootDto(response = listOf(response(lineups, players, events)))

    private fun response(
        lineups: List<ApiSportsFixtureSingleLineupDto?>,
        players: List<ApiSportsFixtureSinglePlayerStatisticsTeamDto?>? = null,
        events: List<ApiSportsFixtureSingleEventDto?>? = null,
    ): ApiSportsFixtureSingleResponseDto =
        ApiSportsFixtureSingleResponseDto(
            teams = ApiSportsFixtureSingleTeamsDto(home = ApiSportsFixtureSingleTeamDto(id = 1), away = ApiSportsFixtureSingleTeamDto(id = 2)),
            lineups = lineups,
            players = players,
            events = events,
        )

    private fun lineup(teamId: Long, start: List<Long>, bench: List<Long>): ApiSportsFixtureSingleLineupDto =
        lineupWithEntries(teamId, start, bench)

    private fun lineupWithEntries(teamId: Long, start: List<Long?>?, bench: List<Long?>?): ApiSportsFixtureSingleLineupDto =
        ApiSportsFixtureSingleLineupDto(
            team = ApiSportsFixtureSingleLineupTeamDto(id = teamId),
            startXI = start?.map { ApiSportsFixtureSingleLineupPlayerEntryDto(ApiSportsFixtureSingleLineupPlayerDto(id = it)) },
            substitutes = bench?.map { ApiSportsFixtureSingleLineupPlayerEntryDto(ApiSportsFixtureSingleLineupPlayerDto(id = it)) },
        )

    private fun statistics(teamId: Long, vararg players: Pair<Int, Boolean?>): ApiSportsFixtureSinglePlayerStatisticsTeamDto =
        statisticsWithEntries(teamId, *players.map { (playerId, substitute) -> playerId.toLong() to substitute }.toTypedArray())

    private fun statisticsWithEntries(teamId: Long, vararg players: Pair<Long?, Boolean?>): ApiSportsFixtureSinglePlayerStatisticsTeamDto =
        ApiSportsFixtureSinglePlayerStatisticsTeamDto(
            team = ApiSportsFixtureSingleEventTeamDto(teamId),
            players =
                players.map { (playerId, substitute) ->
                    ApiSportsFixtureSinglePlayerStatisticsDto(
                        player = ApiSportsFixtureSinglePlayerStatisticsPlayerDto(playerId),
                        statistics = listOf(ApiSportsFixtureSinglePlayerStatisticEntryDto(games = ApiSportsFixtureSinglePlayerGamesDto(substitute = substitute))),
                    )
                },
        )

    private fun statisticsForIds(
        teamId: Long,
        playerIds: List<Long>,
        substituteByPlayerId: Map<Long, Boolean?>,
    ): ApiSportsFixtureSinglePlayerStatisticsTeamDto =
        ApiSportsFixtureSinglePlayerStatisticsTeamDto(
            team = ApiSportsFixtureSingleEventTeamDto(teamId),
            players =
                playerIds.map { playerId ->
                    ApiSportsFixtureSinglePlayerStatisticsDto(
                        player = ApiSportsFixtureSinglePlayerStatisticsPlayerDto(playerId),
                        statistics = listOf(ApiSportsFixtureSinglePlayerStatisticEntryDto(games = ApiSportsFixtureSinglePlayerGamesDto(substitute = substituteByPlayerId[playerId]))),
                    )
                },
        )

    private fun startingEleven(firstPlayerId: Long): List<Long> = (firstPlayerId until firstPlayerId + 11).toList()

    private fun event(teamId: Long, type: String, playerId: Long, assistId: Long? = null): ApiSportsFixtureSingleEventDto =
        ApiSportsFixtureSingleEventDto(
            team = ApiSportsFixtureSingleEventTeamDto(teamId),
            type = type,
            player = ApiSportsFixtureSingleEventPersonDto(playerId),
            assist = assistId?.let(::ApiSportsFixtureSingleEventPersonDto),
        )
}
