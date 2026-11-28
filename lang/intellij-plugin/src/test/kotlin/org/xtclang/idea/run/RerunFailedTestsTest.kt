package org.xtclang.idea.run

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.lsp.protocol.ModuleTests
import org.xvm.lsp.protocol.ServerTest
import org.xvm.lsp.protocol.TestSelector

/**
 * The test console's Rerun Failed Tests, which runs a test run's configuration again with the tests that failed, and,
 * after Run 'All Ecstasy Tests', each module that has failed tests with those, by the module that each test's location
 * names: each module in turn, or in Gradle mode each in its project's testXtc, in one build.
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
    fun `in Gradle mode the rerun is one build of each module's project's testXtc, with only its module's failed tests`() {
        val projects = mapOf("json.xtclang.org" to ":xdk:lib-json", "DeckTest" to ":", "Other" to ":other")
        val tasks =
            XtcRunConfiguration.gradleRerunTasks(
                projects,
                mapOf(
                    "json.xtclang.org" to
                        listOf(TestSelector("Parser.reads", ServerTest.TEST), TestSelector("Parser.writes", ServerTest.TEST)),
                    "DeckTest" to listOf(TestSelector("three", ServerTest.TEST)),
                ),
            )
        // by the projects' paths, a root project's task as :testXtc, and nothing of a project without failed tests
        assertThat(tasks).containsExactly(
            ":testXtc",
            "--module=DeckTest",
            "--test-method=three",
            "--test-report=json",
            ":xdk:lib-json:testXtc",
            "--module=json.xtclang.org",
            "--test-method=Parser.reads",
            "--test-method=Parser.writes",
            "--test-report=json",
        )
    }

    @Test
    fun `two modules of one project rerun in its one testXtc, and a module that no project has stops the rerun`() {
        val three = listOf(TestSelector("three", ServerTest.TEST))
        val four = listOf(TestSelector("four", ServerTest.TEST))
        val shared = mapOf("FirstSuite" to ":suites", "SecondSuite" to ":suites")

        // testXtc takes one --module, so the project's modules each run the tests they have of both
        assertThat(XtcRunConfiguration.gradleRerunTasks(shared, mapOf("FirstSuite" to three, "SecondSuite" to four)))
            .containsExactly(":suites:testXtc", "--test-method=three", "--test-method=four", "--test-report=json")
        // without an imported model, or one that lacks the module, there is no project to send it to
        assertThat(XtcRunConfiguration.gradleRerunTasks(emptyMap(), mapOf("FirstSuite" to three))).isNull()
        assertThat(XtcRunConfiguration.gradleRerunTasks(shared, mapOf("FirstSuite" to three, "Gone" to four))).isNull()
        assertThat(XtcRunConfiguration.gradleRerunTasks(shared, emptyMap())).isNull()
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
