package com.footballay.dataquality.inspection.apisports.fixturesingle

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.dataquality.inspection.InspectionSeverity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EventsInspectorTest {
    private val objectMapper = ObjectMapper()
    private val inspector = EventsInspector()

    @Test
    fun `allows absent events and skips a null response item`() {
        val issues = inspector.inspect(targetOf("""{"response":[null,{"events":null}]}"""))

        assertThat(issues).isEmpty()
    }

    @Test
    fun `reports a null event object while keeping null responses as root responsibility`() {
        val issues = inspector.inspect(targetOf("""{"response":[null,{"events":[null]}]}"""))

        assertThat(issues.map { it.inspectionRuleCode.value }).containsExactly("EVENT_OBJECT_NULL")
        assertThat(issues.single().severity).isEqualTo(InspectionSeverity.CRITICAL)
    }

    @Test
    fun `reports common field issues and does not duplicate unknown type for blank type`() {
        val issues = inspector.inspect(targetOf("""{"response":[{"teams":{"home":{"id":1},"away":{"id":2}},"events":[{"time":{"elapsed":-1,"extra":-2},"team":{"id":3},"type":"New Type","detail":"Value"},{"time":null,"team":null,"type":" ","detail":null}]}]}"""))

        assertThat(issues.map { it.inspectionRuleCode.value })
            .containsExactly(
                "EVENT_TEAM_NOT_FIXTURE_TEAM",
                "EVENT_TIME_INVALID",
                "EVENT_TIME_INVALID",
                "UNKNOWN_EVENT_TYPE",
                "EVENT_TEAM_ID_MISSING",
                "EVENT_TIME_MISSING",
                "EVENT_TYPE_MISSING",
            )
        assertThat(issues.filter { it.inspectionRuleCode.value == "UNKNOWN_EVENT_TYPE" }.map { it.severity }).containsOnly(InspectionSeverity.WARN)
    }

    @Test
    fun `compares an event team only with two distinct valid fixture teams`() {
        val skippedIssues = inspector.inspect(targetOf("""{"response":[{"teams":{"home":{"id":1},"away":{"id":null}},"events":[{"time":{"elapsed":1},"team":{"id":9},"type":"Var","detail":"Review"}]}]}"""))
        val relationIssues = inspector.inspect(targetOf("""{"response":[{"teams":{"home":{"id":1},"away":{"id":2}},"events":[{"time":{"elapsed":1},"team":{"id":9},"type":"Var","detail":"Review"}]}]}"""))

        assertThat(skippedIssues).isEmpty()
        assertThat(relationIssues.map { it.inspectionRuleCode.value }).containsExactly("EVENT_TEAM_NOT_FIXTURE_TEAM")
    }

    @Test
    fun `allows known yellow red card detail`() {
        val issues = inspector.inspect(targetOf("""{"response":[{"events":[{"time":{"elapsed":1},"team":{"id":1},"type":"Card","detail":"Yellow-Red Card","player":{"id":10,"name":"Player"}}]}]}"""))

        assertThat(issues).isEmpty()
    }

    @Test
    fun `reports missing and invalid player identifiers by issue type`() {
        val issues = inspector.inspect(targetOf("""{"response":[{"events":[{"time":{"elapsed":1},"team":{"id":1},"type":"Goal","detail":"Normal Goal","player":{"id":null,"name":"Player"},"assist":{"id":0,"name":"Assist"}},{"time":{"elapsed":2},"team":{"id":1},"type":"Card","detail":"Yellow Card","player":{"id":0,"name":"Player"}},{"time":{"elapsed":3},"team":{"id":1},"type":"subst","detail":"Substitution 1","player":{"id":-1,"name":"Out"},"assist":{"id":null,"name":"In"}}]}]}"""))

        assertThat(issues.map { it.inspectionRuleCode.value })
            .containsExactly(
                "GOAL_PLAYER_ID_MISSING",
                "GOAL_ASSIST_REFERENCE_PARTIAL",
                "CARD_PLAYER_ID_MISSING_OR_INVALID",
                "SUBSTITUTION_OUT_PLAYER_ID_MISSING_OR_INVALID",
                "SUBSTITUTION_IN_PLAYER_ID_MISSING_OR_INVALID",
            )
        assertThat(issues.map { it.suggestedType })
            .containsExactly(
                com.footballay.dataquality.inspection.QualityIssueType.UNEXPECTED_NULL,
                com.footballay.dataquality.inspection.QualityIssueType.OUT_OF_RANGE,
                com.footballay.dataquality.inspection.QualityIssueType.OUT_OF_RANGE,
                com.footballay.dataquality.inspection.QualityIssueType.OUT_OF_RANGE,
                com.footballay.dataquality.inspection.QualityIssueType.UNEXPECTED_NULL,
            )
    }

    @Test
    fun `reports goal assist partial references at the invalid field`() {
        val issues = inspector.inspect(targetOf("""{"response":[{"events":[{"time":{"elapsed":1},"team":{"id":1},"type":"Goal","detail":"Normal Goal","player":{"id":1,"name":"Scorer"},"assist":{"id":null,"name":"Assist"}},{"time":{"elapsed":2},"team":{"id":1},"type":"Goal","detail":"Normal Goal","player":{"id":2,"name":"Scorer"},"assist":{"id":0,"name":"Assist"}},{"time":{"elapsed":3},"team":{"id":1},"type":"Goal","detail":"Normal Goal","player":{"id":3,"name":"Scorer"},"assist":{"id":4,"name":null}},{"time":{"elapsed":4},"team":{"id":1},"type":"Goal","detail":"Normal Goal","player":{"id":5,"name":"Scorer"},"assist":{"id":6,"name":" "}}]}]}"""))

        assertThat(issues.map { it.inspectionRuleCode.value })
            .containsExactly(
                "GOAL_ASSIST_REFERENCE_PARTIAL",
                "GOAL_ASSIST_REFERENCE_PARTIAL",
                "GOAL_ASSIST_REFERENCE_PARTIAL",
                "GOAL_ASSIST_REFERENCE_PARTIAL",
            )
        assertThat(issues.map { it.suggestedType })
            .containsExactly(
                com.footballay.dataquality.inspection.QualityIssueType.UNEXPECTED_NULL,
                com.footballay.dataquality.inspection.QualityIssueType.OUT_OF_RANGE,
                com.footballay.dataquality.inspection.QualityIssueType.UNEXPECTED_NULL,
                com.footballay.dataquality.inspection.QualityIssueType.UNEXPECTED_BLANK,
            )
        assertThat(issues.map { it.responseLocation.path })
            .containsExactly(
                "$.response[0].events[0].assist.id",
                "$.response[0].events[1].assist.id",
                "$.response[0].events[2].assist.name",
                "$.response[0].events[3].assist.name",
            )
    }

    @Test
    fun `reports malformed or non positive substitution detail while allowing positive numbers`() {
        val issues = inspector.inspect(targetOf("""{"response":[{"events":[{"time":{"elapsed":1},"team":{"id":1},"type":"subst","detail":"Changed","player":{"id":1,"name":"Out"},"assist":{"id":2,"name":"In"}},{"time":{"elapsed":2},"team":{"id":1},"type":"subst","detail":"Substitution 0","player":{"id":3,"name":"Out"},"assist":{"id":4,"name":"In"}},{"time":{"elapsed":3},"team":{"id":1},"type":"subst","detail":"Substitution 7","player":{"id":5,"name":"Out"},"assist":{"id":6,"name":"In"}}]}]}"""))

        assertThat(issues.map { it.inspectionRuleCode.value }).containsExactly("SUBSTITUTION_DETAIL_INVALID", "SUBSTITUTION_DETAIL_INVALID")
        assertThat(issues.map { it.suggestedType })
            .containsExactly(
                com.footballay.dataquality.inspection.QualityIssueType.INVALID_FORMAT,
                com.footballay.dataquality.inspection.QualityIssueType.OUT_OF_RANGE,
            )
    }

    @Test
    fun `reports only common detail missing for null or blank substitution detail`() {
        val issues = inspector.inspect(targetOf("""{"response":[{"events":[{"time":{"elapsed":1},"team":{"id":1},"type":"subst","detail":null,"player":{"id":1,"name":"Out"},"assist":{"id":2,"name":"In"}},{"time":{"elapsed":2},"team":{"id":1},"type":"subst","detail":" ","player":{"id":3,"name":"Out"},"assist":{"id":4,"name":"In"}}]}]}"""))

        assertThat(issues.map { it.inspectionRuleCode.value }).containsExactly("EVENT_DETAIL_MISSING", "EVENT_DETAIL_MISSING")
        assertThat(issues.map { it.suggestedType })
            .containsExactly(
                com.footballay.dataquality.inspection.QualityIssueType.UNEXPECTED_NULL,
                com.footballay.dataquality.inspection.QualityIssueType.UNEXPECTED_BLANK,
            )
        assertThat(issues.map { it.inspectionRuleCode.value }).doesNotContain("SUBSTITUTION_DETAIL_INVALID")
    }

    private fun targetOf(json: String): ApiSportsFixtureSingleRootDto =
        objectMapper.treeToValue(objectMapper.readTree(json), ApiSportsFixtureSingleRootDto::class.java)
}
