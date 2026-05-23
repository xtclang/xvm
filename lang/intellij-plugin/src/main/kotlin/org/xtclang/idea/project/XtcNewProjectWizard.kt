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
    // `id` is the internal generator key used by IntelliJ to identify this wizard
    // across sessions/settings; it must stay stable. `name` is the human-readable
    // label shown in the New Project wizard's generator list.
    override val id = "XTC"
    override val name = "Ecstasy"
    override val icon: Icon = XtcIconProvider.XTC_ICON ?: com.intellij.icons.AllIcons.FileTypes.Any_type
    override val ordinal = 1000

    override fun isEnabled() = true

    override fun createStep(context: WizardContext): NewProjectWizardStep =
        RootNewProjectWizardStep(context)
            .nextStep(::NewProjectWizardBaseStep)
            .nextStep(::XtcNewProjectWizardStep)
}
