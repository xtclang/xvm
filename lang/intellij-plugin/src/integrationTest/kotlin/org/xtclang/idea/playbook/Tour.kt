package org.xtclang.idea.playbook

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * A title card of the tour, which the composer draws: a small [eyebrow] line over a [heading], a [lead] under it, and
 * then [boxes] side by side, a block of [code], or both.
 */
internal data class TourCard(
    val eyebrow: String,
    val heading: String,
    val lead: String? = null,
    val boxes: List<TourBox> = emptyList(),
    val code: String? = null,
)

internal data class TourBox(
    val title: String,
    val text: String,
)

/**
 * One step of the tour as `tour.json` records it for the composer: its [section], [title] and [caption], which the
 * recording shows under it, and either the [image] of the screen it captured, with the [focus] it highlights and the
 * [cursor] it points at, or the [card] the composer draws. A step that failed has its [error], and no image when
 * nothing was captured; one whose subject did not show in time has both.
 */
internal data class TourStep(
    val number: Int,
    val section: String,
    val title: String,
    val caption: String,
    val image: String? = null,
    val focus: JsonObject? = null,
    val cursor: JsonObject? = null,
    val card: TourCard? = null,
    val error: String? = null,
    val check: String? = null,
)

/**
 * Records the tour of [project] in the driven IDE: each step does what a user would, through [TourProbe], and
 * captures the screen into [run]'s frames, or adds a title card. A step that fails is recorded with its error, and the
 * tour goes on with the next. [fixture] holds the project's sources as the tour starts, which [restore] puts back, and
 * [sections], when not empty, are the only sections recorded, for a quicker run while a section is worked on.
 */
internal class Tour(
    driver: Driver,
    private val project: Path,
    private val fixture: Path,
    private val run: Path,
    private val sections: Set<String>,
) {
    val ui: TourProbe = with(driver) { utility(TourProbe::class) }
    private val frames: Path = Files.createDirectories(run.resolve("frames"))
    val steps = mutableListOf<TourStep>()

    /** The checks that failed, each as `section: what: why`; the tour's test fails when there is any. */
    val failures = mutableListOf<String>()

    /** How many checks ran, the steps' and [verify]'s. */
    var checks = 0
        private set
    private var section = ""

    /** Whether the current section is recorded. */
    val recording: Boolean get() = sections.isEmpty() || section in sections

    /** The absolute path of [relative], a file of the project. */
    fun file(relative: String): String = project.resolve(relative).toString()

    /** The absolute path of [name], a source of the game's module, such as `Player.x`. */
    fun source(name: String): String = file("src/main/x/cardGame/$name")

    /** The absolute path of [name], a test module, such as `DeckTest.x`. */
    fun test(name: String): String = file("src/test/x/$name")

    fun section(name: String) {
        section = name
    }

    fun card(
        title: String,
        caption: String,
        card: TourCard,
    ) {
        if (recording) steps += TourStep(steps.size + 1, section, title, caption, card = card)
    }

    /**
     * A step on the screen: [act] does what the step shows; then, once [until] holds and the subject [focus] names
     * (see [TourProbe.capture]) shows, for at most [wait], and after [settle] for the screen to draw, the screen is
     * captured with that subject as the step's highlight; [after] then undoes what the step must not leave behind.
     */
    fun shot(
        title: String,
        caption: String,
        focus: String = "none",
        settle: Duration = 1500.milliseconds,
        wait: Duration = 20.seconds,
        until: TourProbe.() -> Boolean = { true },
        after: TourProbe.() -> Unit = {},
        check: TourProbe.() -> Unit = {},
        act: TourProbe.() -> Unit = {},
    ) {
        if (!recording) return
        val number = steps.size + 1
        val image = "frames/%03d.png".format(number)
        var step =
            try {
                ui.act()
                val shown = within(wait) { ui.until() && ui.showing(focus) }
                Thread.sleep(settle.inWholeMilliseconds)
                val answer = JsonParser.parseString(ui.capture(frames.resolve("%03d.png".format(number)).toString(), focus)).asJsonObject
                TourStep(
                    number,
                    section,
                    title,
                    caption,
                    image,
                    answer["focus"]?.asJsonObject,
                    answer["cursor"]?.asJsonObject,
                    error = if (shown) null else "the step's subject ($focus) did not show within $wait",
                )
            } catch (failure: Exception) {
                TourStep(number, section, title, caption, error = causes(failure))
            }
        if (step.image != null) {
            checks++
            runCatching { ui.check() }.onFailure { failure -> step = step.copy(check = causes(failure)) }
        }
        step.error?.let { failures += "$section: $number $title: $it" }
        step.check?.let { failures += "$section: $number $title: check: $it" }
        steps += step
        runCatching { ui.after() }
    }

    /**
     * A check between the steps, which the recording does not show: [block] fails with what it found when what the
     * tour has done did not do what it should, such as a refactoring that left the project with an error.
     */
    fun verify(
        what: String,
        block: TourProbe.() -> Unit,
    ) {
        if (!recording) return
        checks++
        runCatching { ui.block() }.onFailure { failures += "$section: $what: ${causes(it)}" }
    }

    private fun causes(failure: Throwable): String =
        generateSequence(failure) { it.cause }.joinToString(" <- ") { "${it.javaClass.simpleName}: ${it.message}" }

    /** Runs [block] as a step's preparation that captures nothing, in a recorded section only; a failure is not fatal. */
    fun prepare(block: TourProbe.() -> Unit) {
        if (recording) runCatching { ui.block() }.onFailure { println("Tour: preparation failed in $section: $it") }
    }

    /** Whether [condition] holds within [timeout], polled. */
    fun within(
        timeout: Duration,
        condition: () -> Boolean,
    ): Boolean {
        val deadline = TimeSource.Monotonic.markNow() + timeout
        while (true) {
            if (runCatching(condition).getOrDefault(false)) return true
            if (deadline.hasPassedNow()) return false
            Thread.sleep(250)
        }
    }

    /**
     * Puts the project's sources back as [fixture] has them, in the editors and on disk, and deletes the sources that
     * steps added, such as a renamed class's file; then closes popups and dialogs and stops runs.
     */
    fun restore() {
        if (!recording) return
        runCatching {
            ui.dismiss()
            // a modal dialog holds every call that waits for the EDT outside it, as restoring the files does
            ui.closeDialogs()
            if (!within(15.seconds) { !ui.dialogShowing() }) repeat(3) { ui.key("ESCAPE") }
            ui.stopAll()
        }
        val sources = project.resolve("src")
        Files.walk(fixture).use { files ->
            files.filter { it.isRegularFile() }.forEach { original ->
                val copy = sources.resolve(original.relativeTo(fixture).toString())
                if (!Files.exists(copy)) {
                    Files.createDirectories(copy.parent)
                    Files.writeString(copy, original.readText())
                }
                runCatching { ui.restore(copy.toString(), original.readText()) }.onFailure { println("Tour: $copy not restored: $it") }
            }
        }
        Files.walk(sources).use { files ->
            files
                .filter { it.isRegularFile() && !Files.exists(fixture.resolve(it.relativeTo(sources).toString())) }
                .toList()
                .forEach { added -> runCatching { ui.delete(added.toString()) } }
        }
    }

    /** Checks that every source of the project is back as [fixture] has it, as each section leaves it. */
    fun verifyRestored() =
        verify("the project is back as it was") {
            val sources = project.resolve("src")
            val changed =
                Files.walk(fixture).use { files ->
                    files
                        .filter { it.isRegularFile() }
                        .toList()
                        .filter { original ->
                            val copy = sources.resolve(original.relativeTo(fixture).toString())
                            !Files.exists(copy) || fileText(copy.toString()) != original.readText()
                        }.map { it.relativeTo(fixture).toString() }
                }
            expect(changed.isEmpty()) { "Changed: $changed" }
        }

    /** Waits up to [timeout] for the project to open in its window. */
    fun awaitProject(timeout: Duration = 180.seconds) = awaitUi("the project opens", timeout) { ui.projectOpen() }

    /** Waits up to [timeout] for the IDE's background tasks, such as indexing and the Gradle import, to end. */
    fun awaitIdle(timeout: Duration = 600.seconds) {
        var busy = ""
        awaitUi("the IDE's background tasks end", timeout, { "Still running: $busy" }) {
            busy = ui.busy()
            busy.isEmpty()
        }
    }

    /** Waits up to [timeout] for the language server to start. */
    fun awaitServer(timeout: Duration = 120.seconds) = awaitUi("the Ecstasy language server starts", timeout) { ui.ready() }
}

/** The IDE-side driver of the tour; see its implementation for what each method does. */
@Remote("org.xtclang.idea.playbook.probe.TourUi", plugin = "org.xtclang.playbook.probe")
internal interface TourProbe {
    fun busy(): String

    fun projectOpen(): Boolean

    fun ready(): Boolean

    fun prepare(
        width: Int,
        height: Int,
        theme: String,
        fontSize: Int,
    ): String

    fun open(
        path: String,
        line: Int,
    )

    fun caret(
        text: String,
        occurrence: Int,
        delta: Int,
        select: Boolean,
    ): String

    fun action(id: String)

    fun type(text: String)

    fun replace(
        from: String,
        occurrence: Int,
        to: String,
    )

    fun restore(
        path: String,
        text: String,
    ): Boolean

    fun delete(path: String)

    fun key(key: String)

    fun dismiss()

    fun closeDialogs()

    fun toolWindow(
        id: String,
        visible: Boolean,
    )

    fun showing(focus: String): Boolean

    fun fill(text: String)

    fun popupText(): String

    fun lookupItems(): String

    fun toolWindowText(id: String): String

    fun diagnostics(path: String): String

    fun fileText(path: String): String

    fun editorState(): String

    fun watchTests()

    fun testResults(): String

    fun fillDialog(text: String)

    fun click(text: String)

    fun dialogShowing(): Boolean

    fun buttonEnabled(text: String): Boolean

    fun fileHas(
        path: String,
        text: String,
    ): Boolean

    fun choose(text: String): Boolean

    fun rows(): String

    fun settings(id: String)

    fun templateContexts(): String

    fun gutter(
        line: Int,
        tooltip: String,
    )

    fun gutterIcons(): String

    fun breakpoint(
        line: Int,
        condition: String,
    )

    fun clearBreakpoints()

    fun debugState(): String

    fun debugTab(name: String)

    fun runEnded(): Boolean

    fun input(text: String)

    fun console(): String

    fun buildMode(value: String): String

    fun runWith(value: String): String

    fun stopAll()

    /**
     * Captures the IDE's window into the PNG [file], and answers where on it [focus] is: `popup`, `lookup`, `caret`,
     * `line:<n>`, `gutter:<n>`, `toolwindow:<id>`, `editor` or `none`.
     */
    fun capture(
        file: String,
        focus: String,
    ): String
}

/** Puts the caret on the first [text], or its [occurrence], [delta] characters in, and answers its zero-based line. */
internal fun TourProbe.at(
    text: String,
    delta: Int = 0,
    occurrence: Int = 0,
): Int = JsonParser.parseString(caret(text, occurrence, delta, false)).asJsonObject["line"].asInt

/** Selects the first [text], or its [occurrence]. */
internal fun TourProbe.select(
    text: String,
    occurrence: Int = 0,
) {
    caret(text, occurrence, 0, true)
}

/**
 * Opens Show Intention Actions on the caret or selection, and again until its list has a row with [row], as a user
 * would press Alt+Enter again; answers whether it did within [timeout].
 */
internal fun TourProbe.intentions(
    row: String,
    timeout: Duration = 45.seconds,
): Boolean {
    val deadline = TimeSource.Monotonic.markNow() + timeout
    while (!deadline.hasPassedNow()) {
        // TODO LSP4IJ: UP07 — overlapping full pulls can cancel a lazy code action while the list discovers it, so
        //  the list can open without the server's actions; the playbook's quickFix reopens it for the same reason
        action("ShowIntentionActions")
        repeat(12) {
            Thread.sleep(250)
            if (row in rows()) return true
        }
        dismiss()
        Thread.sleep(1000)
    }
    return false
}

/**
 * Presses a dialog's [button] once the dialog has enabled it, as it does once its input is valid, and waits for the
 * dialog to close; answers whether it did within [timeout].
 */
internal fun TourProbe.confirm(
    button: String,
    timeout: Duration = 60.seconds,
): Boolean {
    val deadline = TimeSource.Monotonic.markNow() + timeout
    while (!buttonEnabled(button)) {
        if (deadline.hasPassedNow()) return false
        Thread.sleep(250)
    }
    click(button)
    while (dialogShowing()) {
        if (deadline.hasPassedNow()) return false
        Thread.sleep(250)
    }
    return true
}

/** Fails with [message], which says what was found, unless [condition] holds: a step's or a [Tour.verify]'s check. */
internal fun expect(
    condition: Boolean,
    message: () -> String,
) {
    check(condition, message)
}

/**
 * Fails unless, within [timeout], the editor of the file at [path] marks no error, as a refactoring or a fix must leave
 * the code: it compiles. A step's settle lets the server see the change first.
 */
internal fun TourProbe.compilesCleanly(
    path: String,
    timeout: Duration = 30.seconds,
) {
    val deadline = TimeSource.Monotonic.markNow() + timeout
    while ("ERROR" in diagnostics(path)) {
        check(!deadline.hasPassedNow()) { "Errors in ${Path.of(path).fileName}: ${diagnostics(path)}" }
        Thread.sleep(250)
    }
}

/** The selected editor's state, as [TourProbe.editorState] answers it. */
internal fun TourProbe.editor(): JsonObject = JsonParser.parseString(editorState()).asJsonObject

/** The results of the tests run since [TourProbe.watchTests], counted by state, such as passed=7, failed=1. */
internal fun TourProbe.testCounts(): Map<String, Int> =
    JsonParser
        .parseString(testResults())
        .asJsonObject
        .entrySet()
        .groupingBy { it.value.asJsonObject["state"].asString }
        .eachCount()

/** The current debug session, as [TourProbe.debugState] answers it. */
internal fun TourProbe.debug(): JsonObject = JsonParser.parseString(debugState()).asJsonObject

/** Whether the current debug session is paused, on the 1-based [line] when it is not 0. */
internal fun TourProbe.pausedAt(line: Int = 0): Boolean =
    debug().let { it["suspended"].asBoolean && (line == 0 || it["line"]?.asInt == line) }

/** Where the current debug session is paused, as file:line, or empty while it runs. */
internal fun TourProbe.position(): String =
    debug().let { if (it["suspended"].asBoolean) "${it["file"]?.asString}:${it["line"]?.asInt}" else "" }
