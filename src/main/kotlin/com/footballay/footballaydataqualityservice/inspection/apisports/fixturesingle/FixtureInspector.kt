package com.footballay.footballaydataqualityservice.inspection.apisports.fixturesingle

import com.footballay.footballaydataqualityservice.inspection.FoundIssue
import com.footballay.footballaydataqualityservice.inspection.InspectionEvidence
import com.footballay.footballaydataqualityservice.inspection.InspectionRuleCode
import com.footballay.footballaydataqualityservice.inspection.InspectionSeverity
import com.footballay.footballaydataqualityservice.inspection.InspectionScope
import com.footballay.footballaydataqualityservice.inspection.QualityInspector
import com.footballay.footballaydataqualityservice.inspection.QualityIssueType
import java.time.OffsetDateTime

/** API Sports Fixture Single fixture 기본 정보와 시간 정보를 검사한다. */
class FixtureInspector : QualityInspector<ApiSportsFixtureSingleRootDto> {
    override val inspectionScope: InspectionScope = FixtureSingleInspectionScope.FIXTURE

    override fun inspect(target: ApiSportsFixtureSingleRootDto): List<FoundIssue> {
        val root = target
        val findings = mutableListOf<FoundIssue>()

        root.response.orEmpty().forEachIndexed { index, response ->
            if (response == null) return@forEachIndexed
            val fixture = response.fixture
            val fixturePath = "$.response[$index].fixture"
            if (fixture == null) {
                findings +=
                    issue(
                        Code.OBJECT_NULL,
                        InspectionSeverity.CRITICAL,
                        "Fixture object is null",
                        fixturePath,
                        evidence("responseIndex" to index.toString()),
                    )
                return@forEachIndexed
            }

            inspectIdentifier(findings, fixture, fixturePath)
            inspectTime(findings, fixture, fixturePath)
            inspectVenueAndReferee(findings, fixture, fixturePath)
        }
        return findings
    }

    private fun inspectIdentifier(
        findings: MutableList<FoundIssue>,
        fixture: ApiSportsFixtureSingleFixtureDto,
        fixturePath: String,
    ) {
        when {
            fixture.id == null ->
                findings +=
                    issue(
                        Code.ID_NULL,
                        InspectionSeverity.CRITICAL,
                        "Fixture ID is null",
                        "$fixturePath.id",
                        evidence("fixtureId" to null),
                    )
            fixture.id <= 0 ->
                findings +=
                    issue(
                        Code.ID_NON_POSITIVE,
                        InspectionSeverity.ERROR,
                        "Fixture ID is not positive",
                        "$fixturePath.id",
                        evidence("fixtureId" to fixture.id.toString()),
                    )
        }
    }

    private fun inspectTime(
        findings: MutableList<FoundIssue>,
        fixture: ApiSportsFixtureSingleFixtureDto,
        fixturePath: String,
    ) {
        when {
            fixture.timezone == null ->
                findings +=
                    issue(
                        Code.TIMEZONE_NULL,
                        InspectionSeverity.WARN,
                        "Fixture timezone is null",
                        "$fixturePath.timezone",
                        evidence("timezone" to null),
                    )
            fixture.timezone.isBlank() ->
                findings +=
                    issue(
                        Code.TIMEZONE_BLANK,
                        InspectionSeverity.WARN,
                        "Fixture timezone is blank",
                        "$fixturePath.timezone",
                        evidence("timezone" to fixture.timezone),
                    )
        }

        val kickoff = fixture.date?.let { runCatching { OffsetDateTime.parse(it).toInstant().epochSecond }.getOrNull() }
        when {
            fixture.date == null ->
                findings +=
                    issue(
                        Code.DATE_NULL,
                        InspectionSeverity.ERROR,
                        "Fixture date is null",
                        "$fixturePath.date",
                        evidence("date" to null),
                    )
            kickoff == null ->
                findings +=
                    issue(
                        Code.DATE_INVALID_FORMAT,
                        InspectionSeverity.ERROR,
                        "Fixture date format is invalid",
                        "$fixturePath.date",
                        evidence("date" to fixture.date),
                    )
        }
        when {
            fixture.timestamp == null ->
                findings +=
                    issue(
                        Code.TIMESTAMP_NULL,
                        InspectionSeverity.ERROR,
                        "Fixture timestamp is null",
                        "$fixturePath.timestamp",
                        evidence("timestamp" to null),
                    )
            fixture.timestamp <= 0 ->
                findings +=
                    issue(
                        Code.TIMESTAMP_NON_POSITIVE,
                        InspectionSeverity.ERROR,
                        "Fixture timestamp is not positive",
                        "$fixturePath.timestamp",
                        evidence("timestamp" to fixture.timestamp.toString()),
                    )
            kickoff != null && kickoff != fixture.timestamp ->
                findings +=
                    issue(
                        Code.DATE_TIMESTAMP_MISMATCH,
                        InspectionSeverity.ERROR,
                        "Fixture date and timestamp do not match",
                        fixturePath,
                        evidence(
                            "date" to fixture.date,
                            "timestamp" to fixture.timestamp.toString(),
                            "parsedDateEpochSecond" to kickoff.toString(),
                        ),
                    )
        }

        val periods = fixture.periods ?: run {
            findings +=
                issue(
                    Code.PERIODS_NULL,
                    InspectionSeverity.WARN,
                    "Fixture periods are null",
                    "$fixturePath.periods",
                    evidence("fixtureId" to fixture.id?.toString()),
                )
            return
        }
        if (periods.first != null && periods.first <= 0) {
            findings +=
                issue(
                    Code.FIRST_PERIOD_TIMESTAMP_INVALID,
                    InspectionSeverity.WARN,
                    "First period timestamp is invalid",
                    "$fixturePath.periods.first",
                    evidence("firstPeriodTimestamp" to periods.first.toString()),
                )
        }
        if (periods.second != null && periods.second <= 0) {
            findings +=
                issue(
                    Code.SECOND_PERIOD_TIMESTAMP_INVALID,
                    InspectionSeverity.WARN,
                    "Second period timestamp is invalid",
                    "$fixturePath.periods.second",
                    evidence("secondPeriodTimestamp" to periods.second.toString()),
                )
        }
        if (periods.first != null && periods.second != null && periods.second < periods.first) {
            findings +=
                issue(
                    Code.SECOND_PERIOD_BEFORE_FIRST,
                    InspectionSeverity.ERROR,
                    "Second period is before first period",
                    "$fixturePath.periods.second",
                    evidence(
                        "firstPeriodTimestamp" to periods.first.toString(),
                        "secondPeriodTimestamp" to periods.second.toString(),
                    ),
                )
        }
        if (kickoff != null && periods.first != null && periods.first < kickoff) {
            findings +=
                issue(
                    Code.FIRST_PERIOD_BEFORE_KICKOFF,
                    InspectionSeverity.WARN,
                    "First period is before kickoff",
                    "$fixturePath.periods.first",
                    evidence(
                        "firstPeriodTimestamp" to periods.first.toString(),
                        "kickoffTimestamp" to kickoff.toString(),
                    ),
                )
        }
        if (kickoff != null && periods.second != null && periods.second < kickoff) {
            findings +=
                issue(
                    Code.SECOND_PERIOD_BEFORE_KICKOFF,
                    InspectionSeverity.ERROR,
                    "Second period is before kickoff",
                    "$fixturePath.periods.second",
                    evidence(
                        "secondPeriodTimestamp" to periods.second.toString(),
                        "kickoffTimestamp" to kickoff.toString(),
                    ),
                )
        }
    }

    private fun inspectVenueAndReferee(
        findings: MutableList<FoundIssue>,
        fixture: ApiSportsFixtureSingleFixtureDto,
        fixturePath: String,
    ) {
        if (fixture.referee?.isBlank() == true) {
            findings +=
                issue(
                    Code.REFEREE_BLANK,
                    InspectionSeverity.INFO,
                    "Fixture referee is blank",
                    "$fixturePath.referee",
                    evidence("referee" to fixture.referee),
                )
        }
        val venue = fixture.venue ?: return
        if (venue.id != null && venue.id <= 0) {
            findings +=
                issue(
                    Code.VENUE_ID_NON_POSITIVE,
                    InspectionSeverity.WARN,
                    "Venue ID is not positive",
                    "$fixturePath.venue.id",
                    evidence("venueId" to venue.id.toString()),
                )
        }
        if (venue.name?.isBlank() == true) {
            findings +=
                issue(
                    Code.VENUE_NAME_BLANK,
                    InspectionSeverity.WARN,
                    "Venue name is blank",
                    "$fixturePath.venue.name",
                    evidence("venueName" to venue.name),
                )
        }
        if (venue.city?.isBlank() == true) {
            findings +=
                issue(
                    Code.VENUE_CITY_BLANK,
                    InspectionSeverity.INFO,
                    "Venue city is blank",
                    "$fixturePath.venue.city",
                    evidence("venueCity" to venue.city),
                )
        }
    }

    private fun issue(
        code: Code,
        severity: InspectionSeverity,
        title: String,
        path: String,
        evidence: InspectionEvidence? = null,
    ): FoundIssue = fixtureSingleFoundIssue(code.suggestedType, inspectionScope, code, severity, title, "fixture", path, evidence)

    private fun evidence(vararg entries: Pair<String, String?>): InspectionEvidence = fixtureSingleEvidence(*entries)

    private enum class Code(
        val suggestedType: QualityIssueType,
    ) : InspectionRuleCode {
        OBJECT_NULL(QualityIssueType.UNEXPECTED_NULL),
        ID_NULL(QualityIssueType.UNEXPECTED_NULL),
        ID_NON_POSITIVE(QualityIssueType.OUT_OF_RANGE),
        TIMEZONE_NULL(QualityIssueType.UNEXPECTED_NULL),
        TIMEZONE_BLANK(QualityIssueType.UNEXPECTED_BLANK),
        DATE_NULL(QualityIssueType.UNEXPECTED_NULL),
        DATE_INVALID_FORMAT(QualityIssueType.INVALID_FORMAT),
        TIMESTAMP_NULL(QualityIssueType.UNEXPECTED_NULL),
        TIMESTAMP_NON_POSITIVE(QualityIssueType.OUT_OF_RANGE),
        DATE_TIMESTAMP_MISMATCH(QualityIssueType.INCONSISTENT_INFORMATION),
        PERIODS_NULL(QualityIssueType.UNEXPECTED_NULL),
        FIRST_PERIOD_TIMESTAMP_INVALID(QualityIssueType.OUT_OF_RANGE),
        SECOND_PERIOD_TIMESTAMP_INVALID(QualityIssueType.OUT_OF_RANGE),
        SECOND_PERIOD_BEFORE_FIRST(QualityIssueType.INCONSISTENT_INFORMATION),
        FIRST_PERIOD_BEFORE_KICKOFF(QualityIssueType.INCONSISTENT_INFORMATION),
        SECOND_PERIOD_BEFORE_KICKOFF(QualityIssueType.INCONSISTENT_INFORMATION),
        REFEREE_BLANK(QualityIssueType.UNEXPECTED_BLANK),
        VENUE_ID_NON_POSITIVE(QualityIssueType.OUT_OF_RANGE),
        VENUE_NAME_BLANK(QualityIssueType.UNEXPECTED_BLANK),
        VENUE_CITY_BLANK(QualityIssueType.UNEXPECTED_BLANK),
        ;

        override val value: String
            get() = name
    }
}
