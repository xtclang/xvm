package org.xtclang.idea.playbook

import com.google.gson.JsonObject
import com.intellij.driver.client.service
import com.intellij.driver.model.LockSemantics
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.PsiManager
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.singleProject
import kotlin.time.Duration.Companion.seconds

internal fun ParityScenarios.semanticCases() {
    case("X39") { data ->
        val doc = open(data.string("file"))
        val leaf = calls(doc, doc.at(data.string("callee"), data.int("offset"))).single()
        val incoming = callEdges(leaf, "incomingCalls")
        check(incoming.size == data.int("edgeCount"))
        check(
            incoming
                .single {
                    it.getAsJsonObject("from").string("name").contains(data.string("callerName"))
                }["fromRanges"]
                .asJsonArray
                .size() == data.int("edgeCount"),
        )
        check(
            incoming
                .single {
                    it.getAsJsonObject("from").string("name").contains(data.string("lambdaName"))
                }["fromRanges"]
                .asJsonArray
                .size() == data.int("lambdaSites"),
        )
        val run = calls(doc, doc.at(data.string("caller"), data.int("offset"))).single()
        val outgoing = callEdges(run, "outgoingCalls")
        check(outgoing.size == data.int("edgeCount"))
        check(
            outgoing
                .map {
                    it
                        .getAsJsonObject("to")["selectionRange"]
                        .asJsonObject["start"]
                        .asJsonObject
                        .int("line")
                }.toSet()
                .size == data.int("edgeCount"),
        )
        check(
            outgoing.map { it["fromRanges"].asJsonArray.size() }.sorted() ==
                data["sitesPerCallee"].asJsonArray.map { it.asInt },
        )
        with(driver) {
            nativeHierarchy(
                doc,
                doc.at(data.string("callee"), data.int("offset")),
                "call",
                listOf(data.string("callerName"), data.string("lambdaName")),
            )
        }
    }
    case("X40") { data ->
        val doc = open(data.string("file"))
        val leaf = calls(doc, doc.at(data.string("callee"), data.int("offset"))).single()
        val lambda =
            callEdges(
                leaf,
                "incomingCalls",
            ).single {
                it.getAsJsonObject("from").string("name").contains(data.string("lambdaName"))
            }
        val outgoing = callEdges(lambda.getAsJsonObject("from"), "outgoingCalls")
        check(outgoing.size == data.int("edgeCount"))
        check(outgoing.single().getAsJsonObject("to")["selectionRange"] == leaf["selectionRange"])
        check(
            outgoing
                .single()["fromRanges"]
                .rows()
                .single()
                .getAsJsonObject("start")
                .int("line") ==
                ParityWorkspace.position(doc.text, doc.at(data.string("lambdaCall")))["line"],
        )
        check(calls(doc, doc.at(data.string("dynamicCall"))).isEmpty())
    }
    listOf("X41", "X154").forEach { id ->
    case(id) { data ->
        data["source"]?.asString?.let { write(data.string("file"), it) }
        val doc = open(data.string("file"))
        clean(doc)
        val legend =
            protocol
                .capabilities()
                .asJsonObject["semanticTokensProvider"]
                .asJsonObject["legend"]
                .asJsonObject
        val support = with(driver) { semanticSupport(doc.editor).getSemanticTokensSupport() }
        val tokens =
            with(driver) {
                awaitUi(
                    "native editor receives semantic token classifications",
                    45.seconds,
                    getter = {
                        support
                            .getValidLSPFuture()
                            ?.takeIf { it.isDone() && !it.isCompletedExceptionally() }
                            ?.get()
                            ?.let {
                                protocol
                                    .copy(it.getSemanticTokens())
                                    .asJsonObject["data"]
                                    .asJsonArray
                                    .map { value -> value.asInt }
                            }
                    },
                    checker = { it != null },
                )
            }!!

        data class Token(
            val line: Int,
            val character: Int,
            val text: String,
            val type: String,
            val modifiers: Set<String>,
        )
        val decoded =
            tokens.chunked(5).fold(emptyList<Token>()) { previous, tuple ->
                val line = (previous.lastOrNull()?.line ?: 0) + tuple[0]
                val column =
                    (if (tuple[0] == 0) previous.lastOrNull()?.character ?: 0 else 0) + tuple[1]
                previous +
                    Token(
                        line,
                        column,
                        doc.text.lines()[line].substring(column, column + tuple[2]),
                        legend["tokenTypes"].asJsonArray[tuple[3]].asString,
                        legend["tokenModifiers"]
                            .asJsonArray
                            .mapIndexedNotNull { bit, name ->
                                name.asString.takeIf { tuple[4] and (1 shl bit) != 0 }
                            }.toSet(),
                    )
            }
        data["tokenKinds"].rows().forEach { kind ->
            check(decoded.any { it.text == kind.string("name") && it.type == kind.string("type") })
        }
        check(
            decoded.any {
                it.text == data.string("methodName") &&
                    it.modifiers.containsAll(
                        listOf(data.string("staticModifier"), data.string("declarationModifier")),
                    )
            },
        )
        data["accesses"]?.rows()?.forEach { access ->
            val at = ParityWorkspace.position(doc.text, doc.at(access.string("anchor"), access.int("offset")))
            val token = decoded.single { it.line == at["line"] && it.character == at["character"] }
            check(("modification" in token.modifiers) == access["write"].asBoolean) { "Wrong access classification: $access; $token" }
        }
        val write = doc.at(data.string("anchor2"))
        check(
            decoded.any {
                it.line == ParityWorkspace.position(doc.text, write)["line"] &&
                    it.text == data.string("variableName") &&
                    data.string("writeModifier") in it.modifiers
            },
        )
        with(driver) {
            focusEditor(doc.editor)
            withContext(OnDispatcher.EDT) {
                doc.editor.editor
                    .getCaretModel()
                    .moveToOffset(write)
            }
            invokeAction("HighlightUsagesInFile", component = doc.editor.component)
            val highlights =
                withContext(OnDispatcher.EDT, semantics = LockSemantics.READ_ACTION) {
                    val psi =
                        requireNotNull(
                            service<PsiManager>(singleProject())
                                .findFile(doc.editor.editor.getVirtualFile()),
                        )
                    utility(LspFileSupport::class).getSupport(psi).getHighlightSupport()
                }
            awaitUi("native read/write occurrence highlights", 45.seconds) {
                val future = highlights.getValidLSPFuture()
                future != null &&
                    future.isDone() &&
                    !future.isCompletedExceptionally() &&
                    future.get().let { values ->
                        fun contains(
                            at: Int,
                            kind: String,
                        ) = values.any { item ->
                            val p = ParityWorkspace.position(doc.text, at)
                            item.getRange().getStart().let {
                                it.getLine() == p["line"] && it.getCharacter() == p["character"]
                            } && item.getKind()?.name() == kind
                        }
                        contains(write, "Write") &&
                            contains(doc.at(data.string("anchor"), data.int("offset")), "Read")
                    }
            }
            invokeAction("EditorEscape", component = doc.editor.component)
        }
    }
    }
    case("X42") { data ->
        val doc = open(data.string("file"))

        fun verify(expected: JsonObject) {
            clean(doc)
            val support = with(driver) { semanticSupport(doc.editor).getInlayHintsSupport() }
            val hints =
                with(driver) {
                    awaitUi(
                        "native inlay provider has inferred types and parameter labels",
                        45.seconds,
                        getter = {
                            support
                                .getValidLSPFuture()
                                ?.takeIf {
                                    it.isDone() && !it.isCompletedExceptionally()
                                }?.get()
                                ?.map { protocol.copy(it.inlayHint()).asJsonObject }
                        },
                        checker = { it != null && it.isNotEmpty() },
                    )
                }!!
            val labels =
                hints.map {
                    it["label"].let { label ->
                        if (label.isJsonPrimitive) {
                            label.asString
                        } else {
                            label.rows().joinToString("") { part -> part.string("value") }
                        }
                    }
                }
            expected.strings("hints").forEach { expected ->
                check(labels.any { it.contains(expected) })
            }
            check(
                hints.none {
                    it.getAsJsonObject("position").int("line") ==
                        ParityWorkspace.position(doc.text, doc.at(expected.string("anchor")))[
                            "line",
                        ]
                },
            )
            check(hints.count { it.int("kind") == 1 } == expected.int("typeHintCount"))
            check(labels.none { it.contains(expected.string("excludedHint")) })
            val offsets =
                hints
                    .map { ParityWorkspace.offset(doc.text, it.getAsJsonObject("position")) }
                    .toSet()
            with(driver) {
                awaitUi("inlay labels are installed in the editor", 45.seconds) {
                    withContext(OnDispatcher.EDT) {
                        cast(doc.editor.editor, NativeInlayEditor::class)
                            .getInlayModel()
                            .getInlineElementsInRange(0, doc.text.length)
                            .filter { it.isValid() && it.getWidthInPixels() > 0 }
                            .map { it.getOffset() }
                            .containsAll(offsets)
                    }
                }
            }
        }
        verify(data)
        val inferred = data.getAsJsonObject("inferred")
        replace(doc, inferred.string("source"))
        verify(inferred)
    }
    case("X43") { data ->
        val doc = open(data.string("file"))

        fun current() = calls(doc, doc.at(data.string("anchor"), data.int("offset"))).single()
        val old = current()
        replace(
            doc,
            fixture(doc.file)
                .replace(data.string("replaceFrom"), data.string("shiftedDeclaration")),
        )
        check(callEdges(old, "outgoingCalls").isEmpty())
        val fresh = current()
        check(
            fresh["selectionRange"].asJsonObject["start"].asJsonObject.int("line") ==
                old["selectionRange"].asJsonObject["start"].asJsonObject.int("line") +
                data.int("lineShift"),
        )
        check(callEdges(fresh, "outgoingCalls").size == data.int("edgeCount"))
        replace(doc, doc.text.replace(data.string("anchor"), data.string("replaceWith")))
        check(callEdges(fresh, "outgoingCalls").isEmpty())
        replace(doc, fixture(doc.file))
        check(callEdges(current(), "outgoingCalls").size == data.int("edgeCount"))
        discard(doc)
        val reopened = open(doc.file)
        check(
            callEdges(
                calls(reopened, reopened.at(data.string("anchor"), data.int("offset")))
                    .single(),
                "outgoingCalls",
            ).size == data.int("edgeCount"),
        )
    }
    case("X44") { data ->
        val memberText =
            fixture(data.string("memberFile"))
                .replace(data.string("memberDeclaration"), data.string("memberWithCall"))
        write(data.string("memberFile"), memberText)
        val root =
            open(
                data.string("rootFile"),
                fixture(data.string("rootFile"))
                    .replace(data.string("replaceFrom"), data.string("replaceWith")),
            )
        clean(root)

        fun current() = calls(root, root.at(data.string("anchor"), data.int("offset"))).single()
        val original = current()
        val edge = callEdges(original, "incomingCalls").single()
        check(edge.getAsJsonObject("from").string("uri") == uri(data.string("memberFile")))
        val child = open(data.string("memberFile"))
        replace(child, "\n\n$memberText")
        settle(root)
        check(callEdges(original, "incomingCalls").isEmpty())
        val moved = callEdges(current(), "incomingCalls").single()

        fun line(item: JsonObject) =
            item["fromRanges"]
                .rows()
                .first()
                .getAsJsonObject("start")
                .int("line")
        check(line(moved) == line(edge) + data.int("lineShift"))
    }
}

private fun ParityWorkspace.calls(
    document: ParityWorkspace.Document,
    at: Int,
) = query("textDocument/prepareCallHierarchy", document, at).rows()

private fun ParityWorkspace.callEdges(
    item: JsonObject,
    direction: String,
) = protocol.query("callHierarchy/$direction", mapOf("item" to item)).rows()
