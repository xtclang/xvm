package org.xtclang.idea.playbook

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.LockSemantics
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.FileEditorManager
import com.intellij.driver.sdk.VirtualFile
import com.intellij.driver.sdk.getToolWindow
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.codeEditorForFile
import com.intellij.driver.sdk.ui.components.common.ideFrame
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/** Per-scenario native files/buffers and the same module boundaries as the VS Code playbook. */
class ParityWorkspace(
    val driver: Driver,
    val id: String,
    private val fixtures: Map<String, String>,
    private val shared: SharedScenarios,
) : AutoCloseable {
    val protocol = ClientProtocol(driver)
    val trace = ClientTrace(driver)
    val common =
        JsonParser
            .parseString(
                Files.readString(Path.of(System.getProperty("xtc.playbook.scenarios"))),
            ).asJsonObject["common"]
            .asJsonObject
    private val projectRoot = with(driver) { Path.of(singleProject().getBasePath()) }
    val directory: Path = projectRoot.resolve("parity/$id")

    inner class Document(
        val file: String,
        private val source: VirtualFile,
    ) {
        private val buffer =
            with(driver) {
                withContext(OnDispatcher.EDT, semantics = LockSemantics.READ_ACTION) {
                    requireNotNull(service<ParityDocuments>().getDocument(source))
                }
            }
        val text: String get() =
            with(driver) {
                withContext(OnDispatcher.EDT, semantics = LockSemantics.READ_ACTION) { buffer.getText() }
            }
        val uri: String get() = Path.of(source.getPath()).toUri().toString()
        val editor: JEditorUiComponent get() =
            with(driver) {
                withContext(OnDispatcher.EDT) {
                    val manager = service<FileEditorManager>(singleProject())
                    if (manager.getSelectedTextEditor()?.getVirtualFile()?.getPath() != source.getPath()) {
                        manager.openFile(source, false, false)
                    }
                }
                ideFrame().codeEditorForFile(Path.of(file).fileName.toString())
            }

        fun at(
            anchor: String,
            offset: Int = 0,
            last: Boolean = false,
        ): Int =
            (if (last) text.lastIndexOf(anchor) else text.indexOf(anchor)).also { check(it >= 0) { "Missing '$anchor' in $file" } } + offset

        fun params(at: Int? = null): Map<String, Any> =
            mapOf("textDocument" to mapOf("uri" to uri)) + if (at == null) emptyMap() else mapOf("position" to position(text, at))
    }

    init {
        Files.createDirectories(directory)
        // Existing cases use the common workspace. Close its tabs before same-named per-case files
        // are opened; otherwise editor lookup by tab name can bind to a different source buffer.
        with(driver) {
            dismissPopups()
            withContext(OnDispatcher.EDT) {
                val manager = service<FileEditorManager>(singleProject())
                manager
                    .getAllEditors()
                    .map { it.getFile() }
                    .distinctBy { it.getPath() }
                    .forEach(manager::closeFile)
            }
        }
        configure(emptyList())
        with(driver) {
            withContext(OnDispatcher.EDT) {
                // Instantiate LSP4IJ's trace console before traffic begins. A hidden, never
                // created tool window otherwise retains no console document to inspect.
                getToolWindow("Language Servers").show()
                getToolWindow("Language Servers").hide()
            }
        }
    }

    fun fixture(file: String): String = fixtures.getValue(file)

    fun uri(file: String): String = directory.resolve(file).toUri().toString()

    fun write(
        file: String,
        text: String = fixture(file),
    ) {
        val path = directory.resolve(file)
        Files.createDirectories(path.parent)
        Files.writeString(path, text)
        refresh(path)
    }

    fun delete(file: String) {
        val path = directory.resolve(file)
        Files.delete(path)
        refresh(path.parent)
    }

    private fun refresh(path: Path): VirtualFile? =
        with(driver) { utility(ParityFiles::class).getInstance().refreshAndFindFileByPath(path.toString()) }

    fun open(
        file: String,
        text: String? = null,
    ): Document =
        with(driver) {
            if (text != null || !Files.exists(directory.resolve(file))) write(file, text ?: fixture(file))
            val target = requireNotNull(refresh(directory.resolve(file)))
            withContext(OnDispatcher.EDT) { service<FileEditorManager>(singleProject()).openFile(target, false, false) }
            val document = Document(file, target)
            awaitUi("LSP4IJ opens $id/$file", 45.seconds) {
                clientDocument(document)?.getSynchronizer()?.getDidOpenFuture()?.let { it.isDone() && !it.isCompletedExceptionally() } ==
                    true
            }
            settle(document)
            document
        }

    private fun clientDocument(document: Document): ClientDocument? =
        protocol.server().getOpenedDocuments().singleOrNull { it.getFile().getPath() == Path.of(URI(document.uri)).toString() }

    fun replace(
        document: Document,
        text: String,
        settle: Boolean = true,
    ) {
        document.editor.text = text
        if (settle) settle(document)
    }

    fun settle(document: Document) {
        val pending = requireNotNull(clientDocument(document)).getSynchronizer().flushPendingChanges()
        with(driver) { awaitUi("client sends current $id/${document.file}", 45.seconds) { pending.isDone() } }
        check(!pending.isCompletedExceptionally()) { "Document synchronization failed" }
        query("textDocument/documentSymbol", document)
    }

    fun query(
        method: String,
        document: Document,
        at: Int? = null,
        extra: Map<String, Any> = emptyMap(),
    ): JsonElement = protocol.query(method, document.params(at) + extra)

    fun version(document: Document): Int {
        settle(document)
        return trace.version(document.uri, document.text)
    }

    fun published(
        file: String,
        matches: (List<JsonObject>) -> Boolean,
    ): List<JsonObject> = trace.diagnostics(uri(file), matches)

    fun targets(
        document: Document,
        kind: String,
        at: Int,
        native: Boolean = true,
    ): List<JsonObject> =
        query("textDocument/$kind", document, at).rows().also {
            if (native) with(driver) { nativeLocations(document, at, kind, it) }
        }

    fun names(locations: List<JsonObject>): List<String> =
        targetNames(locations) { uri ->
            protocol
                .server()
                .getOpenedDocuments()
                .firstOrNull { it.getFile().getPath() == Path.of(URI(uri)).toString() }
                ?.getSynchronizer()
                ?.getDocument()
                ?.getText()
        }

    fun hierarchy(
        document: Document,
        at: Int,
    ): JsonObject = query("textDocument/prepareTypeHierarchy", document, at).rows().single()

    fun edges(
        item: JsonObject,
        direction: String,
    ): List<JsonObject> = protocol.query("typeHierarchy/$direction", mapOf("item" to item)).rows()

    fun clean(document: Document) = diagnostics(document) { it.isEmpty() }

    fun errors(document: Document) = diagnostics(document) { it.isNotEmpty() }

    fun diagnostics(
        document: Document,
        matches: (List<ReceivedDiagnostic>) -> Boolean,
    ): List<ReceivedDiagnostic> =
        with(driver) {
            awaitUi(
                message = "current diagnostics for $id/${document.file}",
                timeout = 45.seconds,
                getter = { receivedDiagnostics(document.editor) },
                checker = matches,
            )
        }

    fun project(): Document {
        val setup = common["project"].asJsonObject
        write(setup.string("member"))
        return open(setup.string("root")).also(::clean)
    }

    fun configure(modules: List<SharedScenarios.SourceModule>) =
        with(driver) {
            val content = Gson().toJson(mapOf("xtc" to mapOf("compiler" to mapOf("sourceModules" to modules))))
            withContext(OnDispatcher.EDT) {
                val settings = new(LspServerSettings::class)
                settings.setConfigurationContent(content)
                service<LspSettings>().updateSettings("xtcLanguageServer", settings)
                val traceSettings =
                    new(LspServerSettings::class)
                        .setServerTrace(utility(ClientTraceLevel::class).valueOf("verbose"))
                service<ProjectLspSettings>(singleProject()).updateSettings("xtcLanguageServer", traceSettings)
            }
        }

    fun dependencies(): Document {
        shared.common.sourceModules.forEach { write(it.uri) }
        configure(shared.common.sourceModules.map { it.copy(uri = uri(it.uri)) })
        return open(shared.dependencyNavigation.file).also(::clean)
    }

    fun configure(modules: JsonElement) =
        configure(
            modules.asJsonArray.map {
                it.asJsonObject.let { module ->
                    SharedScenarios.SourceModule(module.string("name"), uri(module.string("uri")), module.strings("dependencies"))
                }
            },
        )

    fun linked(consumer: Document) {
        settle(consumer)
        clean(consumer)
        val location = shared.dependencyNavigation.location
        val result = targets(consumer, "definition", SharedScenarios.offset(consumer.text, location.cursor))
        check(result.single().string("uri") == uri(location.targetFile)) { result.toString() }
    }

    fun marked(
        file: String,
        text: String,
    ): Pair<Document, Int> {
        val at = text.indexOf('§')
        check(at >= 0 && text.lastIndexOf('§') == at)
        return open(file).also { replace(it, text.replace("§", "")) } to at
    }

    fun editing(body: String): Pair<Document, Int> {
        val setup = shared.common.editing
        return marked(setup.file, fixture(setup.file).replace(setup.run, setup.run.replace("{}", "{ $body }")))
    }

    fun virtual(
        file: String,
        text: String,
        version: Int,
        action: () -> Unit,
    ) {
        protocol.notify(
            "textDocument/didOpen",
            mapOf("textDocument" to mapOf("uri" to uri(file), "languageId" to "xtc", "version" to version, "text" to text)),
        )
        try {
            action()
        } finally {
            protocol.notify("textDocument/didClose", mapOf("textDocument" to mapOf("uri" to uri(file))))
        }
    }

    fun save(document: Document) =
        with(driver) {
            withContext(OnDispatcher.EDT, semantics = LockSemantics.WRITE_ACTION) {
                service<ParityDocuments>().saveDocument(cast(document.editor.document, ParityDocument::class))
            }
        }

    fun discard(document: Document) =
        with(driver) {
            // Revert via IntelliJ's file/document manager, preserving the disk line separator.
            withContext(OnDispatcher.EDT) {
                service<ParityDocuments>().reloadFromDisk(cast(document.editor.document, ParityDocument::class))
                service<FileEditorManager>(singleProject()).closeFile(document.editor.editor.getVirtualFile())
            }
            awaitUi("client closes ${document.file}", 30.seconds) { clientDocument(document) == null }
        }

    override fun close() {
        with(driver) {
            dismissPopups()
            val manager = service<FileEditorManager>(singleProject())
            val files =
                manager
                    .getAllEditors()
                    .map {
                        it.getFile()
                    }.filter { it.getPath().startsWith(directory.toString() + "/") }
                    .distinctBy { it.getPath() }
            withContext(OnDispatcher.EDT) { files.forEach(manager::closeFile) }
        }
        configure(shared.common.sourceModules.map { it.copy(uri = projectRoot.resolve(it.uri).toUri().toString()) })
    }

    companion object {
        fun position(
            text: String,
            offset: Int,
        ): Map<String, Int> {
            check(offset in 0..text.length)
            val prefix = text.take(offset)
            return mapOf("line" to prefix.count { it == '\n' }, "character" to offset - prefix.lastIndexOf('\n') - 1)
        }

        fun offset(
            text: String,
            position: JsonObject,
        ): Int = text.splitToSequence('\n').take(position.int("line")).sumOf { it.length + 1 } + position.int("character")
    }
}

/** Compare local URI spelling without changing objects that must round-trip to the server. */
internal fun JsonObject.string(name: String): String =
    get(name).asString.let { value ->
        if (name in setOf("uri", "targetUri", "oldUri", "newUri") && value.startsWith("file:")) {
            Path.of(URI(value)).toUri().toString()
        } else {
            value
        }
    }

internal fun JsonObject.int(name: String): Int = get(name).asInt

internal fun JsonObject.strings(name: String): List<String> = getAsJsonArray(name).map { it.asString }

internal fun JsonElement.rows(): List<JsonObject> = if (isJsonNull) emptyList() else asJsonArray.map { it.asJsonObject }

internal fun JsonObject.pattern(name: String): Regex =
    getAsJsonObject(name).let {
        Regex(it.string("source"), if (it.string("flags").contains('i')) setOf(RegexOption.IGNORE_CASE) else emptySet())
    }

@Remote("com.intellij.openapi.vfs.LocalFileSystem")
interface ParityFiles {
    fun getInstance(): ParityFiles

    fun refreshAndFindFileByPath(path: String): VirtualFile?
}

@Remote("com.intellij.openapi.fileEditor.FileDocumentManager")
interface ParityDocuments {
    fun getDocument(file: VirtualFile): ParityDocument?

    fun reloadFromDisk(document: ParityDocument)

    fun saveDocument(document: ParityDocument)
}

@Remote("com.intellij.openapi.editor.Document")
interface ParityDocument {
    fun getText(): String
}
