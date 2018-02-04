package org.shedlang.compiler.backends.python.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.shedlang.compiler.backends.python.pythoniseName

class NamingTests {
    @Test
    fun namesHavePep8Casing() {
        assertThat(
            pythoniseName("oneTwoThree"),
            equalTo("one_two_three")
        )
        assertThat(
            pythoniseName("OneTwoThree"),
            equalTo("OneTwoThree")
        )
    }

    @Test
    fun namesThatMatchPythonKeywordsHaveUnderscoreAppended() {
        assertThat(
            pythoniseName("assert"),
            equalTo("assert_")
        )
    }
}
