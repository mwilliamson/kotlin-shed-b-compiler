package org.shedlang.compiler.tests

import org.apiguardian.api.API
import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.platform.commons.util.AnnotationUtils
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(RetentionPolicy.RUNTIME)
@API(status = API.Status.STABLE, since = "5.0")
annotation class WorkInProgress

internal class WorkInProgressCondition : ExecutionCondition {
    override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {
        val element = context.element
        val annotation = AnnotationUtils.findAnnotation(element, WorkInProgress::class.java)
        if (annotation.isPresent && isCi()) {
            return ConditionEvaluationResult.disabled("WIP")
        }
        return ENABLED
    }

    private fun isCi(): Boolean {
        val value = System.getenv("CI")
        return value != null && value == "true"
    }

    companion object {
        private val ENABLED = ConditionEvaluationResult.enabled("@WorkInProgress is not present")
    }
}
