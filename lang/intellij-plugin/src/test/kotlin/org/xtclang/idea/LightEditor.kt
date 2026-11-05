package org.xtclang.idea

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.application.AccessToken
import com.intellij.psi.PsiFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import com.redhat.devtools.lsp4ij.server.definition.extension.ServerExtensionPointBean
import org.jetbrains.plugins.textmate.TextMateService
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.nio.file.Path

/**
 * A light platform test's in-memory project, with the plugin and its dependencies loaded, for what IntelliJ does
 * in an Ecstasy file. A test registers it with `@RegisterExtension`; each test opens its own file, highlighted by
 * TextMate's Ecstasy grammar as in the IDE. The language server is [server], a stub in the test's JVM, so that
 * LSP4IJ's side runs as it does against the real one. The tests share the light project, so each test's server serves
 * that test's wrappers alone, and the wrappers that a test leaves are disposed as it ends, whether it passed or not.
 */
class LightEditor(
    val server: StubLanguageServer = StubLanguageServer(),
) : BeforeEachCallback,
    AfterEachCallback {
    /** The test's fixture; [open] gives it a file and runs on the EDT, as most tests need. */
    lateinit var fixture: CodeInsightTestFixture
        private set
    private lateinit var errors: AccessToken

    override fun beforeEach(context: ExtensionContext) {
        val factory = IdeaTestFixtureFactory.getFixtureFactory()
        val project = factory.createLightFixtureBuilder(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR, context.displayName).fixture
        fixture = factory.createCodeInsightFixture(project, LightTempDirTestFixtureImpl(true))
        errors = LoggedErrorProcessor.executeWith(UltimateStartup)
        runInEdtAndWait { fixture.setUp() }
        // the plugin's own provider finds no plugin directory here, so the editor would have no TextMate tokens
        System.getProperty("xtc.test.textmate")?.let { bundle ->
            TextMateBundleProvider.EP_NAME.point.registerExtension(
                object : TextMateBundleProvider {
                    override fun getBundles() = listOf(TextMateBundleProvider.PluginBundle("Ecstasy", Path.of(bundle)))
                },
                fixture.testRootDisposable,
            )
            TextMateService.getInstance().reloadEnabledBundles()
        }
        ExtensionTestUtil.maskExtensions(ServerExtensionPointBean.EP_NAME, listOf(server.extension()), fixture.testRootDisposable, true)
    }

    override fun afterEach(context: ExtensionContext) {
        val project = fixture.project
        try {
            runInEdtAndWait { fixture.tearDown() }
        } finally {
            try {
                // the next test reuses the light project, and the wrappers that LSP4IJ keeps for it: the removal of this
                // test's server disposes only its own, and a failed test can leave more
                runInEdtAndWait { LanguageServiceAccessor.getInstance(project).projectClosing(project) }
            } finally {
                errors.finish()
            }
        }
    }

    /** Opens [text] as the file [name] in an editor, with the caret where [text] has `<caret>`, and runs [test] on the EDT. */
    fun <T> open(
        name: String,
        text: String,
        test: CodeInsightTestFixture.(PsiFile) -> T,
    ): T = runInEdtAndGet { fixture.test(fixture.configureByText(name, text)) }

    /**
     * The IDE these tests load is IntelliJ IDEA's single distribution, whose licensed part cannot start one of its
     * startup activities in a test project and logs that as an error, which a test would otherwise fail on.
     */
    private object UltimateStartup : LoggedErrorProcessor() {
        override fun processError(
            category: String,
            message: String,
            details: Array<out String>,
            t: Throwable?,
        ): Set<Action> =
            if (generateSequence(t) { it.cause }.any { (it as? PluginException)?.pluginId?.idString == "com.intellij.modules.ultimate" }) {
                Action.NONE
            } else {
                super.processError(category, message, details, t)
            }
    }
}
