package org.xtclang.idea.playbook

import com.google.gson.JsonElement
import com.intellij.driver.client.Remote
import com.intellij.driver.model.LockSemantics
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.VirtualFile
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import java.util.concurrent.CancellationException
import kotlin.time.Duration.Companion.seconds

internal fun ParityScenarios.renameCases() {
    case("X53") { data ->
        val document = open(data.string("file"))
        applyRename(document, document.at(data.string("anchor")), data.string("replaceWith"))
        check(
            document.text ==
                fixture(document.file)
                    .replace(data.pattern("replaceFrom"), data.string("replaceWith")),
        )
    }
    case("X54") { data ->
        val document = open(data.string("file"))
        data.strings("anchors").forEach { anchor ->
            replace(document, fixture(document.file))
            val offset =
                data.int(
                    if (anchor == data.string("declaration")) "callOffset" else "declarationOffset",
                )
            applyRename(document, document.at(anchor, offset), data.string("replaceWith"))
            check(
                document.text ==
                    fixture(document.file)
                        .replace(data.pattern("replaceFrom"), data.string("replaceWith")),
            )
            check(data.string("namedArgument") in document.text)
        }
    }
    case("X55") { data ->
        val document = open(data.string("file"))
        check(
            proposedRename(document, document.at(data.string("anchor")), data.string("newName"))
                .isJsonNull,
        )
        check(document.text == fixture(document.file))
        clean(document)
    }
    case("X56") { data ->
        val document = open(data.string("file"))
        data.strings("variants").forEach { anchor ->
            check(
                proposedRename(document, document.at(anchor), data.string("newName")).isJsonNull,
            ) {
                anchor
            }
        }
        replace(
            document,
            fixture(document.file).replace(data.string("replaceFrom"), data.string("replaceWith")),
        )
        check(
            proposedRename(
                document,
                document.at(data.string("anchor"), data.int("offset")),
                data.string("newName"),
            ).isJsonNull,
        )
    }
    case("X57") { data ->
        val document = open(data.string("file"))
        val transaction =
            renameTransaction(document, document.at(data.string("anchor")), data.string("newName"))
        val edit =
            protocol.copy(driver.cast(transaction.getEdit(), ClientValue::class)).asJsonObject
        // LSP4J's no-argument constructor restores an empty map when the wire field is absent.
        check(edit["changes"].let { it == null || it.isJsonNull || it.asJsonObject.size() == 0 }) {
            edit.toString()
        }
        val change = edit["documentChanges"].rows().single()
        val oldVersion = version(document)
        check(change["textDocument"].asJsonObject.int("version") == oldVersion)
        replace(document, "\n" + fixture(document.file))
        check(version(document) != oldVersion)
        // Apply through the same production transaction as the native Rename action.
        // Comparing versions only in the driver would not prove that IntelliJ rejects the edit.
        with(driver) {
            withContext(OnDispatcher.EDT) {
                check(!transaction.apply()) {
                    "Native rename accepted an obsolete document snapshot"
                }
            }
        }
        check(document.text == "\n" + fixture(document.file)) {
            "Native rename applied an edit for an obsolete document version"
        }
        replace(document, fixture(document.file))
        val retired =
            renameTransaction(document, document.at(data.string("anchor")), data.string("newName"))
        val closing =
            protocol.request(
                "textDocument/rename",
                document.params(document.at(data.string("anchor"))) +
                    mapOf("newName" to data.string("newName")),
            )
        discard(document)
        awaitRetired("textDocument/rename", closing)
        val reopened = open(document.file)
        check(reopened.text == fixture(document.file))
        with(driver) {
            withContext(OnDispatcher.EDT) {
                check(!retired.apply()) {
                    "Native rename accepted a closed/reopened document epoch"
                }
            }
        }
        check(reopened.text == fixture(document.file))
        applyRename(reopened, reopened.at(data.string("anchor")), data.string("newName"))
    }
    case("X58") { data ->
        val document = open(data.string("file"))
        replace(
            document,
            fixture(document.file).replace(data.string("replaceFrom"), data.string("replaceWith")),
        )
        errors(document)
        check(
            proposedRename(document, document.at(data.string("anchor")), data.string("newName"))
                .isJsonNull,
        )
        replace(document, fixture(document.file))
        clean(document)
        data.strings("invalidNames").forEach { name ->
            check(proposedRename(document, document.at(data.string("anchor")), name).isJsonNull) {
                name
            }
        }
        applyRename(document, document.at(data.string("anchor")), data.string("newName"))
    }
}

/** Retain the real production transaction so delayed application exercises its write guard. */
internal fun ParityWorkspace.renameTransaction(
    document: ParityWorkspace.Document,
    at: Int,
    name: String,
): NativeRenameTransaction {
    val server = protocol.server()
    return with(driver) {
        val pending =
            withContext(OnDispatcher.EDT, semantics = LockSemantics.READ_ACTION) {
                utility(NativeRenameRequests::class)
                    .request(server, document.editor.editor.getVirtualFile(), at, name)
            }
        awaitUi("native rename transaction", 60.seconds) { pending.isDone() }
        requireNotNull(cast(pending, NativeRenameFuture::class).get())
    }
}

internal fun ParityWorkspace.proposedRename(
    document: ParityWorkspace.Document,
    at: Int,
    name: String,
): JsonElement = query("textDocument/rename", document, at, mapOf("newName" to name))

internal fun ParityWorkspace.applyRename(
    document: ParityWorkspace.Document,
    at: Int,
    name: String,
) {
    val before = document.text
    with(driver) {
        rename(document.editor, at, name)
        awaitUi("native rename changes ${document.file}", 45.seconds) { document.text != before }
    }
    settle(document)
    clean(document)
}

internal fun ParityWorkspace.awaitRetired(
    method: String,
    future: ClientFuture,
) {
    try {
        protocol.await(method, future)
    } catch (_: CancellationException) {
        // Client cancellation is an allowed outcome for a deliberately retired request.
    } catch (error: ClientRequestFailure) {
        check(error.code == ResponseErrorCode.RequestCancelled.value || error.code == ResponseErrorCode.ContentModified.value) { error }
    }
}

@Remote("org.xtclang.idea.lsp.XtcRenameEdit", plugin = "org.xtclang.idea")
interface NativeRenameRequests {
    fun request(
        wrapper: StartedLanguageServer,
        file: VirtualFile,
        offset: Int,
        name: String,
    ): ClientFuture
}

@Remote("org.eclipse.lsp4j.WorkspaceEdit", plugin = "com.redhat.devtools.lsp4ij")
interface NativeWorkspaceEdit

@Remote("org.xtclang.idea.lsp.XtcRenameEdit", plugin = "org.xtclang.idea")
interface NativeRenameTransaction {
    fun getEdit(): NativeWorkspaceEdit

    fun apply(): Boolean
}

@Remote("java.util.concurrent.CompletableFuture")
interface NativeRenameFuture : ClientFuture {
    fun get(): NativeRenameTransaction?
}
