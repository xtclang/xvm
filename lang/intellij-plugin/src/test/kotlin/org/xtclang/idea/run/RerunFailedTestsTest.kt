package org.xtclang.idea.run

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xtclang.idea.lsp.ServerTest
import org.xtclang.idea.lsp.TestSelector

/** The test console's Rerun Failed Tests, which runs a test run's configuration again with the tests that failed. */
class RerunFailedTestsTest {
    @Test
    fun `the tests to run again are the failed tests that their xunit locations select, each once`() {
        val failed =
            XtcRerunFailedTestsAction.failedTests(
                listOf(
                    "xunit://DeckTest.testDeckHas52Cards",
                    null,
                    "java:test://Other.test",
                    "xunit://",
                    "xunit://DeckTest.Inner.works",
                    "xunit://DeckTest.testDeckHas52Cards",
                ),
            )
        assertThat(failed).containsExactly(
            TestSelector("DeckTest.testDeckHas52Cards", ServerTest.TEST),
            TestSelector("DeckTest.Inner.works", ServerTest.TEST),
        )
    }

    @Test
    fun `the tests to run again go with the run request, as a configuration's own tests do`() {
        val request =
            XtcRunConfiguration.embeddedRequest(
                "DeckTest",
                "file:///project/src/test/x/DeckTest.x",
                "",
                "",
                XtcRerunFailedTestsAction.failedTests(listOf("xunit://DeckTest.testDeckHas52Cards")),
            )
        assertThat(request.tests).containsExactly(TestSelector("DeckTest.testDeckHas52Cards", ServerTest.TEST))
    }
}
