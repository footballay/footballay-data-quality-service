package com.footballay.footballaydataqualityservice.inspection

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

class InspectionIdentifierTest {
    @Test
    fun `accepts uppercase snake case inspection scope and code`() {
        validateInspectionIdentifier("Inspection scope", TestScope.FIXTURE_STATUS.value)
        validateInspectionIdentifier("Inspection rule code", TestRuleCode.LONG_NULL.value)

        assertThat(TestScope.FIXTURE_STATUS.value).isEqualTo("FIXTURE_STATUS")
        assertThat(TestRuleCode.LONG_NULL.value).isEqualTo("LONG_NULL")
    }

    @Test
    fun `rejects invalid inspection scope and code formats`() {
        assertThatIllegalArgumentException()
            .isThrownBy { validateInspectionIdentifier("Inspection scope", "fixtureStatus") }
            .withMessage("Inspection scope must use uppercase snake case: fixtureStatus")
        assertThatIllegalArgumentException()
            .isThrownBy { validateInspectionIdentifier("Inspection rule code", "long-null") }
            .withMessage("Inspection rule code must use uppercase snake case: long-null")
    }

    @Test
    fun `uses uppercase snake case names as quality issue type codes`() {
        assertThat(QualityIssueType.UNEXPECTED_NULL.name).isEqualTo("UNEXPECTED_NULL")
        assertThat(QualityIssueType.INCONSISTENT_INFORMATION.name).isEqualTo("INCONSISTENT_INFORMATION")
    }

    private enum class TestScope : InspectionScope {
        FIXTURE_STATUS,
        ;

        override val value: String
            get() = name
    }

    private enum class TestRuleCode : InspectionRuleCode {
        LONG_NULL,
        ;

        override val value: String
            get() = name
    }
}
