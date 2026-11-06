package org.xtclang.idea.playbook

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import org.xtclang.idea.playbook.probe.HANG_LIMIT
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
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
 * recording shows under it, the [feature] it exercises, an LSP or DAP request or an IntelliJ action that the composer
 * shows as a tag, and either the [image] of the screen it captured, with the [focus] it highlights and the [cursor] it
 * points at, or the [card] the composer draws. A step that failed has its [error], and no image when nothing was
 * captured; one whose subject did not show in time has both.
 */
internal data class TourStep(
    val number: Int,
    val section: String,
    val title: String,
    val caption: String,
    val feature: String? = null,
    val image: String? = null,
    val focus: JsonObject? = null,
    val cursor: JsonObject? = null,
    val card: TourCard? = null,
    val error: String? = null,
    val check: String? = null,
    /** When the step began, its screen was captured, and it ended, in epoch milliseconds, the screen recording's clock. */
    val start: Long? = null,
    val shot: Long? = null,
    val end: Long? = null,
)

/** A check between the steps, [verify]'s, with when it began and ended, in epoch milliseconds. */
internal data class TourCheck(
    val section: String,
    val what: String,
    val start: Long,
    val end: Long,
    val error: String? = null,
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
    private val probe: TourProbe = with(driver) { utility(TourProbe::class) }

    /** The driver's connection, lost once a call does not return while a plain one does not either. */
    @Volatile
    private var driverLost = false

    /**
     * The probe, each call bounded as the tour's waits are: a call that waits in the IDE, as for an evaluation or a
     * window, fails once nothing is in progress that could end it, else at [HANG]; and when a plain call does not
     * return either, the driver's connection is lost and the rest of the tour is skipped.
     */
    val ui: TourProbe =
        Proxy.newProxyInstance(TourProbe::class.java.classLoader, arrayOf(TourProbe::class.java)) { _, method, args ->
            check(!driverLost) { "driver connection lost" }
            val call = CompletableFuture.supplyAsync { method.invoke(probe, *(args ?: emptyArray())) }
            try {
                // most calls answer at once; one that does not is waited for as within() waits, with its idle check
                if (runCatching { call.get(1, TimeUnit.SECONDS) }.isFailure && !call.isDone && !within(HANG) { call.isDone }) {
                    throw TimeoutException()
                }
                call.get()
            } catch (_: TimeoutException) {
                val stuck = Quiescence.stuck
                val ping = CompletableFuture.supplyAsync { probe.projectOpen() }
                if (runCatching { ping.get(HANG.inWholeMilliseconds, TimeUnit.MILLISECONDS) }.isFailure) driverLost = true
                throw IllegalStateException(
                    "The IDE did not answer ${method.name}: ${stuck ?: "within $HANG"}${if (driverLost) "; driver connection lost" else ""}",
                )
            } catch (failure: ExecutionException) {
                throw (failure.cause as? InvocationTargetException)?.targetException ?: failure.cause ?: failure
            }
        } as TourProbe

    init {
        // asked beside a call that may hold the EDT or the driver, which the activity needs too: an IDE that does not
        //  say counts toward the idle window, as one that does nothing would
        Quiescence.activity = { asked { probe.activity() } ?: NOT_ANSWERING }
        Quiescence.state = { asked { "${probe.activity().ifEmpty { "idle" }}; ${probe.serverState()}" } ?: "the IDE not answering" }
        val trace = TraceProgress(run.resolve("server-trace"))
        Quiescence.progress = { "${trace.events()} server trace events" }
        Quiescence.watching = true
    }

    /** What [question] answers, or null when the IDE does not answer within [ANSWER] or fails. */
    private fun <T> asked(question: () -> T): T? =
        runCatching { CompletableFuture.supplyAsync(question).get(ANSWER.inWholeMilliseconds, TimeUnit.MILLISECONDS) }.getOrNull()

    /** Writes the IDE's and the server's threads into the run's dumps, as a stuck wait left them; [why] names it. */
    private fun dumpThreads(why: String) {
        val dumps = Files.createDirectories(run.resolve("dumps"))
        val name = "%03d".format(failures.size)
        runCatching { Files.writeString(dumps.resolve("$name-ide.txt"), "$why\n\n${probe.ideThreads()}") }
        runCatching {
            val pid =
                probe
                    .serverState()
                    .substringAfter(' ')
                    .trim()
                    .toLong()
            val dump = ProcessBuilder("jcmd", "$pid", "Thread.print").redirectErrorStream(true).start()
            Files.writeString(dumps.resolve("$name-server.txt"), "$why\n\n${dump.inputStream.bufferedReader().readText()}")
        }
    }

    private val frames: Path = Files.createDirectories(run.resolve("frames"))
    val steps = mutableListOf<TourStep>()

    /** The checks that failed, each as `section: what: why`; the tour's test fails when there is any. */
    val failures = mutableListOf<String>()

    /**
     * The steps and checks not run, as `section: what: skipped: why`, because what they need is missing after an
     * earlier one failed: a cascade reads as its cause, in [failures], and these.
     */
    val skipped = mutableListOf<String>()

    /** The checks between the steps, as [verify] ran them, with their times. */
    val verifies = mutableListOf<TourCheck>()

    /** The last step or check that failed, which a skipped one names as its cause. */
    private var lastFailure: String? = null

    /** The failed step or check after which a dialog stayed open, which every later step waits on; null when none. */
    private var dialogLeft: String? = null

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
     * A step on the screen, which exercises [feature]: [act] does what the step shows; then, once [until] holds and the
     * subject [focus] names (see [TourProbe.capture]) shows, for at most [wait], a bound against a hang only, and after
     * [settle] for the screen to draw, the screen is captured with that subject as the step's highlight; [check] then
     * checks what the step shows, and [after] undoes what the step must not leave behind.
     */
    fun shot(
        title: String,
        caption: String,
        feature: String,
        focus: String = "none",
        settle: Duration = 1500.milliseconds,
        wait: Duration = HANG,
        until: TourProbe.() -> Boolean = { true },
        after: TourProbe.() -> Unit = {},
        needs: Set<Need> = emptySet(),
        check: TourProbe.() -> Unit = {},
        act: TourProbe.() -> Unit = {},
    ) {
        if (!recording || skips(title, needs)) return
        val started = System.currentTimeMillis()
        var shot: Long? = null
        val number = steps.size + 1
        val image = "frames/%03d.png".format(number)
        var step =
            try {
                ui.act()
                val shown = within(wait) { ui.until() && ui.showing(focus) }
                Thread.sleep(settle.inWholeMilliseconds)
                shot = System.currentTimeMillis()
                val answer = JsonParser.parseString(ui.capture(frames.resolve("%03d.png".format(number)).toString(), focus)).asJsonObject
                TourStep(
                    number,
                    section,
                    title,
                    caption,
                    feature,
                    image,
                    answer["focus"]?.asJsonObject,
                    answer["cursor"]?.asJsonObject,
                    error = if (shown) null else "the step's subject ($focus) did not show within $wait",
                )
            } catch (failure: Exception) {
                TourStep(number, section, title, caption, feature, error = causes(failure))
            }
        if (step.image != null) {
            checks++
            runCatching { ui.check() }.onFailure { failure -> step = step.copy(check = causes(failure)) }
        }
        step.error?.let { failures += "$section: $number $title: $it" }
        step.check?.let { failures += "$section: $number $title: check: $it" }
        step = step.copy(start = started, shot = shot)
        if (step.error != null || step.check != null) {
            Quiescence.stuck?.let { stuck ->
                step = step.copy(error = step.error?.let { "$it; $stuck" }, check = step.check?.let { "$it; $stuck" })
                failures[failures.size - 1] += "; $stuck"
                dumpThreads("$section: $number $title: $stuck")
            }
            lastFailure = "$number $title"
            cleanUp()
        }
        Quiescence.stuck = null
        runCatching { ui.after() }
        steps += step.copy(end = System.currentTimeMillis())
    }

    /**
     * A check between the steps, which the recording does not show: [block] fails with what it found when what the
     * tour has done did not do what it should, such as a refactoring that left the project with an error.
     */
    fun verify(
        what: String,
        needs: Set<Need> = emptySet(),
        block: TourProbe.() -> Unit,
    ) {
        if (!recording || skips(what, needs)) return
        checks++
        val started = System.currentTimeMillis()
        Quiescence.stuck = null
        val failure = runCatching { ui.block() }.exceptionOrNull()
        if (failure != null) {
            val stuck = Quiescence.stuck
            failures += "$section: $what: ${causes(failure)}${stuck?.let { "; $it" }.orEmpty()}"
            stuck?.let { dumpThreads("$section: $what: $it") }
            lastFailure = what
            cleanUp()
        }
        Quiescence.stuck = null
        verifies += TourCheck(section, what, started, System.currentTimeMillis(), failure?.let(::causes))
    }

    /**
     * Whether the step or check [what] is skipped, and recorded so in [skipped]: a dialog is still open after an
     * earlier failure's cleanup, or one of its [needs] is missing. It decides from the IDE's state as it is, not from
     * a time, so a step whose prerequisite can still come, such as a debug session that is launching, runs.
     */
    private fun skips(
        what: String,
        needs: Set<Need>,
    ): Boolean {
        if (driverLost) {
            skipped += "$section: $what: skipped: driver connection lost"
            return true
        }
        dialogLeft = dialogLeft?.takeIf { runCatching { ui.dialogShowing() }.getOrDefault(true) }
        val reason =
            dialogLeft?.let { "a dialog is still open after the cleanup of $it" }
                ?: needs.firstOrNull { !runCatching { it.holds(ui) }.getOrDefault(false) }?.let { need ->
                    "${need.missing} (${lastFailure?.let { "after $it failed" } ?: "no step had failed"})"
                }
                ?: return false
        skipped += "$section: $what: skipped: $reason"
        return true
    }

    /**
     * Closes what a failed step or check may have left open, popups and dialogs, as their Escape and Cancel do: a
     * modal dialog holds every later call that waits for the EDT outside it, so the rest of the tour would wait on it.
     */
    private fun cleanUp() {
        runCatching {
            ui.dismiss()
            ui.closeDialogs()
            if (!within(HANG) { !ui.dialogShowing() }) dialogLeft = lastFailure
        }.onFailure { println("Tour: what a failure left open was not closed: $it") }
    }

    private fun causes(failure: Throwable): String =
        generateSequence(failure) { it.cause }.joinToString(" <- ") { "${it.javaClass.simpleName}: ${it.message}" }

    /** Runs [block] as a step's preparation that captures nothing, in a recorded section only; a failure is not fatal. */
    fun prepare(block: TourProbe.() -> Unit) {
        if (recording) runCatching { ui.block() }.onFailure { println("Tour: preparation failed in $section: $it") }
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
        // the directories that steps added, such as a package that a class moved to, deepest first
        Files.walk(sources).use { files ->
            files
                .filter { Files.isDirectory(it) && !Files.exists(fixture.resolve(it.relativeTo(sources).toString())) }
                .toList()
                .sortedByDescending { it.nameCount }
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
            // and the server keeps up: the game, which uses most of its types, compiles as it did
            open(source("Eights.x"), 0)
            compilesCleanly(source("Eights.x"))
        }

    /**
     * Starts [command] in the project's directory, as a terminal there would, such as a program that the debugger
     * attaches to, and answers it with what it prints; [Program.close] ends it.
     */
    fun program(vararg command: String): Program =
        Program(ProcessBuilder(*command).directory(project.toFile()).redirectErrorStream(true).start())

    /** Waits up to [timeout] for the project to open in its window. */
    fun awaitProject(timeout: Duration = HANG) = awaitUi("the project opens", timeout) { ui.projectOpen() }

    /** Waits up to [timeout] for the IDE's background tasks, such as indexing and the Gradle import, to end. */
    fun awaitIdle(timeout: Duration = HANG) {
        var busy = ""
        awaitUi("the IDE's background tasks end", timeout, { "Still running: $busy" }) {
            busy = ui.busy()
            busy.isEmpty()
        }
    }

    /** Waits up to [timeout] for the language server to start. */
    fun awaitServer(timeout: Duration = HANG) = awaitUi("the Ecstasy language server starts", timeout) { ui.ready() }
}

/**
 * How long the tour waits for what it expects before a step or a check fails as hung: [HANG_LIMIT], which no IDE
 * reaches however slow or loaded the machine is, so that no result depends on its speed; `-Dxtc.tour.hang=<seconds>`
 * (`-PintellijTourHang`) shortens it, for a quicker look at a section whose checks fail.
 */
internal val HANG: Duration = System.getProperty("xtc.tour.hang")?.toLongOrNull()?.seconds ?: HANG_LIMIT

/** A program that the tour runs outside the IDE, as a terminal would, and what it has printed. */
internal class Program(
    private val process: Process,
) : AutoCloseable {
    private val printed = StringBuffer()

    init {
        Thread.ofVirtual().start { process.inputReader().forEachLine { printed.append(it).append('\n') } }
    }

    /** What the program has printed so far, its output and errors. */
    val output: String get() = printed.toString()

    /** Writes [text] to the program's input, as typing it in its terminal does. */
    fun input(text: String) {
        process.outputStream.write(text.toByteArray())
        process.outputStream.flush()
    }

    override fun close() {
        process.descendants().forEach(ProcessHandle::destroyForcibly)
        process.destroyForcibly()
    }
}

/** The IDE-side driver of the tour; see its implementation for what each method does. */
@Remote("org.xtclang.idea.playbook.probe.TourUi", plugin = "org.xtclang.playbook.probe")
internal interface TourProbe {
    fun busy(): String

    fun activity(): String

    fun ideThreads(): String

    fun projectOpen(): Boolean

    fun ready(): Boolean

    fun serverState(): String

    fun serverHasFile(path: String): Boolean

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

    fun selectFile(path: String)

    fun close(path: String)

    fun caret(
        text: String,
        occurrence: Int,
        delta: Int,
        select: Boolean,
    ): String

    fun action(id: String)

    fun editorAction(id: String)

    fun breakpointConditions(): String

    fun undoable(): String

    fun buildModules(): String

    fun debugInput(): String

    fun expandGroup(title: String): Boolean

    fun editorShortcut(id: String): String

    fun problemFile(path: String): Boolean

    fun selectedConfiguration(): String

    fun chord(
        first: String,
        second: String,
    )

    fun openMenu(menu: String)

    fun clickMenuItem(text: String): Boolean

    fun realClick(text: String): Boolean

    fun windowButton(
        window: String,
        id: String,
    ): Boolean

    fun showActions(on: Boolean)

    fun frameBounds(): String

    fun actionLog(): String

    fun toolbarState(id: String): String

    fun selectCurrentFile()

    fun runConfiguration(
        name: String,
        module: String,
        path: String,
    ): String

    fun windowAction(
        window: String,
        id: String,
    )

    fun windowActionEnabled(
        window: String,
        id: String,
    ): Boolean

    fun toolbar(id: String)

    fun type(text: String)

    fun insert(text: String)

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

    fun newFile(
        directory: String,
        name: String,
        text: String,
    )

    fun newDirectory(
        parent: String,
        name: String,
    )

    fun move(
        paths: String,
        destination: String,
    )

    fun selectItem(text: String): Boolean

    fun projectAction(
        path: String,
        id: String,
    )

    fun key(key: String)

    fun dismiss()

    fun closeDialogs()

    fun toolWindow(
        id: String,
        visible: Boolean,
    )

    fun toolWindowTab(
        id: String,
        tab: String,
    )

    fun showing(focus: String): Boolean

    fun fill(text: String)

    fun popupText(): String

    fun lookupItems(): String

    fun pickLookup(item: String): Boolean

    fun ghostText(): String

    fun selectLookup(item: String): Boolean

    fun lookupState(): String

    fun toolWindowText(id: String): String

    fun expand(
        id: String,
        row: String,
    )

    fun selectRow(
        id: String,
        row: String,
    ): Boolean

    fun diagnostics(path: String): String

    fun analyzed(path: String): Boolean

    fun highlighted(key: String): String

    fun keysAt(
        text: String,
        occurrence: Int,
    ): String

    fun inlays(line: Int): String

    fun lenses(): String

    fun clickLens(
        title: String,
        line: Int,
    )

    fun folds(): String

    fun fileText(path: String): String

    fun exists(path: String): Boolean

    fun editorState(): String

    fun moveCaret(delta: Int)

    fun focusState(): String

    fun focusEditor()

    fun editorFocused(): Boolean

    fun undoActionState(): String

    fun projectProblems(): String

    fun menuItems(): String

    fun runConfigurations(): String

    fun undoDirectly()

    fun redoDirectly()

    fun closeMenus()

    fun undoByAction()

    fun highlightsAtCaret(): String

    fun daemonState(path: String): String

    fun clearToolWindow(id: String)

    fun showModalMessage(
        title: String,
        message: String,
    )

    fun runFromFolder(
        path: String,
        name: String,
    ): String

    fun fileState(
        path: String,
        word: String,
    ): String

    fun watchTests()

    fun testResults(): String

    fun testsRunning(): String

    fun testRunStopped(): Boolean

    fun fillDialog(text: String)

    fun renaming(): String

    fun form(
        label: String,
        value: String,
    ): String

    fun formValue(label: String): String

    fun click(text: String)

    fun dialogShowing(): Boolean

    fun buttonEnabled(text: String): Boolean

    fun fileHas(
        path: String,
        text: String,
    ): Boolean

    fun choose(text: String): Boolean

    fun pick(label: String): Boolean

    fun rows(): String

    fun settings(id: String)

    fun templateContexts(): String

    fun formatOnSave(on: Boolean): Boolean

    fun serverTrace(value: String): String

    fun serverAction(id: String)

    fun gutter(
        line: Int,
        tooltip: String,
    )

    fun gutterIcons(): String

    fun breakpoint(
        line: Int,
        condition: String,
    )

    fun editBreakpoint(line: Int)

    fun clearBreakpoints()

    fun debugState(): String

    fun frames(): String

    fun threads(): String

    fun variables(path: String): String

    fun setValue(
        path: String,
        expression: String,
    ): String

    fun evaluate(
        expression: String,
        origin: String,
    ): String

    fun quickEvaluateState(): String

    fun smartTargets(): String

    fun hints(): String

    fun debugTab(name: String)

    fun debugConsole(): String

    fun runEnded(): Boolean

    fun consolesSettled(): Boolean

    fun runId(): String

    fun exitCode(): String

    fun input(text: String)

    fun typeInConsole(
        text: String,
        debug: Boolean,
    )

    fun console(): String

    fun buildMode(value: String): String

    fun runWith(value: String): String

    fun configure(
        field: String,
        value: String,
    ): String

    fun selectConfiguration(name: String): String

    fun attachConfiguration(
        name: String,
        port: Int,
    ): String

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
 * What a step or check needs that an earlier one makes, and that is missing once that one failed: each is a state the
 * IDE is in, or not, which [holds] reads as it is.
 */
internal enum class Need(
    val missing: String,
    val holds: TourProbe.() -> Boolean,
) {
    /** A debug session, or one that is launching. */
    DEBUG_SESSION("no debug session", { debug()["session"].asBoolean || "Launching" in busy() }),

    /** A selected run configuration, which Run and Debug run. */
    RUN_CONFIGURATION("no run configuration", { selectedConfiguration().isNotEmpty() }),

    /** A run's or a test run's content in the Run or Debug window. */
    RUN_WINDOW("no run or test window", { runId().isNotEmpty() }),

    /** The language server, started. */
    SERVER("the language server is not started", { serverState().startsWith("started") }),
}

/** Adds [expression], as the selected editor has it at its [occurrence], to the watches, as Add to Watches does. */
internal fun TourProbe.addWatch(
    expression: String,
    occurrence: Int = 0,
) {
    select(expression, occurrence)
    action("Debugger.AddToWatch")
}

/** The Debug window's text, with its watches beside the frame's variables. */
internal fun TourProbe.watches(): String = toolWindowText("Debug")

/** Whether [condition] holds within [timeout], polled: a bound against a hang, which a condition that holds never reaches. */
internal fun within(
    timeout: Duration,
    condition: () -> Boolean,
): Boolean {
    val deadline = TimeSource.Monotonic.markNow() + timeout
    var idleSince: TimeSource.Monotonic.ValueTimeMark? = null
    // what is in progress, with the server's trace, and since when it has stood still: in progress means progress made
    var progress = ""
    var stillSince = TimeSource.Monotonic.markNow()
    while (true) {
        if (runCatching(condition).getOrDefault(false)) return true
        if (deadline.hasPassedNow()) {
            Quiescence.stuck = "the bound of $timeout passed while ${Quiescence.activity().ifEmpty { "nothing was in progress" }}"
            return false
        }
        // nothing in progress that could still make the condition hold, or no answer from the IDE, all through the idle
        //  window: a hang, not slowness
        val activity = Quiescence.activity()
        idleSince = if (activity.isEmpty() || activity == NOT_ANSWERING) idleSince ?: TimeSource.Monotonic.markNow() else null
        if (Quiescence.watching && idleSince != null && idleSince.elapsedNow() >= IDLE) {
            Quiescence.stuck =
                if (activity == NOT_ANSWERING) {
                    "the IDE did not say what is in progress for $IDLE: a call holds its EDT or its driver"
                } else {
                    "nothing was in progress that could make it hold: ${Quiescence.state()}"
                }
            return false
        }
        // something in progress that does not move all through the idle window, the same text and no new server trace
        //  event, as run 16's "analyzing Player.x" with no pass running stood for its hour: a hang, too
        val now = "$activity; ${Quiescence.progress()}"
        if (now != progress) {
            progress = now
            stillSince = TimeSource.Monotonic.markNow()
        }
        if (Quiescence.watching && idleSince == null && stillSince.elapsedNow() >= IDLE) {
            Quiescence.stuck = "nothing that was in progress moved for $IDLE: $now"
            return false
        }
        Thread.sleep(250)
    }
}

/** How long the IDE has to say what is in progress, beside a call that may hold its EDT, before it counts as silent. */
private val ANSWER = 30.seconds

/** What [Quiescence.activity] says for an IDE that does not answer it, which counts toward the idle window. */
internal const val NOT_ANSWERING = "the IDE, not answering what is in progress"

/**
 * How long a wait sees nothing in progress, without a break, before it fails as stuck: the IDE with no background task,
 * no running process or launching session, no pending event and no analysis, and the server with no request unanswered
 * and no compiler job. Long enough to span a debounce or a slow first answer on a loaded machine, which can leave
 * seconds with nothing visibly in progress; a real hang still shows in about a minute.
 */
private val IDLE = 60.seconds

/**
 * What the tour's waits ask about the IDE between their polls: whether anything is in progress that could still make
 * their condition hold, which [within] reads, and the wait that last gave up, which a failure's message and dumps
 * name. The tour sets it up as it starts; a wait outside a tour has nothing to ask and waits out its bound.
 */
internal object Quiescence {
    /** What is in progress, empty when nothing is; set by the tour. */
    @Volatile
    var activity: () -> String = { "" }

    /** The IDE's state when a wait gave up, for its message; set by the tour. */
    @Volatile
    var state: () -> String = { "" }

    /**
     * How far the work beside [activity] has gone, which moves while it runs, such as the count of the server's trace
     * events; set by the tour.
     */
    @Volatile
    var progress: () -> String = { "" }

    /** Whether waits fail as soon as nothing is in progress; off until the tour is set up. */
    @Volatile
    var watching = false

    /** How the last wait that gave up stood, or null; a step's or check's failure reads and clears it. */
    @Volatile
    var stuck: String? = null
}

/**
 * The count of the language server's trace events in [directory], its JSON lines, but the ones of the xtc/busy requests
 * that the tour's own waits send: it grows while the server answers requests and compiles, each leaving events there,
 * and stands still while the server does nothing, however long its last request or job has been in hand. It reads each
 * file from where it last stopped, whole lines only.
 */
internal class TraceProgress(
    private val directory: Path,
) {
    private val read = mutableMapOf<Path, Long>()
    private var events = 0L

    @Synchronized
    fun events(): Long {
        val files =
            runCatching { Files.list(directory).use { paths -> paths.filter { it.toString().endsWith(".jsonl") }.toList() } }
                .getOrDefault(emptyList())
        files.forEach { file ->
            runCatching {
                FileChannel.open(file, StandardOpenOption.READ).use { channel ->
                    val from = read[file] ?: 0L
                    if (channel.size() > from) {
                        val bytes = ByteBuffer.allocate((channel.size() - from).toInt())
                        channel.read(bytes, from)
                        val end = (bytes.position() - 1 downTo 0).firstOrNull { bytes.get(it) == '\n'.code.toByte() }
                        if (end != null) {
                            events += String(bytes.array(), 0, end, Charsets.UTF_8).lineSequence().count { BUSY !in it }
                            read[file] = from + end + 1
                        }
                    }
                }
            }
        }
        return events
    }
}

/** How a trace line of an xtc/busy request names it. */
private const val BUSY = "\"operation\":\"xtc/busy\""

/**
 * Opens Show Intention Actions on the caret or selection, and again, up to [INTENTION_OPENINGS] times, while its list,
 * once the IDE has found what it offers, has no row with [row], as a user would press Alt+Enter again; answers whether
 * it did, each opening waited for as [within] waits, within [timeout].
 */
internal fun TourProbe.intentions(
    row: String,
    timeout: Duration = HANG,
): Boolean {
    repeat(INTENTION_OPENINGS) { opening ->
        // no input while IntelliJ searches for context actions: a key pressed then waits in the queue and reaches the
        //  list once it opens, where it chooses the first row, which may be the very fix a check looks for
        within(timeout) { !searching() }
        // TODO LSP4IJ: UP07 — overlapping full pulls can cancel a lazy code action while the list discovers it, so
        //  the list can open without the server's actions; the playbook's quickFix reopens it for the same reason.
        //  LSP4IJ answers the list again from its cache for the same text and caret, so the caret goes off the spot
        //  and back first, as a user's would, once the IDE has looked at the other spot
        if (opening > 0) {
            val path = editor()["path"].asString
            moveCaret(1)
            within(timeout) { analyzed(path) && "code actions" !in activity() }
            moveCaret(-1)
            within(timeout) { analyzed(path) && "code actions" !in activity() }
        }
        // Alt+Enter in the focused editor, as a user presses it there: run 16 searched for the list three times and
        //  showed it none of them, with the tour's unrecorded input, which gives the editor no focus of its own
        focusedEditor()
        action("ShowIntentionActions")
        if (within(timeout) { row in rows() || listed() } && row in rows()) return true
        within(timeout) { !searching() }
        dismiss()
    }
    return false
}

/** Whether IntelliJ still searches for the context actions that Show Intention Actions lists. */
private fun TourProbe.searching(): Boolean = "Searching for Context Actions" in popupText()

/** How many times [intentions] opens the list before it gives up on a row. */
private const val INTENTION_OPENINGS = 3

/**
 * Whether Show Intention Actions lists what the IDE found: rows, and neither IntelliJ's search for context actions nor
 * LSP4IJ's check of the server's code actions still runs.
 */
private fun TourProbe.listed(): Boolean = rows().isNotBlank() && !searching() && "code actions" !in activity()

/**
 * What Show Intention Actions lists, and the caret or selection it lists it for: the message of a check whose row is
 * missing, as the list has closed by then. It reads the list as [intentions] does, and is no check itself.
 */
internal fun TourProbe.offered(): String {
    within(HANG) { !searching() }
    val focus = focusedEditor()
    action("ShowIntentionActions")
    within(HANG) { listed() }
    val rows = rows()
    dismiss()
    return "[${rows.lines().joinToString(" | ")}] at ${editorState()}; the focus: $focus"
}

/** Chooses the intention [row], once Show Intention Actions lists it, as Enter on it does; fails if it never does. */
internal fun TourProbe.apply(row: String) {
    check(intentions(row)) { "No \"$row\": ${offered()}" }
    check(choose(row)) { "No \"$row\": ${rows()}" }
}

/**
 * Presses the first of a dialog's [buttons] that the dialog enables, as it does once its input is valid, such as OK, or
 * Save where a save dialog has that, and waits for the dialog to close; answers whether it did within [timeout].
 */
internal fun TourProbe.confirm(
    vararg buttons: String,
    timeout: Duration = HANG,
): Boolean {
    if (!within(timeout) { buttons.any(::buttonEnabled) }) return false
    click(buttons.first(::buttonEnabled))
    return within(timeout) { !dialogShowing() }
}

/**
 * Undoes the selected editor's last change, as the keymap's Undo shortcut there does, Ctrl+Z or Cmd+Z, and fails
 * unless the change is [undone] then. The editor must be as the change left it, as [TourProbe.selectFile] keeps it:
 * IntelliJ's first Undo after the caret has left the change only takes the caret back to it. IntelliJ asks first
 * whether to undo a command whose undo policy asks for it, as its own refactorings' does, and the plugin's commands, a
 * rename's too, do not: [asks] says which this is, and the question is answered.
 */
internal fun TourProbe.undo(
    asks: Boolean,
    undone: () -> Boolean,
) {
    val before = undoable()
    // where the focus is and what the selected editor's document holds before the key, and where the focus is when it
    // goes, so that a failure says what the key reached
    val focus = focusState()
    val path = editor()["path"].asString
    val text = fileText(path).lines()
    // the keymap's Undo key in the focused editor, as a user presses it
    focusedEditor()
    val pressed = editorShortcut("\$Undo")
    val notes = {
        val after = fileText(path).lines()
        val lost = (text - after.toSet()).take(LINES).map(String::trim)
        val gained = (after - text.toSet()).take(LINES).map(String::trim)
        val change = if (lost.isEmpty() && gained.isEmpty()) "unchanged" else "lost $lost and gained $gained"
        "it was to undo $before, and now ${undoable()}; the Undo action is ${undoActionState()}; the focus before: $focus; " +
            "at the key: $pressed; ${path.substringAfterLast('/')} since: $change"
    }

    // where the key ran the Undo action and it undid nothing, whether UndoManager itself undoes, asked directly, as the
    //  action asks it: runs 16 and 18b got "ran $Undo" with the document unchanged, and the direct undo, the second,
    //  undid it, as the key's had only taken the caret back to the change, which the step had opened its file away from
    // an undo as [undo] asks for it, its question answered: how it ended
    fun undid(undo: () -> Unit): String {
        undo()
        val asked = within(HANG) { undone() || buttonEnabled("Undo") } && !undone()
        if (asked) confirm("Undo")
        return when {
            within(HANG) { undone() } -> "undid it${if (asked) ", once asked" else ""}"
            asked -> "asked, and did not undo it"
            else -> "did not undo it"
        }
    }
    // then, redone, the Undo action performed with the editor's context, as CodeActionUndoTest performs it and undoes:
    //  the redo leaves the caret at the change, so this one undoes at once
    val directly = {
        val direct = undid { undoDirectly() }
        val byAction =
            if (!direct.startsWith("undid")) {
                "not tried"
            } else {
                redoDirectly()
                if (within(HANG) { !undone() }) undid { undoByAction() } else "not tried, as the redo did not redo it"
            }
        "$direct; then, redone, the Undo action performed with the editor's context: $byAction"
    }
    expect(within(HANG) { undone() || buttonEnabled("Undo") }) {
        "Undo neither undid the change nor asked whether to; ${notes()}; UndoManager's undo of the selected editor, " +
            "asked directly then: ${directly()}"
    }
    if (asks) {
        expect(!undone()) { "Undo did not ask first" }
        expect(confirm("Undo")) { "Undo's question did not take its answer" }
    } else {
        expect(!buttonEnabled("Undo")) { "Undo asked first" }
    }
    expect(within(HANG) { undone() }) { "Undo did not undo the change; ${notes()}" }
}

/**
 * Gives the selected editor the keyboard focus, as a user's click into it does, and answers where the focus is then,
 * as [TourProbe.focusState]: what the editor opens for its keys, such as Show Intention Actions' list, follows the
 * focus. The wait for it ends once the editor has it, or the idle window passes without it.
 */
internal fun TourProbe.focusedEditor(): String {
    focusEditor()
    within(HANG) { editorFocused() }
    return focusState()
}

/** Presses the shortcut [first], then [second], in the selected editor once it has the focus, as a user does. */
internal fun TourProbe.editorChord(
    first: String,
    second: String,
) {
    focusedEditor()
    chord(first, second)
}

/** How many of a document's changed lines a failure's message names. */
private const val LINES = 3

/**
 * Renames what is at the caret to [name], as Shift+F6 does: in its dialog, which checks the name first and then takes
 * Refactor, or in place, when the rename edits the name in the editor and Enter ends it.
 */
internal fun TourProbe.rename(name: String) {
    action("RenameElement")
    check(within(HANG) { renaming().isNotEmpty() }) { "Rename started neither a dialog nor a rename in place" }
    if (renaming() == "dialog") {
        fillDialog(name)
        check(confirm("Refactor")) { "The rename dialog did not take $name" }
    } else {
        // in place: the name is selected in the editor, and typing replaces it
        type(name)
        type("\n")
    }
}

/** Fails with [message], which says what was found, unless [condition] holds: a step's or a [Tour.verify]'s check. */
internal fun expect(
    condition: Boolean,
    message: () -> String,
) {
    check(condition, message)
}

/**
 * Fails unless, within [timeout], the editor of the file at [path] has analyzed its text and marks no error, as a
 * refactoring or a fix must leave the code: it compiles. The bound is against a hang only.
 */
internal fun TourProbe.compilesCleanly(
    path: String,
    timeout: Duration = HANG,
) {
    expect(within(timeout) { analyzed(path) && "ERROR" !in diagnostics(path) }) {
        "Errors in ${Path.of(path).fileName}: ${diagnostics(path)}"
    }
}

/** Fails unless, within [timeout], the editor of the file at [path] marks an error whose message contains [text]. */
internal fun TourProbe.hasError(
    path: String,
    text: String = "",
    timeout: Duration = HANG,
) {
    expect(within(timeout) { diagnostics(path).lines().any { it.startsWith("ERROR") && text in it } }) {
        "No error${if (text.isEmpty()) "" else " with $text"} in ${Path.of(path).fileName}: ${diagnostics(path)}"
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

/** The result of the test run since [TourProbe.watchTests] whose location ends with [name], or null when it has none. */
internal fun TourProbe.testResult(name: String): JsonObject? =
    JsonParser
        .parseString(testResults())
        .asJsonObject
        .entrySet()
        .firstOrNull { it.key.endsWith(name) }
        ?.value
        ?.asJsonObject

/** The results of the tests run since [TourProbe.watchTests], each test's location and state, such as xunit://Deck.works=passed. */
internal fun TourProbe.testStates(): Map<String, String> =
    JsonParser
        .parseString(testResults())
        .asJsonObject
        .entrySet()
        .associate { it.key to it.value.asJsonObject["state"].asString }

/** The current debug session, as [TourProbe.debugState] answers it. */
internal fun TourProbe.debug(): JsonObject = JsonParser.parseString(debugState()).asJsonObject

/** Whether the current debug session is paused, on the 1-based [line] when it is not 0. */
internal fun TourProbe.pausedAt(line: Int = 0): Boolean =
    debug().let { it["suspended"].asBoolean && (line == 0 || it["line"]?.asInt == line) }

/** Whether the current debug session is paused in the file [name], on its 1-based [line] when that is not 0. */
internal fun TourProbe.pausedIn(
    name: String,
    line: Int = 0,
): Boolean = debug().let { it["suspended"].asBoolean && it["file"]?.asString == name && (line == 0 || it["line"]?.asInt == line) }

/** Where the current debug session is paused, as file:line, or empty while it runs. */
internal fun TourProbe.position(): String =
    debug().let { if (it["suspended"].asBoolean) "${it["file"]?.asString}:${it["line"]?.asInt}" else "" }

/** The current debug session's paused variables, or those at [path], by name, each as its value. */
internal fun TourProbe.values(path: String = ""): Map<String, String> =
    JsonParser
        .parseString(variables(path))
        .asJsonArray
        .map { it.asJsonObject }
        .associate { it["name"].asString to it["value"]?.asString.orEmpty() }

/** The names of the current debug session's paused frames, innermost first. */
internal fun TourProbe.frameNames(): List<String> =
    JsonParser
        .parseString(frames())
        .asJsonArray
        .map { it.asJsonObject["name"].asString }
