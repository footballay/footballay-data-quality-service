package com.footballay.dataquality.inspection.apisports.fixturesingle

import com.footballay.dataquality.inspection.InspectionSeverity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.time.Instant

class LineupAvailabilityInspectorTest {
    private val inspector = inspector()

    @Test
    fun `allows unconfigured leagues and responses with lineups`() {
        assertThat(inspector.inspect(target(leagueId = 140L, collectedAt = "2026-07-12T11:40:00Z"))).isEmpty()
        assertThat(inspector.inspect(target(lineups = listOf(ApiSportsFixtureSingleLineupDto()), collectedAt = "2026-07-12T12:00:00Z"))).isEmpty()
    }

    @Test
    fun `allows missing lineups before the configured threshold`() {
        assertThat(inspector.inspect(target(collectedAt = "2026-07-12T11:29:59Z"))).isEmpty()
        assertThat(inspector.inspect(target(lineups = emptyList(), collectedAt = "2026-07-12T11:29:59Z"))).isEmpty()
    }

    @Test
    fun `reports a warning when lineups are missing after the before kickoff threshold`() {
        val issue = inspector.inspect(target(lineups = emptyList(), collectedAt = "2026-07-12T11:30:00Z")).single()

        assertThat(issue.inspectionRuleCode.value).isEqualTo("LINEUP_NOT_AVAILABLE_BEFORE_KICKOFF")
        assertThat(issue.severity).isEqualTo(InspectionSeverity.WARN)
        assertThat(issue.responseLocation.section).isEqualTo("lineups")
        assertThat(issue.responseLocation.path).isEqualTo("$.response[0].lineups")
        assertThat(issue.evidence?.values)
            .containsEntry("leagueId", "39")
            .containsEntry("expectedBeforeKickoffMinutes", "30")
            .containsEntry("collectedAt", "2026-07-12T11:30:00Z")
    }

    @Test
    fun `uses only fixed collected at values when evaluating the grace deadline`() {
        val firstResult = inspector.inspect(target(collectedAt = "2026-07-12T12:03:00Z"))
        val repeatedResult = inspector.inspect(target(collectedAt = "2026-07-12T12:03:00Z"))

        assertThat(firstResult.map { it.inspectionRuleCode.value to it.severity })
            .containsExactly("LINEUP_NOT_AVAILABLE_AFTER_KICKOFF" to InspectionSeverity.ERROR)
        assertThat(repeatedResult).isEqualTo(firstResult)
    }

    @Test
    fun `treats started and finished statuses as after kickoff before the availability threshold`() {
        val started = inspector.inspect(target(statusShort = "1H", collectedAt = "2026-07-12T11:00:00Z")).single()
        val finished = inspector.inspect(target(statusShort = "FT", collectedAt = "2026-07-12T11:00:00Z")).single()

        assertThat(started.inspectionRuleCode.value).isEqualTo("LINEUP_NOT_AVAILABLE_AFTER_KICKOFF")
        assertThat(finished.inspectionRuleCode.value).isEqualTo("LINEUP_NOT_AVAILABLE_AFTER_KICKOFF")
        assertThat(started.severity).isEqualTo(InspectionSeverity.ERROR)
        assertThat(finished.severity).isEqualTo(InspectionSeverity.ERROR)
    }

    @Test
    fun `keeps NS availability at kickoff and within the three minute grace period as before kickoff`() {
        val atKickoff = inspector.inspect(target(collectedAt = "2026-07-12T12:00:00Z")).single()
        val beforeGraceDeadline = inspector.inspect(target(collectedAt = "2026-07-12T12:02:59Z")).single()

        assertThat(atKickoff.inspectionRuleCode.value).isEqualTo("LINEUP_NOT_AVAILABLE_BEFORE_KICKOFF")
        assertThat(atKickoff.severity).isEqualTo(InspectionSeverity.WARN)
        assertThat(beforeGraceDeadline.inspectionRuleCode.value).isEqualTo("LINEUP_NOT_AVAILABLE_BEFORE_KICKOFF")
        assertThat(beforeGraceDeadline.severity).isEqualTo(InspectionSeverity.WARN)
    }

    @Test
    fun `uses the configured grace period boundary for NS availability`() {
        val fiveMinuteGraceInspector = inspector(notStartedGraceMinutes = 5)

        val beforeDeadline = fiveMinuteGraceInspector.inspect(target(collectedAt = "2026-07-12T12:04:59Z")).single()
        val atDeadline = fiveMinuteGraceInspector.inspect(target(collectedAt = "2026-07-12T12:05:00Z")).single()

        assertThat(beforeDeadline.inspectionRuleCode.value).isEqualTo("LINEUP_NOT_AVAILABLE_BEFORE_KICKOFF")
        assertThat(atDeadline.inspectionRuleCode.value).isEqualTo("LINEUP_NOT_AVAILABLE_AFTER_KICKOFF")
        assertThat(atDeadline.severity).isEqualTo(InspectionSeverity.ERROR)
    }

    @Test
    fun `does not infer availability after kickoff for special non-started or missing statuses`() {
        listOf("PST", "CANC", "TBD", null, " ").forEach { statusShort ->
            assertThat(inspector.inspect(target(statusShort = statusShort, collectedAt = "2026-07-12T12:10:00Z"))).isEmpty()
        }
    }

    @Test
    fun `rejects a negative not started grace period`() {
        assertThatIllegalArgumentException()
            .isThrownBy { LineupAvailabilityProperties(notStartedGraceMinutes = -1) }
            .withMessage("Lineup availability notStartedGraceMinutes must not be negative: -1")
    }

    @Test
    fun `does not infer availability when league kickoff or collection time is unavailable`() {
        assertThat(inspector.inspect(target(leagueId = null, collectedAt = "2026-07-12T11:40:00Z"))).isEmpty()
        assertThat(inspector.inspect(target(leagueId = 0L, collectedAt = "2026-07-12T11:40:00Z"))).isEmpty()
        assertThat(inspector.inspect(target(kickoffTimestamp = null, collectedAt = "2026-07-12T11:40:00Z"))).isEmpty()
        assertThat(inspector.inspect(target(kickoffTimestamp = 0L, collectedAt = "2026-07-12T11:40:00Z"))).isEmpty()
        assertThat(inspector.inspect(target(collectedAt = null))).isEmpty()
    }

    private fun target(
        leagueId: Long? = 39L,
        kickoffTimestamp: Long? = 1783857600L,
        statusShort: String? = "NS",
        lineups: List<ApiSportsFixtureSingleLineupDto?>? = null,
        collectedAt: String?,
    ): FixtureSingleLineupAvailabilityTarget =
        FixtureSingleLineupAvailabilityTarget(
            fixtureSingle =
                ApiSportsFixtureSingleRootDto(
                    response =
                        listOf(
                            ApiSportsFixtureSingleResponseDto(
                                fixture =
                                    ApiSportsFixtureSingleFixtureDto(
                                        timestamp = kickoffTimestamp,
                                        status = ApiSportsFixtureSingleStatusDto(short = statusShort),
                                    ),
                                league = ApiSportsFixtureSingleLeagueDto(id = leagueId),
                                lineups = lineups,
                            ),
                        ),
                ),
            collectedAt = collectedAt?.let(Instant::parse),
        )

    private fun inspector(notStartedGraceMinutes: Long = 3): LineupAvailabilityInspector =
        LineupAvailabilityInspector(
            LineupAvailabilityProperties(
                leagues =
                    mapOf(
                        39L to
                            LineupAvailabilityProperties.LeagueRule(
                                expectedBeforeKickoffMinutes = 30,
                                missingBeforeKickoffSeverity = InspectionSeverity.WARN,
                                missingAfterKickoffSeverity = InspectionSeverity.ERROR,
                            ),
                    ),
                notStartedGraceMinutes = notStartedGraceMinutes,
            ),
        )
}
