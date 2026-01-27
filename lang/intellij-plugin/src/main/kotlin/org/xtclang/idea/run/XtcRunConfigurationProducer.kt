package org.xtclang.idea.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Produces XTC run configurations automatically when the user invokes
 * Run (Ctrl+Shift+R) from a .x file context.
 *
 * This allows users to run XTC modules without manually creating run configurations.
 */
class XtcRunConfigurationProducer : LazyRunConfigurationProducer<XtcRunConfiguration>() {
    override fun getConfigurationFactory(): ConfigurationFactory = XtcRunConfigurationType().configurationFactories.first()

    override fun setupConfigurationFromContext(
        configuration: XtcRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val file = context.location?.psiElement?.containingFile ?: return false

        // Only handle .x files
        if (!file.name.endsWith(".x")) {
            return false
        }

        // Extract module name from the file (filename without extension)
        val moduleName = extractModuleName(file)
        if (moduleName != null) {
            configuration.moduleName = moduleName
            configuration.name = "Run $moduleName"
            configuration.useGradle = true
            sourceElement.set(file)
            return true
        }

        return false
    }

    override fun isConfigurationFromContext(
        configuration: XtcRunConfiguration,
        context: ConfigurationContext,
    ): Boolean {
        val file = context.location?.psiElement?.containingFile ?: return false

        if (!file.name.endsWith(".x")) {
            return false
        }

        val moduleName = extractModuleName(file)
        return moduleName != null && configuration.moduleName == moduleName
    }

    /**
     * Extract the module name from an XTC source file.
     * Looks for "module <name>" declaration in the file content.
     */
    private fun extractModuleName(file: PsiFile): String? {
        val text = file.text
        // Simple regex to find module declaration: module <name> {
        val modulePattern = Regex("""^\s*module\s+(\w+)""", RegexOption.MULTILINE)
        val match = modulePattern.find(text)
        return match?.groupValues?.get(1)
    }
}
