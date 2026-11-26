package org.xtclang.idea.lsp

import com.intellij.ide.hierarchy.LanguageCallHierarchy
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.runInEdtAndGet
import com.redhat.devtools.lsp4ij.features.hierarchy.LSPHierarchyNodeDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.CallHierarchyIncomingCall
import org.eclipse.lsp4j.CallHierarchyItem
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolKind
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.xtclang.idea.LightEditor
import org.xtclang.idea.StubLanguageServer
import org.xtclang.idea.XtcIntelliJLanguage
import org.xtclang.idea.awaitUntil
import org.xtclang.idea.what
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

/**
 * Call Hierarchy (Ctrl+Alt+H) in an Ecstasy file shows a method's callers also when a write action comes while IntelliJ
 * waits for them: LSP4IJ 0.30.1 then cancels the request and returns no callers (UP88), which IntelliJ keeps, and
 * XtcCallHierarchyProvider's structure throws the cancellation, so that IntelliJ asks again after the write action.
 */
class XtcCallHierarchyProviderTest {
    @JvmField
    @RegisterExtension
    val editor = LightEditor(StubLanguageServer(callHierarchy = true))

    @TempDir
    lateinit var directory: Path

    @Test
    fun `an Ecstasy file's call hierarchy is the plugin's`() {
        assertThat(LanguageCallHierarchy.INSTANCE.forLanguage(XtcIntelliJLanguage)).isInstanceOf(XtcCallHierarchyProvider::class.java)
    }

    @Test
    fun `the callers show when a write action comes while IntelliJ waits for them`() {
        // the server holds the first request's answer, which the write action's cancellation ends, and answers the
        // next at once
        val asked = AtomicInteger()
        editor.server.incomingCalls = { params ->
            if (asked.getAndIncrement() == 0) CompletableFuture() else CompletableFuture.completedFuture(listOf(caller(params.item)))
        }
        // a file on disk, which the light project's in-memory files are not, so that LSP4IJ finds the callers' file by
        // its URI
        val source = Files.writeString(directory.resolve("Callers.x"), SOURCE)
        val file = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(source))
        val structure =
            runInEdtAndGet {
                with(editor.fixture) {
                    openFileInEditor(file)
                    editor.caretModel.moveToOffset(SOURCE.indexOf("cardMatches"))
                    val context =
                        SimpleDataContext
                            .builder()
                            .add(CommonDataKeys.PROJECT, project)
                            .add(CommonDataKeys.PSI_FILE, this.file)
                            .add(CommonDataKeys.EDITOR, editor)
                            .build()
                    XtcCallHierarchyProvider.Callers(project, checkNotNull(XtcCallHierarchyProvider().getTarget(context)))
                }
            }
        val callers =
            runBlocking(Dispatchers.Default) {
                // as IntelliJ's hierarchy tree computes a node's children: in a read action, which a write action
                // cancels, and which then runs again
                val method = readAction { structure.getChildElements(checkNotNull(structure.baseDescriptor)) }.single()
                val children = async { readAction { structure.getChildElements(method) } }
                awaitUntil(what { "the server asked for the callers, ${editor.server.requests}" }) { asked.get() > 0 }
                edtWriteAction {}
                children.await()
            }
        assertThat(callers.map(::name))
            .describedAs("the callers, after the requests %s", editor.server.requests)
            .containsExactly("choose")
        assertThat(editor.server.requests.filter { it == "callHierarchy/incomingCalls" }).hasSize(2)
    }

    /** The name of the method of a node of the call hierarchy. */
    private fun name(node: Any) = ((node as LSPHierarchyNodeDescriptor<*>).hierarchyItem as CallHierarchyItem).name

    /** The method that calls [method] in SOURCE, choose(), declared in the same file. */
    private fun caller(method: CallHierarchyItem): CallHierarchyIncomingCall {
        val line = SOURCE.lines().indexOfFirst { "choose" in it }
        val column = SOURCE.lines()[line].indexOf("choose")
        val range = Range(Position(line, column), Position(line, column + "choose".length))
        return CallHierarchyIncomingCall(CallHierarchyItem("choose", SymbolKind.Method, method.uri, range, range), listOf(range))
    }

    companion object {
        private val SOURCE =
            """
            |module M {
            |    static Boolean cardMatches(Int card) = card > 0;
            |
            |    Boolean choose(Int card) = cardMatches(card);
            |}
            |
            """.trimMargin()
    }
}
