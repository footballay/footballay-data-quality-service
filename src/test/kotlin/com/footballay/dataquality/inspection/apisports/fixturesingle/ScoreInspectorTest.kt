package com.footballay.dataquality.inspection.apisports.fixturesingle

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.dataquality.inspection.InspectionSeverity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ScoreInspectorTest {
    private val objectMapper = ObjectMapper()
    private val inspector = ScoreInspector()

    @Test
    fun `allows a valid extra time score`() {
        val issues = inspector.inspect(targetOf("""{"response":[{"fixture":{"status":{"short":"AET"}},"goals":{"home":2,"away":1},"score":{"halftime":{"home":0,"away":0},"fulltime":{"home":1,"away":1},"extratime":{"home":1,"away":0},"penalty":null}}]}"""))

        assertThat(issues).isEmpty()
    }

    @Test
    fun `skips a null response item and reports null score objects independently`() {
        val issues = inspector.inspect(targetOf("""{"response":[null,{"goals":null,"score":null}]}"""))

        assertThat(issues.map { it.inspectionRuleCode.value }).containsExactly("GOALS_OBJECT_NULL", "SCORE_OBJECT_NULL")
        assertThat(issues.map { it.severity }).containsOnly(InspectionSeverity.ERROR)
    }

    @Test
    fun `reports pair and negative score rules without relationship rules for invalid values`() {
        val issues = inspector.inspect(targetOf("""{"response":[{"fixture":{"status":{"short":"FT"}},"goals":{"home":-1,"away":null},"score":{"halftime":{"home":2,"away":0},"fulltime":{"home":1,"away":1},"extratime":{"home":null,"away":0},"penalty":null}}]}"""))

        assertThat(issues.map { it.inspectionRuleCode.value })
            .containsExactly("GOALS_NEGATIVE", "FINISHED_WITHOUT_FINAL_GOALS", "SCORE_PAIR_PARTIAL", "HALFTIME_SCORE_EXCEEDS_FULLTIME")
        assertThat(issues.map { it.inspectionRuleCode.value })
            .doesNotContain("GOALS_PARTIAL", "REGULAR_TIME_SCORE_TOTAL_MISMATCH")
    }

    @Test
    fun `reports goals partial before a fixture is finished`() {
        val issues = inspector.inspect(targetOf("""{"response":[{"fixture":{"status":{"short":"1H"}},"goals":{"home":1,"away":null},"score":{}}]}"""))

        assertThat(issues.map { it.inspectionRuleCode.value }).containsExactly("GOALS_PARTIAL")
    }

    @Test
    fun `reports final score relationship rules only for their matching finished statuses`() {
        val regularIssues = inspector.inspect(targetOf("""{"response":[{"fixture":{"status":{"short":"FT"}},"goals":{"home":2,"away":1},"score":{"halftime":{"home":0,"away":0},"fulltime":{"home":1,"away":1}}}]}"""))
        val extraTimeIssues = inspector.inspect(targetOf("""{"response":[{"fixture":{"status":{"short":"AET"}},"goals":{"home":2,"away":1},"score":{"halftime":{"home":0,"away":0},"fulltime":{"home":1,"away":1},"extratime":{"home":0,"away":0}}}]}"""))
        val penaltyIssues = inspector.inspect(targetOf("""{"response":[{"fixture":{"status":{"short":"PEN"}},"goals":{"home":1,"away":1},"score":{"fulltime":{"home":1,"away":1},"extratime":{"home":0,"away":0},"penalty":{"home":3,"away":3}}}]}"""))

        assertThat(regularIssues.map { it.inspectionRuleCode.value }).contains("REGULAR_TIME_SCORE_TOTAL_MISMATCH")
        assertThat(extraTimeIssues.map { it.inspectionRuleCode.value }).contains("EXTRATIME_SCORE_TOTAL_MISMATCH")
        assertThat(penaltyIssues.map { it.inspectionRuleCode.value }).contains("PENALTY_SCORE_TIED")
    }

    @Test
    fun `does not apply final score rules to abandoned fixtures`() {
        val issues = inspector.inspect(targetOf("""{"response":[{"fixture":{"status":{"short":"ABD"}},"goals":{"home":null,"away":null},"score":{"fulltime":{"home":2,"away":1}}}]}"""))

        assertThat(issues).isEmpty()
    }

    private fun targetOf(json: String): ApiSportsFixtureSingleRootDto =
        objectMapper.treeToValue(objectMapper.readTree(json), ApiSportsFixtureSingleRootDto::class.java)
}
