package org.xtclang.idea.playbook

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import org.xtclang.idea.playbook.probe.HANG_LIMIT
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalTime
import java.time.temporal.ChronoUnit
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
    /** The user guide's image that the step's frame makes. */
    val guide: GuideImage? = null,
    /** When the step began, its screen was captured, and it ended, in epoch milliseconds, the screen recording's clock. */
    val start: Long? = null,
    val shot: Long? = null,
    val end: Long? = null,
)

/**
 * A screenshot of the user guide, lang/doc/user-guide/images/[name].png, that a step's frame makes: lang/intellij-plugin/tour/
 * guide.py crops the frame to [crop], ImageMagick geometry on the tour's 1920x936 frame, or keeps all of it.
 */
internal data class GuideImage(
    val name: String,
    val crop: String? = null,
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
                if (
                    runCatching { call.get(1, TimeUnit.SECONDS) }.isFailure &&
                    !call.isDone &&
                    !within(HANG, "the IDE's answer to ${method.name}") { call.isDone }
                ) {
                    throw TimeoutException()
                }
                call.get()
            } catch (_: TimeoutException) {
                val stuck = Quiescence.stuck
                val ping = CompletableFuture.supplyAsync { probe.projectOpen() }
                // waited for as within() waits, which reports every minute and ends once nothing is in progress, not in
                //  silence up to the hang's bound
                if (!within(HANG, "the IDE's answer to a ping after ${method.name}") { ping.isDone } ||
                    runCatching { ping.get() }.isFailure
                ) {
                    driverLost = true
                }
                throw IllegalStateException(
                    "The IDE did not answer ${method.name}: ${stuck ?: "within $HANG"}${if (driverLost) "; driver connection lost" else ""}",
                )
            } catch (failure: ExecutionException) {
                throw (failure.cause as? InvocationTargetException)?.targetException ?: failure.cause ?: failure
            }
        } as TourProbe

    init {
        Quiescence.watch(probe, run) { line -> progress("${section.ifEmpty { "the start" }}: $line") }
    }

    /** Writes the IDE's and the server's threads into the run's dumps, as a stuck wait left them; [why] names it. */
    private fun dumpThreads(why: String) {
        val dumps = Files.createDirectories(run.resolve("dumps"))
        val name = "%03d".format(failures.size)
        val threads = dumpAnswer { probe.ideThreads() } ?: "the IDE did not give its threads within $DUMP_ANSWER"
        runCatching { Files.writeString(dumps.resolve("$name-ide.txt"), "$why\n\n$threads") }
        runCatching {
            val pid =
                requireNotNull(dumpAnswer { probe.serverState() })
                    .substringAfter(' ')
                    .trim()
                    .toLong()
            val dump = ProcessBuilder("jcmd", "$pid", "Thread.print").redirectErrorStream(true).start()
            Files.writeString(dumps.resolve("$name-server.txt"), "$why\n\n${dump.inputStream.bufferedReader().readText()}")
        }
    }

    /**
     * [call]'s answer for a dump, or none when the IDE does not give it within [DUMP_ANSWER]: a dump is written once a wait
     * has given up, so its own calls are bounded rather than waited for, and decide no result.
     */
    private fun <T> dumpAnswer(call: () -> T): T? =
        runCatching { CompletableFuture.supplyAsync(call).get(DUMP_ANSWER.inWholeMilliseconds, TimeUnit.MILLISECONDS) }.getOrNull()

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

    /**
     * How the language server of process [pid] ended the first `textDocument/hover` request it got that the client did
     * not cancel, as its trace says: `replied`, or `error` for one it rejected; null until that request has ended. A
     * request the client canceled ends with RequestCancelled (-32800): LSP4IJ cancels a hover it counts as outdated
     * once a file of the project has changed, as often right after a restart, and asks again. A line still being
     * written does not parse, and is read the next time.
     */
    fun firstHover(pid: String): String? {
        val files =
            runCatching { Files.list(run.resolve("server-trace")).use { paths -> paths.toList() } }
                .getOrDefault(emptyList())
                .filter { it.fileName.toString().startsWith("lsp-trace-$pid-") }
        val hovers =
            files
                .flatMap { it.readText().lines() }
                .mapNotNull { line -> runCatching { JsonParser.parseString(line).asJsonObject }.getOrNull() }
                .filter { it["kind"]?.asString == "lsp-request" && it["operation"]?.asString == "textDocument/hover" }
        val ends = hovers.filter { it["event"]?.asString == "end" }.associateBy { it["id"] }
        for (start in hovers.filter { it["event"]?.asString == "start" && it["direction"]?.asString == "from-client" }) {
            val end = ends[start["id"]] ?: return null
            if (end["errorCode"]?.asInt != REQUEST_CANCELLED) return end["outcome"]?.asString
        }
        return null
    }

    /** The process ids of the language servers that have written a trace in this run, which ended ones keep. */
    fun tracedServers(): Set<String> =
        runCatching { Files.list(run.resolve("server-trace")).use { paths -> paths.toList() } }
            .getOrDefault(emptyList())
            .mapNotNull { TRACE_FILE.matchEntire(it.fileName.toString())?.groupValues?.get(1) }
            .toSet()

    fun section(name: String) {
        section = name
        progress("Section $name")
    }

    /** A line of the run's progress in the build's output, as the VS Code tour prints it, with the time of day. */
    private fun progress(line: String) = println("[tour] ${LocalTime.now().truncatedTo(ChronoUnit.SECONDS)} $line")

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
     * checks what the step shows, and [after] undoes what the step must not leave behind. A step whose frame makes a
     * screenshot of the user guide names it as its [guide].
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
        guide: GuideImage? = null,
        check: TourProbe.() -> Unit = {},
        act: TourProbe.() -> Unit = {},
    ) {
        if (!recording || skips(title, needs)) return
        val started = System.currentTimeMillis()
        var shot: Long? = null
        val number = steps.size + 1
        progress("$number $title: starts")
        Quiescence.begin("$number $title")
        val image = "frames/%03d.png".format(number)
        var step =
            try {
                // what can close a popup, as a popup that did not show can have shown and closed: counted from the first
                //  popup step on, and read as the act ends and as the wait does
                if (focus == "popup") ui.popupClosers()
                ui.act()
                val acted = if (focus == "popup") ui.popupClosers() else null
                val shown = within(wait) { ui.until() && ui.showing(focus) }
                val waited = if (acted != null && !shown) ui.popupClosers() else null
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
                    error =
                        when {
                            shown -> null

                            acted == null -> "the step's subject ($focus) did not show within $wait"

                            else ->
                                "the step's subject ($focus) did not show within $wait; what closes a popup, as its act " +
                                    "ended: $acted; as the wait ended: $waited"
                        },
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
        step = step.copy(guide = guide, start = started, shot = shot)
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
        progress("$number $title: ${if (step.error == null && step.check == null) "ok" else "FAILED"}")
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
        progress("check $what: starts")
        Quiescence.begin("check $what")
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
        progress("check $what: ${if (failure == null) "ok" else "FAILED"}")
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
        progress("$what: skipped: $reason")
        return true
    }

    /**
     * Closes what a failed step or check may have left open, popups and dialogs, as their Escape and Cancel do: a
     * modal dialog holds every later call that waits for the EDT outside it, so the rest of the tour would wait on it.
     */
    private fun cleanUp() {
        runCatching {
            ui.dismiss()
            if (!closeDialogs()) dialogLeft = lastFailure
        }.onFailure { println("Tour: what a failure left open was not closed: $it") }
    }

    /**
     * Closes the dialogs that show, as [TourProbe.closeDialogs] does, and answers whether none shows once that has run:
     * it queues each dialog's Cancel on the EDT, which runs the calls made after it once it has run those, so a dialog
     * that shows then stays open, but a progress's, which closes once its task ends, is waited for. A dialog that
     * stays is not waited on: nothing is to close it, and a wait for that would end only at a hang's bound.
     */
    private fun closeDialogs(): Boolean {
        ui.closeDialogs()
        within(HANG, "a progress's dialog to close") { !ui.progressShowing() }
        return !ui.dialogShowing()
    }

    private fun causes(failure: Throwable): String =
        generateSequence(failure) { it.cause }.joinToString(" <- ") { "${it.javaClass.simpleName}: ${it.message}" }

    /** Runs [block] as a step's preparation that captures nothing, in a recorded section only; a failure is not fatal. */
    fun prepare(block: TourProbe.() -> Unit) {
        if (!recording) return
        Quiescence.begin("a preparation")
        runCatching { ui.block() }.onFailure { println("Tour: preparation failed in $section: $it") }
    }

    /**
     * Puts the project's sources back as [fixture] has them, in the editors and on disk, and deletes the sources that
     * steps added, such as a renamed class's file; then closes popups and dialogs and stops runs.
     */
    fun restore() {
        if (!recording) return
        Quiescence.begin("the restore of the project's sources")
        runCatching {
            ui.dismiss()
            // a modal dialog holds every call that waits for the EDT outside it, as restoring the files does
            if (!closeDialogs()) repeat(3) { ui.key("ESCAPE") }
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

    /** Runs the card game with xtc run, as a terminal would, waiting for a debugger to attach on the port [body] gets. */
    fun gameAwaitingDebugger(body: (Program, Int) -> Unit) {
        val xtc = PlaybookIde.XDK_BIN.resolve("xtc").toString()
        program(xtc, "run", "--debug", "listen:127.0.0.1:0,suspend", "--deduce", "cardGame").use { game ->
            val listening = Regex("Listening for the debugger at [^ ]+:(\\d+)")
            expect(within(HANG) { listening.containsMatchIn(game.output) }) { "The program: ${game.output.takeLast(400)}" }
            body(game, listening.find(game.output)!!.groupValues[1].toInt())
        }
    }

    /** Waits up to [timeout] for the project to open in its window. */
    fun awaitProject(timeout: Duration = HANG) = awaitStart("the project opens", timeout) { ui.projectOpen() }

    /** Waits up to [timeout] for the IDE's background tasks, such as indexing and the Gradle import, to end. */
    fun awaitIdle(timeout: Duration = HANG) {
        var busy = ""
        awaitStart("the IDE's background tasks end", timeout, { "Still running: $busy" }) {
            busy = ui.busy()
            busy.isEmpty()
        }
    }

    /** Waits up to [timeout] for the language server to start. */
    fun awaitServer(timeout: Duration = HANG) = awaitStart("the Ecstasy language server starts", timeout) { ui.ready() }

    /**
     * Waits as [within] does, up to [timeout], until [condition] holds, which [what] names, as the tour starts, and
     * fails with [errorMessage] and how the wait stood, or with what [condition] failed with. The IDE's start, as
     * [TourProbe.starting] says it, is [Quiescence.start] meanwhile, which every wait sees, a probe call's too.
     */
    private fun awaitStart(
        what: String,
        timeout: Duration,
        errorMessage: (() -> String)? = null,
        condition: () -> Boolean,
    ) {
        Quiescence.stuck = null
        Quiescence.start = { asked { probe.starting() }?.let(Start::of) ?: Start() }
        try {
            check(withinOrFail(timeout, what, condition)) { "${errorMessage?.invoke() ?: "Failed: $what"}; ${Quiescence.stuck}" }
        } finally {
            Quiescence.start = { Start() }
        }
    }
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

    fun work(): String

    fun starting(): String

    fun projectOpen(): Boolean

    fun ready(): Boolean

    fun serverState(): String

    fun settledServerState(): String

    /** The process ids of the language servers that the IDE runs, as a JSON array. */
    fun serverProcesses(): String

    /** Got It tooltips on, with [id] unseen, for a check of it. */
    fun enableGotIt(id: String)

    /** Whether the selected editor shows a Got It tooltip. */
    fun gotItShown(): Boolean

    /** Clicks the Got It tooltip that shows away, true when one showed, and turns Got It tooltips off again. */
    fun dismissGotIt(): Boolean

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

    fun keysGot(): String

    fun showing(focus: String): Boolean

    fun fill(text: String)

    fun popupText(): String

    fun popupClosers(): String

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

    fun heldDiagnostics(path: String): String

    fun analyzed(path: String): Boolean

    fun highlighted(key: String): String

    fun keysAt(
        text: String,
        occurrence: Int,
    ): String

    fun inlays(line: Int): String

    fun lenses(): String

    fun lensIdentities(): String

    fun lensesPending(): Boolean

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

    fun progressShowing(): Boolean

    fun dialogTitled(title: String): Boolean

    fun buttonEnabled(text: String): Boolean

    fun fileHas(
        path: String,
        text: String,
    ): Boolean

    fun choose(text: String): Boolean

    fun chooseRow(text: String): Boolean

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

/**
 * Whether [condition] holds within [timeout], polled: a bound against a hang, which a condition that holds never reaches.
 * A wait that goes on says so about once a minute, as the VS Code tour's waits do: what it waits for, [what] in the step
 * or check that waits, and what is in progress. It fails before its bound once nothing that could make [condition] hold
 * has been in progress, or nothing in progress has moved, for [IDLE]; the server's own traffic is neither. While the
 * IDE opens the project, as [Quiescence.start] says, only the bound ends the wait; its startup activities, after that,
 * are in progress beside what [Quiescence.activity] says.
 */
internal fun within(
    timeout: Duration,
    what: String? = null,
    condition: () -> Boolean,
): Boolean {
    val deadline = TimeSource.Monotonic.markNow() + timeout
    val report = WaitReport(listOfNotNull(what, Quiescence.doing.ifEmpty { null }).joinToString(", in ").ifEmpty { "a condition" })
    var idleSince: TimeSource.Monotonic.ValueTimeMark? = null
    // what is in progress, with the step's trace, and since when it has stood still: in progress means progress made
    var progress = ""
    var stillSince = TimeSource.Monotonic.markNow()
    // the server's own traffic while nothing was in progress, which the idle failure names: it goes on, and decides nothing
    var traffic = Quiescence.traffic()
    while (true) {
        if (runCatching(condition).getOrDefault(false)) return true
        val start = Quiescence.start()
        if (deadline.hasPassedNow()) {
            val underWay = start.opening.ifEmpty { Quiescence.activity().text }
            Quiescence.stuck = "the bound of $timeout passed while ${underWay.ifEmpty { "nothing was in progress" }}"
            return false
        }
        // the IDE, opening the project before its window shows, does what the probe cannot ask about
        if (start.opening.isNotEmpty()) {
            idleSince = null
            stillSince = TimeSource.Monotonic.markNow()
            report.due { start.opening }
            Thread.sleep(250)
            continue
        }
        // nothing in progress that could still make the condition hold, or no answer from the IDE, all through the idle
        //  window: a hang, not slowness
        val activity = Quiescence.activity().let { if (start.activities) it.starting() else it }
        if (idleSince == null) traffic = Quiescence.traffic()
        idleSince = if (activity.text.isEmpty() || activity.text == NOT_ANSWERING) idleSince ?: TimeSource.Monotonic.markNow() else null
        if (Quiescence.watching && idleSince != null && idleSince.elapsedNow() >= IDLE) {
            Quiescence.stuck =
                if (activity.text == NOT_ANSWERING) {
                    "the IDE did not say what is in progress for $IDLE: a call holds its EDT or its driver"
                } else {
                    "nothing that could make it hold was in progress for $IDLE, while the server's own traffic added " +
                        "${Quiescence.traffic() - traffic} trace events: ${Quiescence.state()}"
                }
            return false
        }
        // something in progress that does not move all through the idle window, the same text and no new trace event of
        //  the steps' work, as run 16's "analyzing Player.x" with no pass running stood for its hour: a hang, too; but
        //  not while the IDE runs a task, process or debug session of its own, which moves where the wait cannot always
        //  see it, as a Gradle build that prints nothing while its daemon starts or a task compiles
        val now = "${activity.text}; ${Quiescence.progress()}"
        if (now != progress || activity.live) {
            progress = now
            stillSince = TimeSource.Monotonic.markNow()
        }
        if (Quiescence.watching && idleSince == null && stillSince.elapsedNow() >= IDLE) {
            Quiescence.stuck = "nothing that was in progress moved for $IDLE: $now"
            return false
        }
        report.due { activity.text }
        Thread.sleep(250)
    }
}

/** Whether [condition] holds within [timeout], as [within] waits for it, which [what] names; a failure of [condition] ends the wait and is thrown. */
internal fun withinOrFail(
    timeout: Duration,
    what: String,
    condition: () -> Boolean,
): Boolean {
    var failure: Throwable? = null
    val held =
        within(timeout, what) {
            runCatching(condition).getOrElse {
                failure = it
                true
            }
        }
    failure?.let { throw it }
    return held
}

/** What [question] answers, or null when the IDE does not answer within [ANSWER] or fails. */
private fun <T> asked(question: () -> T): T? =
    runCatching { CompletableFuture.supplyAsync(question).get(ANSWER.inWholeMilliseconds, TimeUnit.MILLISECONDS) }.getOrNull()

/** How long the IDE has to say what is in progress, beside a call that may hold its EDT, before it counts as silent. */
private val ANSWER = 30.seconds

/** What [Quiescence.activity] says for an IDE that does not answer it, which counts toward the idle window. */
internal const val NOT_ANSWERING = "the IDE, not answering what is in progress"

/**
 * How long a wait sees nothing in progress, without a break, before it fails as stuck: the IDE with no background task,
 * no running process or launching session, no pending event and no analysis, and the server with no request of the
 * step's unanswered and no compiler job. Long enough to span a debounce or a slow first answer on a loaded machine,
 * which can leave seconds with nothing visibly in progress; a real hang still shows in about a minute.
 */
private val IDLE = 60.seconds

/** How often a wait that has not ended says what it waits for, as the VS Code tour's waits do. */
private val REPORT = 60.seconds

/** How long a dump waits for each of the IDE's answers it asks for, once a wait has given up. */
private val DUMP_ANSWER = 60.seconds

/**
 * What a wait that goes on says, about once a minute since it began, as the VS Code tour's waits do: how long it has
 * waited, for [what], and what is in progress, which [Quiescence.report] writes into the run's progress.
 */
private class WaitReport(
    private val what: String,
) {
    private val begun = TimeSource.Monotonic.markNow()
    private var next = REPORT

    /** Reports, once another minute has passed since the last report, with [underWay] as what is in progress. */
    fun due(underWay: () -> String) {
        if (begun.elapsedNow() < next) return
        Quiescence.report("waiting ${begun.elapsedNow().inWholeSeconds} s for: $what; under way: ${underWay().ifEmpty { "nothing" }}")
        next = begun.elapsedNow() + REPORT
    }
}

/**
 * What the tour's and the playbook's waits ask about the IDE between their polls: whether anything is in progress that
 * could still make their condition hold, which [within] reads, and the wait that last gave up, which a failure's message
 * and dumps name. [watch] sets it up as the tour or the playbook starts; a wait outside them has nothing to ask and
 * waits out its bound.
 */
internal object Quiescence {
    /** The step, check or case that runs, which a wait's report names; set by [begin]. */
    @Volatile
    var doing = ""

    /** Writes a wait's report into the run's progress; set by [watch]. */
    @Volatile
    var report: (String) -> Unit = {}

    /** What is in progress that could make a wait's condition hold; set by [watch]. */
    @Volatile
    var activity: () -> Activity = { Activity("") }

    /** How far the IDE's start is, which a wait at the tour's start sets; done otherwise. */
    @Volatile
    var start: () -> Start = { Start() }

    /** The IDE's state when a wait gave up, with what it does that no wait counts, for its message; set by [watch]. */
    @Volatile
    var state: () -> String = { "" }

    /**
     * How far the work beside [activity] has gone, which moves while it runs, such as the count of the trace events of
     * the steps' work; set by [watch].
     */
    @Volatile
    var progress: () -> String = { "" }

    /** How much of the server's own traffic its trace has, which no wait counts, for a failure's message; set by [watch]. */
    @Volatile
    var traffic: () -> Long = { 0L }

    /** What [begin] does as a step, check, preparation, restore or case begins; set by [watch]. */
    @Volatile
    var begun: () -> Unit = {}

    /** Whether waits fail as soon as nothing is in progress; off until [watch]. */
    @Volatile
    var watching = false

    /** How the last wait that gave up stood, or null; a step's or check's failure reads and clears it. */
    @Volatile
    var stuck: String? = null

    /** Begins [what], a step, check, preparation, restore or case, whose waits count what the client sends from now on. */
    fun begin(what: String) {
        doing = what
        begun()
    }

    /**
     * Sets the waits up for a run of the IDE that [probe] asks, whose language servers trace into [run]'s server-trace
     * directory, with [report] writing a wait's line every minute. What counts as in progress is what could make a
     * wait's condition hold: the IDE's own work and the server's start and compiles, which the probe says, and the
     * requests of the step that waits, which the server's trace has; not the server's own traffic, such as LSP4IJ's
     * pulls of the workspace's diagnostics, which goes on however long the condition does not come. The probe is asked
     * beside a call that may hold the IDE's EDT or its driver, which it needs too: an IDE that does not say counts toward
     * the idle window, as one that does nothing would.
     */
    fun watch(
        probe: TourProbe,
        run: Path,
        report: (String) -> Unit,
    ) {
        val trace = ServerTrace(run.resolve("server-trace"))
        activity = {
            asked { JsonParser.parseString(probe.work()).asJsonObject }?.let { work ->
                val live = work["live"].asString
                Activity(
                    listOf(live, work["settling"].asString, trace.inHand()).filter(String::isNotEmpty).joinToString("; "),
                    live.isNotEmpty(),
                )
            } ?: Activity(NOT_ANSWERING)
        }
        state = { asked { "${probe.activity().ifEmpty { "idle" }}; ${probe.serverState()}" } ?: "the IDE not answering" }
        progress = { "${trace.moved()} trace events of the steps' work" }
        traffic = trace::traffic
        begun = trace::begin
        this.report = report
        watching = true
    }
}

/**
 * What is in progress that could make a wait's condition hold, as [text] says it, empty when nothing is, or
 * [NOT_ANSWERING]; [live] when the IDE runs a task, process or debug session of its own among it, which moves where a
 * wait cannot always see it.
 */
internal data class Activity(
    val text: String,
    val live: Boolean = false,
) {
    /** This, with the project's startup activities in progress beside it, unless the IDE does not answer. */
    fun starting(): Activity =
        when (text) {
            NOT_ANSWERING -> this
            else -> copy(text = listOf(STARTUP, text).filter(String::isNotEmpty).joinToString("; "))
        }
}

/**
 * How far the IDE's start is, as [TourProbe.starting] says: [opening], what it does before the project's window shows,
 * empty once the window shows; and [activities], whether the project's startup activities still run.
 */
internal data class Start(
    val opening: String = "",
    val activities: Boolean = false,
) {
    companion object {
        /** The start as [TourProbe.starting] answers it, a JSON object. */
        fun of(json: String): Start =
            JsonParser.parseString(json).asJsonObject.let { start ->
                Start(start["opening"]?.asString.orEmpty(), start["activities"]?.asBoolean == true)
            }
    }
}

/** How a wait names the project's startup activities while they run. */
private const val STARTUP = "the project's startup activities"

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
 * IntelliJ's first Undo after the caret has left the change only takes the caret back to it. Undo asks first whether to
 * take back a change of the project, such as a rename that changed several files, as it asks for IntelliJ's own
 * refactorings, and not a change of one file, such as a quick fix's: [asks] says which this is. The question is a
 * dialog titled Undo, with OK and Cancel, and OK answers it.
 */
internal fun TourProbe.undo(
    asks: Boolean,
    undone: () -> Boolean,
) {
    val notes = undoKey(undone)
    if (asks) {
        expect(dialogTitled("Undo")) { "Undo did not ask first" }
        expect(confirm("OK")) { "Undo's question did not take its answer: ${popupText()}" }
    } else {
        expect(!dialogTitled("Undo")) { "Undo asked first: ${popupText()}" }
    }
    expect(within(HANG) { undone() }) { "Undo did not undo the change; ${notes()}" }
}

/**
 * Presses the keymap's Undo shortcut in the selected editor, as [undo] does, and fails unless Undo has then [undone] the
 * change or asks whether to: a step that shows the question presses it so, and answers it in the next. Answers what a
 * later failure's message says of the undo. While the question is open, its modal dialog holds every probe call that
 * waits for the EDT outside it, as reading a document does, so the question is looked for first.
 */
internal fun TourProbe.undoKey(undone: () -> Boolean): () -> String {
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
        val asked = within(HANG) { dialogTitled("Undo") || undone() } && dialogTitled("Undo")
        if (asked) confirm("OK")
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
                // a redo of a change of the project asks first too, in a dialog titled Redo
                if (within(HANG) { dialogTitled("Redo") || !undone() } && dialogTitled("Redo")) confirm("OK")
                if (within(HANG) { !undone() }) undid { undoByAction() } else "not tried, as the redo did not redo it"
            }
        "$direct; then, redone, the Undo action performed with the editor's context: $byAction"
    }
    expect(within(HANG) { dialogTitled("Undo") || undone() }) {
        "Undo neither undid the change nor asked whether to; ${notes()}; UndoManager's undo of the selected editor, " +
            "asked directly then: ${directly()}"
    }
    return notes
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
 * Waits until the selected editor, of the file at [path], has its code lenses in place, and returns them as
 * [TourProbe.lensIdentities] names them: some, or any number where [some] is false, as for a file that an edit left
 * with an error, whose lenses go; none pending, nothing else in progress, and, after an edit, others than [before],
 * those it had before it; fails the step otherwise. A step that opens a popup presses once they are: lenses
 * that arrive above the caret move the editor, and IntelliJ closes an editor's popup when its visible area moves.
 * IntelliJ's code vision has an editor's lenses pending from a change or a refresh until they are in place again, also
 * while the editor does not show; it computes them anew as the editor shows.
 */
internal fun TourProbe.lensesInPlace(
    path: String,
    before: String? = null,
    some: Boolean = true,
): String {
    val inPlace =
        within(HANG) {
            lensIdentities().let { (!some || it.isNotEmpty()) && it != before } && !lensesPending() && activity().isEmpty()
        }
    expect(inPlace) {
        "The code lenses of ${Path.of(path).fileName} did not come into place: ${before?.let { "before the edit $it, " }.orEmpty()}" +
            "now ${lensIdentities().ifEmpty { "none" }}, pending ${lensesPending()}, in progress ${activity().ifEmpty { "nothing" }}"
    }
    return lensIdentities()
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
        "Errors in ${Path.of(path).fileName}: ${diagnostics(path)}; LSP4IJ holds: ${heldDiagnostics(path)}"
    }
}

/** Fails unless, within [timeout], the editor of the file at [path] marks an error whose message contains [text]. */
internal fun TourProbe.hasError(
    path: String,
    text: String = "",
    timeout: Duration = HANG,
) {
    expect(within(timeout) { diagnostics(path).lines().any { it.startsWith("ERROR") && text in it } }) {
        "No error${if (text.isEmpty()) "" else " with $text"} in ${Path.of(path).fileName}: ${diagnostics(path)}; " +
            "LSP4IJ holds: ${heldDiagnostics(path)}"
    }
}

/** Runs the tests of the gutter icon [tooltip] on [line] with its menu's Run, watched, and answers the run before it. */
internal fun TourProbe.runTests(
    line: Int,
    tooltip: String,
): String {
    gutter(line, tooltip)
    expect(within(HANG) { "Run" in rows() }) { "No test choices: ${rows()}" }
    watchTests()
    val before = runId()
    check(choose("Run")) { "No Run: ${rows()}" }
    return before
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

/** A language server's trace file, `lsp-trace-<pid>-….jsonl`, with the process id of the server that wrote it. */
private val TRACE_FILE = Regex("lsp-trace-(\\d+)-.*\\.jsonl")

/** LSP's RequestCancelled: the error a request ends with when the client cancels it. */
private const val REQUEST_CANCELLED = -32800
