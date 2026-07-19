package com.footballay.footballaydataqualityservice.inspection

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class QualityIssueTypeTest {
    @Test
    fun `uses uppercase snake case names as quality issue type codes`() {
        assertThat(QualityIssueType.UNEXPECTED_NULL.name).isEqualTo("UNEXPECTED_NULL")
        assertThat(QualityIssueType.INCONSISTENT_INFORMATION.name).isEqualTo("INCONSISTENT_INFORMATION")
    }
}
