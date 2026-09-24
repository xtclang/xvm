package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams
import org.eclipse.lsp4j.CallHierarchyPrepareParams
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.FoldingRangeRequestParams
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.ImplementationParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.InlayHintParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ReferenceContext
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.SelectionRangeParams
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.TypeDefinitionParams
import org.eclipse.lsp4j.TypeHierarchyPrepareParams
import org.eclipse.lsp4j.TypeHierarchySubtypesParams
import org.eclipse.lsp4j.TypeHierarchySupertypesParams
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceClientCapabilities
import org.eclipse.lsp4j.WorkspaceEditCapabilities
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.TimeoutException
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/** Process tests consume the actual fat JAR, including its manifest, resources and logging setup. */
@Tag("compiler-stdio")
class XdkStdioTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `property implementations round trip fields and accessors through the packaged server`() {
        val source =
            "module Stdio { interface Named { @RO String name; } " +
                "class Stored implements Named { @Override String name=\"stored\"; } " +
                "class Computed implements Named { @Override String name.get()=\"computed\"; } " +
                "class Unrelated { String name=\"other\"; } Int size(String text)=text.size; }"
        Session(packagedJar(), directory).use { session ->
            session.initialize()
            session.open(source)
            assertThat(session.diagnosticsAt(1).diagnostics).isEmpty()
            val documents = session.server.textDocumentService
            val id = TextDocumentIdentifier(URI)

            fun lookup(at: Int) = session.await(documents.implementation(ImplementationParams(id, Position(0, at)))).left
            val targets = lookup(source.indexOf("name;"))
            assertThat(targets.map { it.uri }).containsOnly(URI)
            assertThat(targets.map { it.range })
                .containsExactlyInAnyOrder(
                    Range(Position(0, source.indexOf("name=")), Position(0, source.indexOf("name=") + 4)),
                    Range(Position(0, source.indexOf("get()")), Position(0, source.indexOf("get()") + 3)),
                )
            assertThat(lookup(source.lastIndexOf("size"))).isEmpty()
            session.change("module Stdio {", 2)
            assertThat(session.diagnosticsAt(2).diagnostics).isNotEmpty()
            assertThat(lookup(7)).isEmpty()
            session.change(source, 3)
            assertThat(session.diagnosticsAt(3).diagnostics).isEmpty()
            assertThat(lookup(source.indexOf("name;"))).isEqualTo(targets)
            session.shutdownAndExit()
        }
    }

    @Test
    fun `private parameter rename round trips versioned edits and rejects silent capture`() {
        val source = "module Stdio { private Int pick(Int input)=input; Int run()=pick(input=1); }"
        Session(packagedJar(), directory).use { session ->
            session.initialize(versionedEdits = true)
            session.open(source)
            assertThat(session.diagnosticsAt(1).diagnostics).isEmpty()
            val documents = session.server.textDocumentService
            val document = TextDocumentIdentifier(URI)
            val result =
                requireNotNull(session.await(documents.rename(RenameParams(document, Position(0, source.lastIndexOf("input")), "value"))))
            // LSP4J reconstructs its default empty map when the wire omits the changes member.
            assertThat(result.changes).isNullOrEmpty()
            val edit = result.documentChanges.single().left
            assertThat(edit.textDocument.uri).isEqualTo(URI)
            assertThat(edit.textDocument.version).isEqualTo(1)
            assertThat(edit.edits.map { it.left.newText }).containsExactly("value", "value", "value")
            val changed =
                edit.edits.map { it.left }.sortedByDescending { it.range.start.character }.fold(source) { text, change ->
                    text.replaceRange(change.range.start.character, change.range.end.character, change.newText)
                }
            session.change(changed, 2)
            assertThat(session.diagnosticsAt(2).diagnostics).isEmpty()
            val capture = "module Stdio { Int value=10; Int run() { Int local=1; return local+value; } }"
            session.change(capture, 3)
            assertThat(session.diagnosticsAt(3).diagnostics).isEmpty()
            assertThat(session.await(documents.rename(RenameParams(document, Position(0, capture.indexOf("local")), "value")))).isNull()
        }
    }

    @Test
    fun `compiler call hierarchy tokens and hints round trip and reject stale items`() {
        val source = "module Stdio { static Int leaf(Int input)=input; Int run() { var n=leaf(1); return n; } }"
        Session(packagedJar(), directory).use { session ->
            session.initialize()
            session.open(source)
            assertThat(session.diagnosticsAt(1).diagnostics).isEmpty()
            val documents = session.server.textDocumentService
            val document = TextDocumentIdentifier(URI)
            val leaf =
                session
                    .await(
                        documents.prepareCallHierarchy(CallHierarchyPrepareParams(document, Position(0, source.indexOf("leaf")))),
                    ).single()
            val incoming = session.await(documents.callHierarchyIncomingCalls(CallHierarchyIncomingCallsParams(leaf))).single()
            assertThat(incoming.from.name).isEqualTo("run")
            assertThat(incoming.fromRanges).containsExactly(
                Range(
                    Position(0, source.lastIndexOf("leaf")),
                    Position(
                        0,
                        source.lastIndexOf("leaf") + 4,
                    ),
                ),
            )
            val outgoing = session.await(documents.callHierarchyOutgoingCalls(CallHierarchyOutgoingCallsParams(incoming.from))).single()
            assertThat(outgoing.to).isEqualTo(leaf)
            val hints = session.await(documents.inlayHint(InlayHintParams(document, Range(Position(0, 0), Position(1, 0)))))
            assertThat(hints.map { it.label.left }).containsExactly(": Int", "input:")
            assertThat(session.await(documents.semanticTokensFull(SemanticTokensParams(document))).data).isNotEmpty()
            session.change("\n$source", 2)
            assertThat(session.diagnosticsAt(2).diagnostics).isEmpty()
            assertThat(session.await(documents.callHierarchyIncomingCalls(CallHierarchyIncomingCallsParams(leaf)))).isEmpty()
            assertThat(session.await(documents.callHierarchyOutgoingCalls(CallHierarchyOutgoingCallsParams(incoming.from)))).isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `bundled compiler handles edits and shutdown without an external XDK`(invalidXdkHome: Boolean) {
        Session(packagedJar(), directory, invalidXdkHome).use { session ->
            session.initialize()
            session.open(BROKEN)
            val errors = session.diagnosticsAt(1).diagnostics
            assertThat(errors).isNotEmpty()
            assertThat(errors.map { it.code.left }).doesNotContain("XDK-UNAVAILABLE", "ANALYSIS-FAILED", "EMB-5")
            assertThat(errors.any { it.message.left.contains("missing") }).isTrue()

            for (version in 2..101) {
                session.change(if (version == 101) VALID else BROKEN, version)
            }
            assertThat(session.diagnosticsAt(101).diagnostics).isEmpty()
            val symbols =
                session.await(
                    session.server.textDocumentService.documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(URI))),
                )
            assertThat(symbols).isNotEmpty()
            session.verifySemantics(VALID, "value", "Int")

            session.server.textDocumentService.didClose(DidCloseTextDocumentParams(TextDocumentIdentifier(URI)))
            assertThat(session.diagnosticsAt(101).diagnostics).isEmpty()
            session.open(REOPENED)
            assertThat(session.diagnosticsAt(1).diagnostics).isEmpty()
            session.verifySemantics(REOPENED, "label", "String")

            for (version in 2..20) session.change(BROKEN, version)
            session.shutdownAndExit()
        }
    }

    @Test
    fun `packaged module diagnostics cross file definitions and hierarchy round trip over stdio`() {
        directory = directory.toRealPath()
        val root = directory.resolve("Multi.x").toFile()
        val member = directory.resolve("Multi/Child.x").toFile()
        val source =
            "module Multi { interface Named { String name(); } " +
                "class Base implements Named { @Override String name() = \"base\"; } Child make() = new Child(); }"
        root.writeText(source)
        member.parentFile.mkdirs()
        member.writeText("class Child extends Base { @Override String name() = \"child\"; }")
        Session(packagedJar(), directory).use { session ->
            session.initialize()
            val rootId = TextDocumentIdentifier(root.toURI().toString())
            val memberId = TextDocumentIdentifier(member.toURI().toString())
            val documents = session.server.textDocumentService
            documents.didOpen(DidOpenTextDocumentParams(TextDocumentItem(rootId.uri, "xtc", 1, source)))
            assertThat(session.diagnosticsFor(memberId.uri, null).diagnostics).isEmpty()
            val definition =
                session
                    .await(
                        documents.definition(DefinitionParams(rootId, Position(0, source.indexOf("Child")))),
                    ).left
                    .single()
            assertThat(definition.uri).isEqualTo(memberId.uri)
            val typeDefinition =
                session.await(documents.typeDefinition(TypeDefinitionParams(rootId, Position(0, source.indexOf("make"))))).left.single()
            assertThat(typeDefinition.uri).isEqualTo(memberId.uri)
            assertThat(typeDefinition.range).isEqualTo(Range(Position(0, 6), Position(0, 11)))
            for (name in listOf("Named", "name")) {
                val implementations =
                    session.await(documents.implementation(ImplementationParams(rootId, Position(0, source.indexOf(name))))).left
                assertThat(implementations.map { it.uri }).containsExactlyInAnyOrder(rootId.uri, memberId.uri)
            }
            val base =
                session
                    .await(
                        documents.prepareTypeHierarchy(TypeHierarchyPrepareParams(rootId, Position(0, source.indexOf("Base")))),
                    ).single()
            val child = session.await(documents.typeHierarchySubtypes(TypeHierarchySubtypesParams(base))).single()
            assertThat(child.uri).isEqualTo(memberId.uri)
            assertThat(
                session.await(documents.typeHierarchySupertypes(TypeHierarchySupertypesParams(child))).single().uri,
            ).isEqualTo(rootId.uri)
            documents.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(memberId.uri, "xtc", 7, "class Child extends Base { MissingType absent; }")),
            )
            assertThat(session.diagnosticsFor(memberId.uri, 7).diagnostics).anyMatch { it.code.left == "COMPILER-38" }
            documents.didChange(
                DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier(memberId.uri, 8),
                    listOf(TextDocumentContentChangeEvent(member.readText())),
                ),
            )
            assertThat(session.diagnosticsFor(memberId.uri, 8).diagnostics).isEmpty()
            assertThat(session.await(documents.typeHierarchySubtypes(TypeHierarchySubtypesParams(base)))).isEmpty()
            session.shutdownAndExit()
        }
    }

    @Test
    fun `type definition preserves multiple union targets over stdio and follows narrowing`() {
        val source =
            """
            module Stdio {
                class First {}
                class Second {}
                void run(First|Second value) {
                    value.toString();
                    if (value.is(First)) { value.toString(); }
                }
            }
            """.trimIndent()
        Session(packagedJar(), directory).use { session ->
            session.initialize()
            session.open(source)
            assertThat(session.diagnosticsAt(1).diagnostics).isEmpty()
            val documents = session.server.textDocumentService
            val id = TextDocumentIdentifier(URI)
            val union = session.await(documents.typeDefinition(TypeDefinitionParams(id, Position(4, 8)))).left
            assertThat(union.map { it.range.start.line }).containsExactly(1, 2)
            assertThat(union.map { it.uri }).containsOnly(URI)
            val narrowedPosition = Position(5, source.lines()[5].lastIndexOf("value"))
            val narrowed = session.await(documents.typeDefinition(TypeDefinitionParams(id, narrowedPosition))).left
            assertThat(narrowed).containsExactly(union.first())
            session.shutdownAndExit()
        }
    }

    @Test
    fun `Java only compiler preserves structure through incomplete edits over stdio`() {
        Session(packagedJar(), directory).use { session ->
            session.initialize()
            session.open(VALID)
            assertThat(session.diagnosticsAt(1).diagnostics).isEmpty()
            val text = "module Stdio {\n    void editing() {\n        /* café 😀 */ console."
            session.change(text, 2)
            val errors = session.diagnosticsAt(2).diagnostics
            assertThat(errors).isNotEmpty()
            assertThat(errors.map { it.code.left }).doesNotContain("ANALYSIS-FAILED", "EMB-5")
            val document = TextDocumentIdentifier(URI)
            val service = session.server.textDocumentService
            val symbols = session.await(service.documentSymbol(DocumentSymbolParams(document)))
            assertThat(
                symbols
                    .single()
                    .right.children
                    .map { it.name },
            ).containsExactly("editing")
            val folds = session.await(service.foldingRange(FoldingRangeRequestParams(document)))
            assertThat(folds).anyMatch { it.startLine == 1 && it.endLine == 2 }
            val cursor = Position(2, text.lines().last().length)
            val selected = session.await(service.selectionRange(SelectionRangeParams(document, listOf(cursor)))).single()
            assertThat(selected.range.end).isEqualTo(cursor)
            assertThat(selected.range.start.line).isLessThan(2)
            assertThat(session.await(service.definition(DefinitionParams(document, Position(1, 10)))).left).isEmpty()
            session.change(REOPENED, 3)
            assertThat(session.diagnosticsAt(3).diagnostics).isEmpty()
            session.verifySemantics(REOPENED, "label", "String")
            session.shutdownAndExit()
        }
        assertThat(Files.readString(directory.resolve("stderr.log"))).doesNotContain("loadXtcLanguage", "TreeSitterAdapter")
    }

    @Test
    fun `compiler completion and signature requests round trip over stdio`() {
        val declarations =
            "module Stdio { class Box<Element> { Element echo(Element value) { return value; } " +
                "Int choose(Int n) { return n; } String choose(String text) { return text; } " +
                "String label=\"box\"; private Int secret() { return 1; } } "
        val prefix = "${declarations}void run(Box<String> box) { box."
        Session(packagedJar(), directory).use { session ->
            session.initialize()
            val service = session.server.textDocumentService
            val document = TextDocumentIdentifier(URI)
            session.open("$prefix } }")
            assertThat(session.diagnosticsAt(1).diagnostics).isNotEmpty()
            val items = session.await(service.completion(CompletionParams(document, Position(0, prefix.length)))).left
            assertThat(items.map { it.label }).contains("echo", "choose", "label").doesNotContain("secret")
            assertThat(items.single { it.label == "echo" }.detail).isEqualTo("String echo(String value)")
            assertThat(items.filter { it.label == "choose" }).hasSize(2)

            val call = "${prefix}echo("
            session.change("$call } }", 2)
            assertThat(session.diagnosticsAt(2).diagnostics).isNotEmpty()
            val help = session.await(service.signatureHelp(SignatureHelpParams(document, Position(0, call.length))))
            assertThat(help.signatures.single().label).isEqualTo("String echo(String value)")
            assertThat(help.signatures.single().activeParameter).isZero()
            assertThat(
                help.signatures
                    .single()
                    .documentation.left,
            ).contains("overload not selected")

            val overloaded = "${prefix}choose("
            session.change("$overloaded } }", 3)
            assertThat(session.diagnosticsAt(3).diagnostics).isNotEmpty()
            assertThat(
                session.await(service.signatureHelp(SignatureHelpParams(document, Position(0, overloaded.length)))).signatures,
            ).hasSize(2)

            val complete =
                "module Stdio { <T> T echo(T value, T backup) { return value; } " +
                    "void run() { String text=echo(backup=\"b\", value=\"a\"); } }"
            session.change(complete, 4)
            assertThat(session.diagnosticsAt(4).diagnostics).isEmpty()
            val selected =
                session.await(
                    service.signatureHelp(
                        SignatureHelpParams(
                            document,
                            Position(
                                0,
                                complete.indexOf("backup=\"b\"") + 10,
                            ),
                        ),
                    ),
                )
            assertThat(selected.signatures.single().label).isEqualTo("String echo(String value, String backup)")
            assertThat(selected.activeParameter).isEqualTo(1)
            assertThat(selected.signatures.single().activeParameter).isEqualTo(1)

            session.change("$prefix } }", 5)
            assertThat(session.diagnosticsAt(5).diagnostics).isNotEmpty()
            repeat(10) {
                service.completion(CompletionParams(document, Position(0, prefix.length))).cancel(false)
            }
            val latest = session.await(service.completion(CompletionParams(document, Position(0, prefix.length)))).left
            assertThat(latest.map { it.label }).contains("echo")
            session.shutdownAndExit()
        }
        assertThat(Files.readString(directory.resolve("stderr.log"))).doesNotContain("TreeSitterAdapter", "loadXtcLanguage")
    }

    @Test
    fun `typed completion edits and auto-closed signature help round trip over stdio`() {
        val prefix = "module Stdio {\r\n Int run(Object value) {\r\n  if (value.is(String)) { /* 😀 */ return value.si"
        val text = "$prefix; } return 0; }\r\n}"
        Session(packagedJar(), directory).use { session ->
            session.initialize()
            val service = session.server.textDocumentService
            val document = TextDocumentIdentifier(URI)
            session.open(text)
            assertThat(session.diagnosticsAt(1).diagnostics).isNotEmpty()
            val cursor = Position(2, prefix.lines().last().length)
            val items = session.await(service.completion(CompletionParams(document, cursor))).left
            assertThat(items.map { it.label }).contains("size")
            assertThat(items).allSatisfy { assertThat(it.label).startsWith("si") }
            val edit = items.single { it.label == "size" }.textEdit.left
            assertThat(edit.range).isEqualTo(Range(Position(2, cursor.character - 2), cursor))
            assertThat(edit.newText).isEqualTo("size")
            val changed = text.replaceRange(prefix.length - 2, prefix.length, edit.newText)
            session.change(changed, 2)
            assertThat(session.diagnosticsAt(2).diagnostics).isEmpty()

            val call = "module Stdio { void run(String value) { value.indexOf("
            session.change("$call); } Int later() = 42; }", 3)
            assertThat(session.diagnosticsAt(3).diagnostics).isNotEmpty()
            val help = session.await(service.signatureHelp(SignatureHelpParams(document, Position(0, call.length))))
            assertThat(help.signatures).hasSize(2).allSatisfy {
                assertThat(it.label).startsWith("conditional Int indexOf(")
                assertThat(it.activeParameter).isZero()
                assertThat(it.documentation.left).contains("overload not selected")
            }
            val completed = "$call\"x\""
            session.change("$completed); } Int later() = 42; }", 4)
            assertThat(session.diagnosticsAt(4).diagnostics).isEmpty()
            val selected = session.await(service.signatureHelp(SignatureHelpParams(document, Position(0, completed.length))))
            assertThat(selected.signatures).hasSize(1)
            assertThat(selected.signatures.single().documentation).isNull()
            assertThat(selected.signatures.single().activeParameter).isZero()
            session.shutdownAndExit()
        }
    }

    @Test
    fun `missing enclosing delimiters preserve cursor queries and ordinary diagnostics over stdio`() {
        val header = "module Stdio { Int pair(Int first, Int second) = first; Int run(String value, Int[] values) { return "
        Session(packagedJar(), directory).use { session ->
            session.initialize()
            val service = session.server.textDocumentService
            val document = TextDocumentIdentifier(URI)
            val prefix = "$header(values[value.si"
            session.open("$prefix; } }")
            val diagnostics = session.diagnosticsAt(1).diagnostics
            assertThat(diagnostics).isNotEmpty()
            val cursor = Position(0, prefix.length)
            val items = session.await(service.completion(CompletionParams(document, cursor))).left
            val edit = items.single { it.label == "size" }.textEdit.left
            assertThat(edit.range).isEqualTo(Range(Position(0, prefix.length - 2), cursor))

            val call = "$header(values[pair(1, "
            session.change("$call; } }", 2)
            assertThat(session.diagnosticsAt(2).diagnostics).isNotEmpty()
            val help = session.await(service.signatureHelp(SignatureHelpParams(document, Position(0, call.length))))
            assertThat(help.signatures.single().label).isEqualTo("Int pair(Int first, Int second)")
            assertThat(help.signatures.single().activeParameter).isEqualTo(1)
            assertThat(
                help.signatures
                    .single()
                    .documentation.left,
            ).contains("overload not selected")
            session.change("${call}2)]); } }", 3)
            assertThat(session.diagnosticsAt(3).diagnostics).isEmpty()
            session.shutdownAndExit()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["take(first=1, second=", "fn(1, ", "new Box<String>("])
    fun `argument value edits round trip and clear diagnostics after insertion`(call: String) {
        val prefix =
            "module Stdio { void take(Int first, String second) {} class Box<T> { construct(T value) {} } " +
                "void run(Int number, String text, Boolean flag, function Int(Int, String) fn) { /* 😀 */ $call"
        Session(packagedJar(), directory).use { session ->
            session.initialize()
            session.open("$prefix); } }")
            assertThat(session.diagnosticsAt(1).diagnostics).isNotEmpty()
            val service = session.server.textDocumentService
            val document = TextDocumentIdentifier(URI)
            val cursor = Position(0, prefix.length)
            val items = session.await(service.completion(CompletionParams(document, cursor))).left
            assertThat(items.map { it.label }).containsExactly("text")
            val edit = items.single().textEdit.left
            assertThat(edit.range).isEqualTo(Range(cursor, cursor))
            assertThat(edit.newText).isEqualTo("text")
            val help = session.await(service.signatureHelp(SignatureHelpParams(document, cursor)))
            assertThat(
                help.signatures
                    .single()
                    .documentation.left,
            ).contains(if (call.startsWith("fn(")) "runtime target unknown" else "overload not selected")
            session.change("$prefix${edit.newText}); } }", 2)
            assertThat(session.diagnosticsAt(2).diagnostics).isEmpty()
            session.shutdownAndExit()
        }
    }

    @Test
    fun `scope completion and inferred named signatures round trip over stdio`() {
        Session(packagedJar(), directory).use { session ->
            session.initialize()
            val service = session.server.textDocumentService
            val document = TextDocumentIdentifier(URI)
            val prefix = "module Stdio { Int run(Int item) { return ite"
            session.open("$prefix; } }")
            assertThat(session.diagnosticsAt(1).diagnostics).isNotEmpty()
            val items = session.await(service.completion(CompletionParams(document, Position(0, prefix.length)))).left
            val item = items.single { it.label == "item" }
            assertThat(item.detail).isEqualTo("Int item")
            assertThat(item.textEdit.left.range).isEqualTo(Range(Position(0, prefix.length - 3), Position(0, prefix.length)))
            session.change(prefix.dropLast(3) + item.textEdit.left.newText + "; } }", 2)
            assertThat(session.diagnosticsAt(2).diagnostics).isEmpty()

            val call =
                "module Stdio { <T> T pair(T first, T second) = first; " +
                    "void run() { pair(second=\"x\", first="
            session.change("$call); } }", 3)
            assertThat(session.diagnosticsAt(3).diagnostics).isNotEmpty()
            val help = session.await(service.signatureHelp(SignatureHelpParams(document, Position(0, call.length))))
            assertThat(help.signatures).hasSize(1)
            assertThat(help.signatures.single().label).isEqualTo("String pair(String first, String second)")
            assertThat(help.signatures.single().activeParameter).isZero()
            assertThat(
                help.signatures
                    .single()
                    .documentation.left,
            ).contains("overload not selected")
            session.change("$call\"y\"); } }", 4)
            assertThat(session.diagnosticsAt(4).diagnostics).isEmpty()
            session.shutdownAndExit()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["function", "constructor"])
    fun `incomplete function and constructor signatures round trip over stdio`(kind: String) {
        val prefix =
            if (kind == "function") {
                "module Stdio { void run(function Int(Int, String) fn) { fn(1, "
            } else {
                "module Stdio { class Box { construct(Int first, String second) {} } void run() { new Box(1, "
            }
        Session(packagedJar(), directory).use { session ->
            session.initialize()
            session.open("$prefix); } }")
            assertThat(session.diagnosticsAt(1).diagnostics).isNotEmpty()
            val help =
                session.await(
                    session.server.textDocumentService.signatureHelp(
                        SignatureHelpParams(TextDocumentIdentifier(URI), Position(0, prefix.length)),
                    ),
                )
            assertThat(help.signatures.single().label)
                .isEqualTo(if (kind == "function") "Int fn(Int, String)" else "new Box(Int first, String second)")
            assertThat(help.signatures.single().activeParameter).isEqualTo(1)
            session.change("$prefix\"x\"); } }", 2)
            assertThat(session.diagnosticsAt(2).diagnostics).isEmpty()
            session.shutdownAndExit()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "in"])
    fun `module overlays feed completion and root edits invalidate member requests over stdio`(memberPrefix: String) {
        directory = directory.toRealPath()
        val root = directory.resolve("Multi.x").toFile()
        val member = directory.resolve("Multi/Child.x").toFile()
        root.writeText("module Multi { class Base { Int value = 1; } }")
        member.parentFile.mkdirs()
        val prefix = "class Child extends Base { Int run() { return value.$memberPrefix"
        member.writeText("$prefix } }")
        Session(packagedJar(), directory).use { session ->
            session.initialize()
            val rootId = TextDocumentIdentifier(root.toURI().toString())
            val memberId = TextDocumentIdentifier(member.toURI().toString())
            val service = session.server.textDocumentService
            service.didOpen(
                DidOpenTextDocumentParams(
                    TextDocumentItem(rootId.uri, "xtc", 1, "module Multi { class Base { String value = \"overlay\"; } }"),
                ),
            )
            service.didOpen(DidOpenTextDocumentParams(TextDocumentItem(memberId.uri, "xtc", 1, member.readText())))
            val before = session.await(service.completion(CompletionParams(memberId, Position(0, prefix.length)))).left
            assertThat(before.map { it.label }).contains("indexOf")
            service.didChange(
                DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier(rootId.uri, 2),
                    listOf(TextDocumentContentChangeEvent(root.readText())),
                ),
            )
            val after = session.await(service.completion(CompletionParams(memberId, Position(0, prefix.length)))).left
            if (memberPrefix.isEmpty()) assertThat(after).isNotEmpty()
            assertThat(after.map { it.label }).doesNotContain("indexOf")
            assertThat(root.readText()).contains("Int value")
            session.shutdownAndExit()
        }
    }

    @Test
    fun `an invalid packaged backend setting fails startup explicitly`() {
        val invalid = directory.resolve("invalid-setting.jar")
        JarFile(packagedJar().toFile()).use { original ->
            JarOutputStream(Files.newOutputStream(invalid)).use { output ->
                for (entry in original.entries()) {
                    output.putNextEntry(JarEntry(entry.name))
                    original.getInputStream(entry).use { input ->
                        if (entry.name == "lsp-version.properties") {
                            Properties().apply {
                                load(input)
                                setProperty("lsp.adapter", "compielr")
                                store(output, null)
                            }
                        } else {
                            input.copyTo(output)
                        }
                    }
                    output.closeEntry()
                }
            }
        }
        Session(invalid, directory).use { it.expectExit(1) }
        assertThat(Files.readString(directory.resolve("stderr.log")))
            .contains("Unknown lsp.adapter 'compielr'; expected treesitter, compiler or mock")
    }

    @Test
    fun `a missing bundled bootstrap reports an analysis failure`() {
        val damaged = directory.resolve("damaged.jar")
        JarFile(packagedJar().toFile()).use { original ->
            assertThat(original.getJarEntry(BOOTSTRAP)).isNotNull()
            JarOutputStream(Files.newOutputStream(damaged)).use { output ->
                for (entry in original.entries()) {
                    if (entry.name == BOOTSTRAP) continue
                    output.putNextEntry(JarEntry(entry.name))
                    original.getInputStream(entry).use { it.copyTo(output) }
                    output.closeEntry()
                }
            }
        }
        Session(damaged, directory).use { session ->
            session.initialize()
            session.open(VALID)
            val errors = session.diagnosticsAt(1).diagnostics
            assertThat(errors.map { it.code.left }).containsExactly("ANALYSIS-FAILED")
            session.shutdownAndExit()
        }
        assertThat(Files.readString(directory.resolve("stderr.log"))).contains("Bundled XDK resource is missing: javatools_turtle.xtc")
    }

    @Test
    fun `exit without shutdown terminates with failure status`() {
        Session(packagedJar(), directory).use { session ->
            session.initialize()
            session.server.exit()
            session.expectExit(1)
        }
    }

    private fun packagedJar(): Path {
        val jar = Path.of(requireNotNull(System.getProperty("xtc.lsp.jar")) { "Run the compilerStdioTest Gradle task" })
        JarFile(jar.toFile()).use { archive ->
            val properties = Properties()
            archive.getInputStream(archive.getJarEntry("lsp-version.properties")).use { properties.load(it) }
            assertThat(properties.getProperty("lsp.adapter"))
                .describedAs("compilerStdioTest requires -Plsp.adapter=compiler")
                .isIn("compiler", "xtc", "full")
        }
        return jar
    }

    private class Session(
        jar: Path,
        directory: Path,
        invalidXdkHome: Boolean = false,
    ) : AutoCloseable {
        private val stderr = directory.resolve("stderr.log")
        private val published = LinkedBlockingQueue<PublishDiagnosticsParams>()
        private val executor = Executors.newVirtualThreadPerTaskExecutor()
        private val process =
            ProcessBuilder(
                ProcessHandle
                    .current()
                    .info()
                    .command()
                    .orElseThrow(),
                "-ea",
                "-Duser.home=$directory",
                "-jar",
                jar.toString(),
            ).apply {
                directory(directory.toFile())
                environment().remove("XDK_HOME")
                if (invalidXdkHome) environment()["XDK_HOME"] = directory.resolve("absent-xdk").toString()
                redirectError(stderr.toFile())
            }.start()
        private val client =
            object : LanguageClient {
                override fun publishDiagnostics(params: PublishDiagnosticsParams) {
                    published.add(params)
                }

                override fun telemetryEvent(value: Any?) = Unit

                override fun showMessage(params: MessageParams) = Unit

                override fun showMessageRequest(params: ShowMessageRequestParams): CompletableFuture<MessageActionItem> =
                    CompletableFuture.completedFuture(null)

                override fun logMessage(params: MessageParams) = Unit

                override fun configuration(params: ConfigurationParams): CompletableFuture<List<Any>> =
                    CompletableFuture.completedFuture(params.items.map { emptyMap<String, Any>() })
            }
        private val launcher =
            try {
                LSPLauncher.createClientLauncher(client, process.inputStream, process.outputStream, executor) { it }
            } catch (e: Exception) {
                process.destroyForcibly()
                process.waitFor(10, SECONDS)
                executor.shutdownNow()
                throw e
            }
        private val listening = launcher.startListening()
        val server = launcher.remoteProxy

        fun initialize(versionedEdits: Boolean = false) {
            val initialized =
                await(
                    server.initialize(
                        InitializeParams().apply {
                            capabilities =
                                ClientCapabilities().apply {
                                    workspace =
                                        WorkspaceClientCapabilities().apply {
                                            workspaceEdit = WorkspaceEditCapabilities().apply { documentChanges = versionedEdits }
                                        }
                                }
                        },
                    ),
                )
            assertThat(initialized.capabilities.definitionProvider.left).isTrue()
            assertThat(initialized.capabilities.typeDefinitionProvider.left).isTrue()
            assertThat(initialized.capabilities.implementationProvider.left).isTrue()
            assertThat(initialized.capabilities.callHierarchyProvider.left).isTrue()
            assertThat(initialized.capabilities.inlayHintProvider.left).isTrue()
            assertThat(initialized.capabilities.semanticTokensProvider).isNotNull()
            assertThat(initialized.capabilities.hoverProvider.left).isTrue()
            assertThat(initialized.capabilities.referencesProvider.left).isTrue()
            assertThat(initialized.capabilities.documentHighlightProvider.left).isTrue()
            assertThat(initialized.capabilities.completionProvider).isNotNull()
            if (versionedEdits) {
                assertThat(initialized.capabilities.renameProvider.right.prepareProvider).isTrue()
            } else {
                assertThat(initialized.capabilities.renameProvider).isNull()
            }
            assertThat(initialized.capabilities.signatureHelpProvider).isNotNull()
            server.initialized(InitializedParams())
        }

        fun verifySemantics(
            content: String,
            name: String,
            type: String,
        ) {
            val document = TextDocumentIdentifier(URI)
            val declaration = content.indexOf(name)
            val reference = content.lastIndexOf(name)
            val cursor = Position(0, reference)
            val declaredRange = Range(Position(0, declaration), Position(0, declaration + name.length))
            val usedRange = Range(cursor, Position(0, reference + name.length))
            val service = server.textDocumentService
            val hover = await(service.hover(HoverParams(document, cursor)))
            assertThat(hover.contents.right.value).contains(type)
            val definitions = await(service.definition(DefinitionParams(document, cursor))).left
            assertThat(definitions.map { it.uri }).containsExactly(URI)
            assertThat(definitions.map { it.range }).containsExactly(declaredRange)
            val references = await(service.references(ReferenceParams(document, cursor, ReferenceContext(true))))
            assertThat(references.map { it.uri }).containsOnly(URI)
            assertThat(references.map { it.range }).containsExactly(declaredRange, usedRange)
            val highlights = await(service.documentHighlight(DocumentHighlightParams(document, cursor)))
            assertThat(highlights.map { it.range }).containsExactly(declaredRange, usedRange)
        }

        fun open(content: String) = server.textDocumentService.didOpen(DidOpenTextDocumentParams(TextDocumentItem(URI, "xtc", 1, content)))

        fun change(
            content: String,
            version: Int,
        ) = server.textDocumentService.didChange(
            DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier(URI, version),
                listOf(TextDocumentContentChangeEvent(content)),
            ),
        )

        fun diagnosticsAt(version: Int): PublishDiagnosticsParams {
            val deadline = System.nanoTime() + SECONDS.toNanos(30)
            while (true) {
                val remaining = deadline - System.nanoTime()
                val publication = if (remaining > 0) published.poll(remaining, NANOSECONDS) else null
                checkNotNull(publication) { "No diagnostics for version $version. ${log()}" }
                assertThat(publication.uri).isEqualTo(URI)
                assertThat(publication.version).isLessThanOrEqualTo(version)
                if (publication.version == version) return publication
            }
        }

        fun diagnosticsFor(
            uri: String,
            version: Int?,
        ): PublishDiagnosticsParams {
            val deadline = System.nanoTime() + SECONDS.toNanos(30)
            while (true) {
                val remaining = deadline - System.nanoTime()
                val publication = if (remaining > 0) published.poll(remaining, NANOSECONDS) else null
                checkNotNull(publication) { "No diagnostics for $uri version $version. ${log()}" }
                if (publication.uri == uri && publication.version == version) return publication
            }
        }

        fun <T> await(future: Future<T>): T =
            try {
                future.get(30, SECONDS)
            } catch (e: TimeoutException) {
                throw AssertionError("LSP request timed out. ${log()}", e)
            }

        fun shutdownAndExit() {
            await(server.shutdown())
            server.exit()
            expectExit(0)
        }

        fun expectExit(status: Int) {
            assertThat(process.waitFor(10, SECONDS)).describedAs("Server did not exit. ${log()}").isTrue()
            assertThat(process.exitValue()).describedAs(log()).isEqualTo(status)
        }

        private fun log(): String = Files.readString(stderr).takeLast(8192)

        override fun close() {
            try {
                if (process.isAlive) process.destroyForcibly()
                check(process.waitFor(10, SECONDS)) { "Could not terminate test server. ${log()}" }
            } finally {
                listening.cancel(true)
                executor.shutdownNow()
                process.outputStream.close()
                process.inputStream.close()
            }
        }
    }

    private companion object {
        const val URI = "file:///Stdio.x"
        const val VALID = "module Stdio { Int run() { Int value = 1; return value; } }"
        const val REOPENED = "module Stdio { String run() { String label = \"ok\"; return label; } }"
        const val BROKEN = "module Stdio { Int run() { return missing; } }"
        const val BOOTSTRAP = "org/xvm/lsp/xdk/javatools_turtle.xtc"
    }
}
