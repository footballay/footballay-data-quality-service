package com.footballay.dataquality.inspection.apisports.fixturesingle

import com.footballay.dataquality.inspection.InspectionSeverity
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("footballay.data-quality.fixture-single.lineup-availability")
data class LineupAvailabilityProperties(
    val leagues: Map<Long, LeagueRule> = emptyMap(),
    val notStartedGraceMinutes: Long = 3,
) {
    init {
        require(notStartedGraceMinutes >= 0) {
            "Lineup availability notStartedGraceMinutes must not be negative: $notStartedGraceMinutes"
        }
    }

    data class LeagueRule(
        val expectedBeforeKickoffMinutes: Long = 0,
        val missingBeforeKickoffSeverity: InspectionSeverity = InspectionSeverity.WARN,
        val missingAfterKickoffSeverity: InspectionSeverity = InspectionSeverity.ERROR,
    ) {
        init {
            require(expectedBeforeKickoffMinutes >= 0) {
                "Lineup availability expectedBeforeKickoffMinutes must not be negative: $expectedBeforeKickoffMinutes"
            }
        }
    }
}
