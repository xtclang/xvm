package org.xtclang.idea.playbook

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit

/**
 * Records the tour: the plugin's features, step by step, in a real IDE that edits, runs, debugs and tests the card
 * game of lang/test-fixtures/tour, for the tour's video and slides, which the composer makes from the run's
 * `tour.json` and frames. Opt-in, with `-Dxtc.tour=true`, and in the playbook container, whose screen it captures;
 * `-Dxtc.tour.sections=<names>` records only the named sections.
 */
class TourTest {
    @Test
    @EnabledIfSystemProperty(named = "xtc.tour", matches = "true")
    fun tour() {
        val reports = Files.createDirectories(Path.of(System.getProperty("xtc.playbook.reports")))
        val run = Files.createTempDirectory(reports, "run-tour-")
        val project = cardGame()
        val sections =
            System
                .getProperty("xtc.tour.sections")
                .orEmpty()
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toSet()
        val ide = PlaybookIde("XtcCompilerPlaybook-${run.fileName}", project, run, reports)
        var tour: Tour? = null
        var frame: String? = null
        var actionLog: String? = null
        try {
            ide.run(options = TOUR_OPTIONS) {
                tour =
                    Tour(this, project, FIXTURE, run, sections).also {
                        // with a screen recording, the pointer, the keys and the typing show what each step does; with
                        //  -Dxtc.tour.visible=true they go the same way without one, as a recording's run takes them
                        it.ui.showActions(System.getenv("XTC_RECORD_VIDEO") == "1" || System.getProperty("xtc.tour.visible") == "true")
                        frame = runCatching { it.ui.frameBounds() }.getOrNull()
                        try {
                            it.script()
                        } finally {
                            actionLog = runCatching { it.ui.actionLog() }.getOrNull()
                        }
                    }
            }
        } catch (failure: Throwable) {
            Files.writeString(run.resolve("failure.txt"), failure.stackTraceToString())
            throw failure
        } finally {
            val steps = tour?.steps.orEmpty()
            val json = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
            // with a screen recording, when it began, and what the tour pressed and clicked, for its clips
            val screen =
                Path.of(System.getenv("XTC_RECORDING_START_FILE") ?: "/out/screen.start").takeIf(Files::isRegularFile)?.let {
                    mapOf(
                        "file" to "screen.mp4",
                        "start" to Files.readString(it).trim().toLong(),
                        "frame" to frame,
                    )
                }
            val actions = actionLog?.let { JsonParser.parseString(it) }
            Files.writeString(
                run.resolve("tour.json"),
                json.toJson(
                    mapOf(
                        "project" to project.toString(),
                        "screen" to screen,
                        "steps" to steps,
                        "checks" to tour?.verifies.orEmpty(),
                        "actions" to actions,
                    ),
                ) + "\n",
            )
            Files.writeString(
                run.resolve("results.json"),
                json.toJson(
                    mapOf(
                        "mode" to "TOUR",
                        "ideVersion" to ide.ideVersion,
                        "lsp4ijVersion" to ide.lsp4ijVersion,
                        "steps" to steps.size,
                        "checks" to (tour?.checks ?: 0),
                        "failed" to tour?.failures.orEmpty(),
                        "skippedCount" to (tour?.skipped?.size ?: 0),
                        "skipped" to tour?.skipped.orEmpty(),
                        "ideFailures" to ide.ideFailures.toList(),
                    ),
                ) + "\n",
            )
            println("IntelliJ tour: ${run.resolve("tour.json")}")
        }
        val failures = tour?.failures.orEmpty()
        check(failures.isEmpty()) { "${failures.size} of the tour's steps and checks failed:\n" + failures.joinToString("\n") }
    }

    /**
     * The tour's project, in ~/IdeaProjects as a user's would be, which the IDE shows: a project that `xtc init`
     * makes, as New Project does, with the card game's sources in place of its own, so that it builds, runs and tests
     * with Gradle as well as embedded. The container's home is new for each run; elsewhere, the project must not be
     * there yet.
     */
    private fun cardGame(): Path {
        val projects = Files.createDirectories(Path.of(System.getProperty("user.home"), "IdeaProjects"))
        val project = projects.resolve(PROJECT)
        check(!Files.exists(project)) { "$project is in the way of the tour's project" }
        val init =
            ProcessBuilder(PlaybookIde.XDK_BIN.resolve("xtc").toString(), "init", PROJECT, "--dir", projects.toString())
                .redirectErrorStream(true)
                .start()
        val output = init.inputReader().readText()
        check(init.waitFor(120, TimeUnit.SECONDS) && init.exitValue() == 0) { "xtc init failed: $output" }
        project.resolve("src").toFile().deleteRecursively()
        FIXTURE.toFile().copyRecursively(project.resolve("src").toFile())
        // the container holds the IDE, its language server and the tests' Gradle as well: the project's own Gradle,
        // which its Gradle-mode steps start, gets a heap that leaves them room
        Files.writeString(
            project.resolve("gradle.properties"),
            "org.gradle.jvmargs=-Xmx768m -XX:MaxMetaspaceSize=384m\n",
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
        return project
    }

    private companion object {
        const val PROJECT = "cardGame"

        /** The card game's sources, which the tour puts back after each step that changes them. */
        val FIXTURE: Path = PlaybookIde.TREE.resolve("lang/test-fixtures/tour/$PROJECT/src")

        /** The recording's IDE: no tips, and completion that pops up as the user types, as by default. */
        val TOUR_OPTIONS =
            mapOf(
                "ide.general.xml" to
                    """
                    <application>
                      <component name="GeneralSettings">
                        <option name="autoSaveFiles" value="false" />
                        <option name="autoSaveIfInactive" value="false" />
                        <option name="showTipsOnStartup" value="false" />
                        <option name="confirmExit" value="false" />
                      </component>
                    </application>
                    """.trimIndent(),
                // the main menu as a menu bar of its own, whose menus a click opens: under the new UI's hamburger
                //  button, the bar it expands opened File, and a click on Tools closed it or left File open
                "ui.lnf.xml" to
                    """
                    <application>
                      <component name="UISettings">
                        <option name="mainMenuDisplayMode" value="SEPARATE_TOOLBAR" />
                      </component>
                    </application>
                    """.trimIndent(),
            )
    }
}
