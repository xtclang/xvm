package org.xtclang.idea.project

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.GeneratorNewProjectWizard
import com.intellij.ide.wizard.NewProjectWizardBaseStep
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.RootNewProjectWizardStep
import org.xtclang.idea.XtcIconProvider
import javax.swing.Icon

/**
 * Project generator for the New Project wizard in IntelliJ IDEA.
 * Creates XTC projects by invoking the `xtc init` CLI command.
 */
class XtcNewProjectWizard : GeneratorNewProjectWizard {
    override val id = "XTC"
    override val name = "XTC"
    override val icon: Icon = XtcIconProvider.XTC_ICON ?: com.intellij.icons.AllIcons.FileTypes.Any_type
    override val ordinal = 1000

    override fun isEnabled() = true

    override fun createStep(context: WizardContext): NewProjectWizardStep =
        RootNewProjectWizardStep(context)
            .nextStep(::NewProjectWizardBaseStep)
            .nextStep(::XtcNewProjectWizardStep)
}
