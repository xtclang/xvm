package org.xtclang.idea.playbook

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.singleProject
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

internal fun ParityScenarios.librarySettingsCases() {
    listOf("X266", "X267", "X268").forEach { id ->
        case(id) {
            val data = common["librarySettings"].asJsonObject
            val binaries =
                Path
                    .of(System.getProperty("xtc.playbook.scenarios"))
                    .parent.parent.parent
                    .resolve("lsp-server/build/generated/compiler-playbook/libraries")
            val one = binaries.resolve("one").toUri().toString()
            val two = binaries.resolve("two").toUri().toString()
            val sources = binaries.resolve("sources").toUri().toString()

            fun options(
                paths: List<String>?,
                attached: Boolean = false,
            ) = GsonBuilder().serializeNulls().create().toJson(
                mapOf(
                    "modulePath" to paths,
                    "sourceAttachments" to
                        if (attached) listOf(mapOf("module" to data.string("module"), "roots" to listOf(sources))) else emptyList(),
                ),
            )
            val document = open(data.string("consumerFile"), data.string("consumer"))
            if (id == "X268") {
                configure(listOf(SharedScenarios.SourceModule("LibraryConsumer", document.uri, emptyList())))
            }
            with(driver) {
                val page = utility(CompilerLibrariesPage::class)
                val project = singleProject()
                val original = withContext(OnDispatcher.EDT) { page.content(project) }

                fun edit(
                    paths: List<String>?,
                    action: String,
                    attached: Boolean = false,
                ) = withContext(OnDispatcher.EDT) { page.edit(project, options(paths, attached), action) }
                try {
                    if (id == "X266") {
                        edit(listOf(one, two), "cancel")
                        edit(listOf(one, two), "reset")
                        val result = requireNotNull(edit(listOf(one, two), "order"))
                        val paths =
                            JsonParser
                                .parseString(
                                    result,
                                ).asJsonObject["xtc"]
                                .asJsonObject["compiler"]
                                .asJsonObject["libraries"]
                                .asJsonObject["modulePath"]
                                .asJsonArray
                        check(paths.map { it.asString } == listOf(two, one))
                        clean(document)
                    } else {
                        edit(listOf(one), "apply", attached = true)
                        clean(document)

                        fun definition() {
                            val target = query("textDocument/definition", document, document.at(data.string("anchor"))).rows().single()
                            val path = Path.of(URI(target.string("uri")))
                            check(!Files.isWritable(path))
                            check(Files.readString(path) == data.string("source"))
                            check(target["range"].asJsonObject["start"].asJsonObject.int("line") == data.int("definitionLine"))
                            val library = open(path.toString())
                            check(library.text == data.string("source"))
                        }
                        definition()
                        if (id == "X267") {
                            val previous = protocol.server().getCurrentProcessId()
                            protocol.server().restart()
                            awaitUi("library settings survive server restart", 45.seconds) {
                                protocol.server().getCurrentProcessId()?.let {
                                    it !=
                                        previous
                                } ==
                                    true
                            }
                            clean(document)
                            definition()
                        } else {
                            edit(listOf(binaries.resolve("missing").toUri().toString()), "invalid")
                            edit(listOf(one, one), "invalid")
                            withContext(OnDispatcher.EDT) {
                                utility(
                                    CompilerSettingsPage::class,
                                ).resourceRootsRoundTrip(project, GsonBuilder().create().toJson(listOf(two, one)))
                            }
                            clean(document)
                            definition()
                        }
                        edit(emptyList(), "apply")
                        diagnostics(document) { it.isNotEmpty() }
                        edit(listOf(one), "apply", attached = true)
                        clean(document)
                    }
                } finally {
                    withContext(OnDispatcher.EDT) { page.restore(project, original) }
                }
            }
        }
    }
}

@Remote("org.xtclang.idea.playbook.probe.CompilerLibrariesPage", plugin = "org.xtclang.playbook.probe")
interface CompilerLibrariesPage {
    fun content(project: Project): String?

    fun edit(
        project: Project,
        json: String,
        action: String,
    ): String?

    fun restore(
        project: Project,
        content: String?,
    )
}
