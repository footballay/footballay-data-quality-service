package com.footballay.dataquality.inspection

import com.footballay.dataquality.pipeline.input.DataProvider
import com.footballay.dataquality.pipeline.input.QualityCheckRequest
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test

class InspectionProcessorInspectorValidatorTest {
    @Test
    fun `rejects duplicated inspector names within a processor`() {
        val processor =
            TestProcessor(
                listOf(
                    TestInspector(InspectionName.of("FIXTURE")),
                    TestInspector(InspectionName.of("FIXTURE")),
                ),
            )

        assertThatIllegalStateException()
            .isThrownBy { InspectionProcessorInspectorValidator(listOf(processor)).afterSingletonsInstantiated() }
            .withMessage("Duplicated inspection names for processor=${TestProcessor::class.java.name}: FIXTURE")
    }

    @Test
    fun `rejects an inspector name duplicated by a processor inspection name`() {
        val processor =
            TestProcessor(
                usedInspectors = listOf(TestInspector(InspectionName.of("RAW_MAPPING"))),
                processorInspectionNames = listOf(InspectionName.of("RAW_MAPPING")),
            )

        assertThatIllegalStateException()
            .isThrownBy { InspectionProcessorInspectorValidator(listOf(processor)).afterSingletonsInstantiated() }
            .withMessage("Duplicated inspection names for processor=${TestProcessor::class.java.name}: RAW_MAPPING")
    }

    private class TestProcessor(
        private val usedInspectors: List<QualityInspector<*>>,
        private val processorInspectionNames: List<InspectionName> = emptyList(),
    ) : QualityInspectionProcessor {
        override fun inspectors(): List<QualityInspector<*>> = usedInspectors

        override fun inspectionNames(): List<InspectionName> =
            super<QualityInspectionProcessor>.inspectionNames() + processorInspectionNames

        override fun supports(
            provider: DataProvider,
            endpointKey: String,
        ): Boolean = false

        override fun inspect(
            request: QualityCheckRequest,
            rawJson: com.fasterxml.jackson.databind.JsonNode,
        ): List<FoundIssue> = emptyList()
    }

    private class TestInspector(
        override val inspectionName: InspectionName,
    ) : QualityInspector<Any> {
        override fun inspect(target: Any): List<FoundIssue> = emptyList()
    }
}
