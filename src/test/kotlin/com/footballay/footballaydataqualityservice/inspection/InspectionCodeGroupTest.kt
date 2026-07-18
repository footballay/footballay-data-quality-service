package com.footballay.footballaydataqualityservice.inspection

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test

class InspectionCodeGroupTest {
    @Test
    fun `defines different codes in the same group`() {
        val group = InspectionCodeGroup("APISPORTS", "FIXTURE_SINGLE")

        val first = group.define("PLAYER_ID_NULL")
        val second = group.define("TEAM_ID_NULL")

        assertThat(first).isEqualTo(InspectionCode("APISPORTS", "FIXTURE_SINGLE", "PLAYER_ID_NULL"))
        assertThat(second).isEqualTo(InspectionCode("APISPORTS", "FIXTURE_SINGLE", "TEAM_ID_NULL"))
    }

    @Test
    fun `rejects a duplicated code in the same group`() {
        val group = InspectionCodeGroup("APISPORTS", "FIXTURE_SINGLE")
        group.define("PLAYER_ID_NULL")

        assertThatIllegalStateException()
            .isThrownBy { group.define("PLAYER_ID_NULL") }
            .withMessage("Duplicated inspection code: APISPORTS:FIXTURE_SINGLE:PLAYER_ID_NULL")
    }

    @Test
    fun `allows the same local code in different groups`() {
        val fixtureCode = InspectionCodeGroup("APISPORTS", "FIXTURE_SINGLE").define("INVALID_TEAM_LOGO")
        val standingCode = InspectionCodeGroup("APISPORTS", "LEAGUE_STANDING").define("INVALID_TEAM_LOGO")

        assertThat(fixtureCode).isNotEqualTo(standingCode)
    }

    @Test
    fun `rejects invalid provider endpoint and code formats`() {
        assertThatIllegalArgumentException().isThrownBy { InspectionCodeGroup("apiSports", "FIXTURE_SINGLE") }
        assertThatIllegalArgumentException().isThrownBy { InspectionCodeGroup("APISPORTS", "fixtureSingle") }
        assertThatIllegalArgumentException()
            .isThrownBy { InspectionCodeGroup("APISPORTS", "FIXTURE_SINGLE").define("player-id-null") }
    }

    @Test
    fun `allows multiple findings to use the same defined code`() {
        val code = InspectionCodeGroup("APISPORTS", "FIXTURE_SINGLE").define("PLAYER_ID_NULL")

        val foundIssues =
            listOf(
                issue(code, "$.response[0].player.id"),
                issue(code, "$.response[1].player.id"),
            )

        assertThat(foundIssues).hasSize(2)
        assertThat(foundIssues.map(FoundIssue::inspectionCode)).containsOnly(code)
    }

    private fun issue(
        code: InspectionCode,
        path: String,
    ): FoundIssue =
        FoundIssue(
            inspectionCode = code,
            severity = InspectionSeverity.ERROR,
            title = "Player ID is null",
            responseLocation = InspectionResponseLocation("players", path),
        )
}
