package org.xtclang.idea.run

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xtclang.idea.lsp.ModuleTests
import org.xtclang.idea.lsp.ServerTest
import org.xtclang.idea.lsp.TestSelector

/**
 * The test console's Rerun Failed Tests, which runs a test run's configuration again with the tests that failed, and,
 * after Run 'All Ecstasy Tests', each module that has failed tests with those, by the module that each test's location
 * names.
 */
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
        assertThat(failed).containsOnlyKeys("")
        assertThat(failed[""]).containsExactly(
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
                XtcRerunFailedTestsAction.failedTests(listOf("xunit://DeckTest.testDeckHas52Cards"))[""],
            )
        assertThat(request.tests).containsExactly(TestSelector("DeckTest.testDeckHas52Cards", ServerTest.TEST))
    }

    @Test
    fun `after a run of every test module, the failed tests go by the module that their locations name`() {
        val failed =
            XtcRerunFailedTestsAction.failedTests(
                listOf(
                    "xunit://SecondSuite/three",
                    "xunit://FirstSuite/Inner.four",
                    "xunit://SecondSuite/three",
                    "xunit://FirstSuite/",
                    "xunit://SecondSuite/Inner.five",
                ),
            )
        assertThat(failed).containsOnlyKeys("FirstSuite", "SecondSuite")
        assertThat(failed["FirstSuite"]).containsExactly(TestSelector("Inner.four", ServerTest.TEST))
        assertThat(failed["SecondSuite"]).containsExactly(
            TestSelector("three", ServerTest.TEST),
            TestSelector("Inner.five", ServerTest.TEST),
        )
    }

    @Test
    fun `a test's location names its module only where the run gives one, and reads back as it was given`() {
        val own = XtcTestLocation("", "Inner.works")
        val other = XtcTestLocation("SecondSuite", "Inner.works")

        assertThat(own.url).isEqualTo("xunit://Inner.works")
        assertThat(other.url).isEqualTo("xunit://SecondSuite/Inner.works")
        assertThat(listOf(own, other).map { XtcTestLocation.parse(it.url) }).containsExactly(own, other)
        assertThat(XtcTestLocation.of("SecondSuite/Inner.works")).isEqualTo(other)
        assertThat(XtcTestLocation.of("SecondSuite/")).isEqualTo(XtcTestLocation("SecondSuite", ""))
        assertThat(XtcTestLocation.parse("java:test://Other.test")).isNull()
    }

    @Test
    fun `the rerun runs each module that has failed tests, in turn, with only those, and the full run every module's`() {
        val listed =
            listOf(
                ModuleTests("SecondSuite", "file:///p/SecondSuite.x", ServerTest(children = listOf(ServerTest("three")))),
                ModuleTests("Empty", "file:///p/Empty.x", ServerTest()),
                ModuleTests("FirstSuite", "file:///p/FirstSuite.x", ServerTest(children = listOf(ServerTest("one")))),
                ModuleTests("ThirdSuite", "file:///p/ThirdSuite.x", ServerTest(children = listOf(ServerTest("six")))),
            )
        val three = listOf(TestSelector("three", ServerTest.TEST))
        val four = listOf(TestSelector("four", ServerTest.TEST))

        val rerun = AllTestModulesState.selection(listed, mapOf("SecondSuite" to three, "FirstSuite" to four))
        assertThat(rerun.map { (module, tests) -> module.module to tests }).containsExactly("FirstSuite" to four, "SecondSuite" to three)
        // each module as the server lists it, for an embedded run's source
        assertThat(rerun.map { it.first.uri }).containsExactly("file:///p/FirstSuite.x", "file:///p/SecondSuite.x")

        // a module that the server no longer lists runs by its name
        assertThat(AllTestModulesState.selection(listed, mapOf("Gone" to three))).containsExactly(ModuleTests("Gone") to three)

        // the full run: every module that has tests, all of them
        assertThat(AllTestModulesState.selection(listed, emptyMap()).map { (module, tests) -> module.module to tests })
            .containsExactly("FirstSuite" to emptyList(), "SecondSuite" to emptyList(), "ThirdSuite" to emptyList())
    }
}
