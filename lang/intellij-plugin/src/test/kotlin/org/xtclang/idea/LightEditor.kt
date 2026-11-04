package org.xtclang.idea

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.application.AccessToken
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * A light platform test's in-memory project, with the plugin and its dependencies loaded, for what IntelliJ does
 * in an Ecstasy file. A test registers it with `@RegisterExtension`; each test opens its own file. No language
 * server runs: these tests check the IDE's side.
 */
class LightEditor :
    BeforeEachCallback,
    AfterEachCallback {
    private lateinit var fixture: CodeInsightTestFixture
    private lateinit var errors: AccessToken

    override fun beforeEach(context: ExtensionContext) {
        val factory = IdeaTestFixtureFactory.getFixtureFactory()
        val project = factory.createLightFixtureBuilder(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR, context.displayName).fixture
        fixture = factory.createCodeInsightFixture(project, LightTempDirTestFixtureImpl(true))
        errors = LoggedErrorProcessor.executeWith(UltimateStartup)
        runInEdtAndWait { fixture.setUp() }
    }

    override fun afterEach(context: ExtensionContext) {
        try {
            runInEdtAndWait { fixture.tearDown() }
        } finally {
            errors.finish()
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
