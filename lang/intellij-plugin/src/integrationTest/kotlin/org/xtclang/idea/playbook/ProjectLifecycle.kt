package org.xtclang.idea.playbook

import com.google.gson.Gson
import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.singleProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/** Two real frames share the IDE while their compiler processes and pending replies stay isolated. */
internal fun Driver.projectLifecycle(shared: SharedScenarios) {
    val primary = singleProject()
    val root = Path.of(primary.getBasePath())
    val directory = Files.createDirectories(root.parent.resolve("second-project"))
    val data = shared.scenarios.getValue("X145")
    val module = data.text("module")
    val name = data.text("file")
    val firstFile = root.resolve(name)
    val secondFile = directory.resolve(name)
    val initial = data.text("source")
    listOf(firstFile, secondFile).forEach { Files.writeString(it, initial) }
    val first = ClientProtocol(this) { primary }
    val probe = utility(ProjectLifecycle::class)

    fun configure(
        project: Project,
        file: Path,
    ) = withContext(OnDispatcher.EDT) {
        val settings = new(LspServerSettings::class)
        settings.setConfigurationContent(
            Gson().toJson(
                mapOf(
                    "xtc" to
                        mapOf(
                            "compiler" to
                                mapOf(
                                    "sourceModules" to listOf(mapOf("name" to module, "uri" to file.toUri().toString())),
                                ),
                        ),
                ),
            ),
        )
        service<ProjectLspSettings>(project).updateSettings("xtcLanguageServer", settings)
    }

    fun edit(
        project: Project,
        file: Path,
        text: String,
    ) = withContext(OnDispatcher.EDT) {
        probe.edit(project, file.toString(), text)
    }

    fun params(file: Path) =
        mapOf(
            "textDocument" to mapOf("uri" to file.toUri().toString()),
            "position" to
                mapOf(
                    "line" to 1,
                    "character" to
                        probe
                            .text(file.toString())
                            .lineSequence()
                            .elementAt(1)
                            .indexOf("value"),
                ),
        )

    fun hover(
        client: ClientProtocol,
        file: Path,
        type: String,
    ) {
        awaitUi("$type hover in ${file.parent.fileName}", 45.seconds) {
            runCatching { client.query("textDocument/hover", params(file)).toString().contains("$type value") }.getOrDefault(false)
        }
    }

    fun openSecond(): Project {
        val opened = probe.open(directory.toString())
        awaitUi("second project opens", 60.seconds) { opened.isDone() }
        val project = requireNotNull(opened.get())
        awaitUi("both native project frames are visible", 30.seconds) {
            withContext(OnDispatcher.EDT) { probe.visible(primary) && probe.visible(project) }
        }
        return project
    }

    fun close(
        project: Project,
        pid: Long,
    ) {
        val closed = probe.close(project)
        awaitUi("project closes and owns server retirement", 30.seconds) {
            closed.isDone() && !utility(ProgressUi::class).alive(pid)
        }
        check(closed.get())
    }

    fun workload(
        type: String,
        value: String,
    ) = "module $module {\n    static $type value = $value;\n" +
        (0 until data.values["methods"].asInt).joinToString("\n") { "    $type read$it() { return value; }" } + "\n}\n"

    configure(primary, firstFile)
    val firstText = "module $module {\n    static Int value = 7;\n}\n"
    edit(primary, firstFile, firstText)
    hover(first, firstFile, "Int")
    val firstPid = requireNotNull(first.server().getCurrentProcessId())
    val secondary = openSecond()
    configure(secondary, secondFile)
    edit(secondary, secondFile, "module $module {\n    static String value = \"second\";\n}\n")
    val second = ClientProtocol(this) { secondary }
    hover(second, secondFile, "String")
    val secondPid = requireNotNull(second.server().getCurrentProcessId())
    check(firstPid != secondPid)
    val firstPendingText = workload("Int", "7") + "// unsaved primary\n"
    val secondPendingText = workload("String", "\"second\"") + "// pending close\n"
    edit(primary, firstFile, firstPendingText)
    val firstPending = first.request("textDocument/references", params(firstFile) + mapOf("context" to mapOf("includeDeclaration" to true)))
    edit(secondary, secondFile, secondPendingText)
    val secondPending =
        second.request(
            "textDocument/references",
            params(secondFile) + mapOf("context" to mapOf("includeDeclaration" to true)),
        )
    awaitUi("compiler is working when its project closes", 15.seconds) {
        check(!secondPending.isDone()) { "Close must overlap a pending native request" }
        second
            .query("xtc/languageServiceStatus", emptyMap<String, Any>())
            .asJsonObject["compilerQueue"]
            .asJsonObject["runningSize"]
            .asInt > 0
    }
    close(secondary, secondPid)
    awaitUi("closed project's pending reply retires", 15.seconds) { secondPending.isDone() }
    check(first.await("textDocument/references", firstPending).asJsonArray.size() == data.values["methods"].asInt + 1)
    check(first.server().getCurrentProcessId() == firstPid)
    check(probe.text(firstFile.toString()) == firstPendingText)
    hover(first, firstFile, "Int")
    check(Files.readString(secondFile) == secondPendingText) { "Native project close must preserve edited source" }
    val reopened = openSecond()
    withContext(OnDispatcher.EDT) { probe.show(reopened, secondFile.toString()) }
    check(probe.text(secondFile.toString()) == secondPendingText) { "Reopening must restore source without replaying edits" }
    val reconnected = ClientProtocol(this) { reopened }
    hover(reconnected, secondFile, "String")
    val reopenedPid = requireNotNull(reconnected.server().getCurrentProcessId())
    check(reopenedPid != secondPid && reopenedPid != firstPid)
    close(reopened, reopenedPid)
    hover(first, firstFile, "Int")
    Files.writeString(
        root.parent.resolve("project-lifecycle.json"),
        Gson().toJson(
            mapOf(
                "primaryPid" to firstPid,
                "closedPid" to secondPid,
                "reopenedPid" to reopenedPid,
                "closedPendingRetired" to secondPending.isDone(),
                "primaryTextPreserved" to true,
                "reopenedTextPreserved" to true,
                "status" to "passed",
            ),
        ) + "\n",
    )
}

@Remote("org.xtclang.idea.playbook.probe.ProjectLifecycle", plugin = "org.xtclang.playbook.probe")
internal interface ProjectLifecycle {
    fun open(path: String): OpenProjectFuture

    fun close(project: Project): CloseProjectFuture

    fun show(
        project: Project,
        path: String,
    )

    fun edit(
        project: Project,
        path: String,
        text: String,
    )

    fun text(path: String): String

    fun visible(project: Project): Boolean
}

@Remote("java.util.concurrent.CompletableFuture")
internal interface OpenProjectFuture : ClientFuture {
    fun get(): Project?
}

@Remote("java.util.concurrent.CompletableFuture")
internal interface CloseProjectFuture : ClientFuture {
    fun get(): Boolean
}
