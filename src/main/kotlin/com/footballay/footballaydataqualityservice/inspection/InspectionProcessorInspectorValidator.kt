package com.footballay.footballaydataqualityservice.inspection

import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.stereotype.Component

/** Processor별 Inspector 이름의 중복을 애플리케이션 초기화 시 차단한다. */
@Component
class InspectionProcessorInspectorValidator(
    private val processors: List<QualityInspectionProcessor>,
) : SmartInitializingSingleton {
    override fun afterSingletonsInstantiated() {
        processors.forEach(::validateInspectorNames)
    }

    private fun validateInspectorNames(processor: QualityInspectionProcessor) {
        val duplicatedNames =
            processor.inspectors()
                .groupBy { it.inspectionName.value }
                .filterValues { it.size > 1 }
                .keys

        check(duplicatedNames.isEmpty()) {
            "Duplicated inspector names for processor=${processor.javaClass.name}: ${duplicatedNames.sorted().joinToString(", ")}"
        }
    }
}
