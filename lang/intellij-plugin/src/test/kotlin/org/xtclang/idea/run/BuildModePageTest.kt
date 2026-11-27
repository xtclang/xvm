package org.xtclang.idea.run

import com.intellij.testFramework.runInEdtAndGet
import com.intellij.util.ui.UIUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.xtclang.idea.LightEditor
import javax.swing.JEditorPane
import javax.swing.JRadioButton

/**
 * The Build and Run page and the Select Build Mode popup of a light project, while the language server says that the
 * project builds and runs with Gradle as it builds with another XDK than the bundled one: the embedded compiler is not
 * available in either, and each says which XDK the project builds with.
 */
class BuildModePageTest {
    @JvmField
    @RegisterExtension
    val editor = LightEditor()

    @Test
    fun `the page and the popup show the embedded compiler unavailable for a project on another XDK, and say which`() {
        val project = editor.fixture.project
        val settings = XtcBuildSettings.getInstance(project)
        val reason = "builds with XDK 0.5.0, not the bundled XDK 0.4.4"
        try {
            settings.replaced(project, emptyList(), listOf(reason))
            assertThat(settings.gradleReasons).containsExactly(reason)
            assertThat(XtcBuildSettings.effective(project)).isEqualTo(XtcBuildMode.GRADLE)

            val (buttons, comments) =
                runInEdtAndGet {
                    val panel = XtcBuildConfigurable(project).createPanel()
                    UIUtil.findComponentsOfType(panel, JRadioButton::class.java).associate { it.text to it.isEnabled } to
                        // the comments' HTML, with its lines as the page wraps them
                        UIUtil.findComponentsOfType(panel, JEditorPane::class.java).map { it.text.replace(Regex("\\s+"), " ") }
                }
            assertThat(buttons).isEqualTo(XtcBuildMode.entries.associate { it.label to (it != XtcBuildMode.EMBEDDED) })
            assertThat(comments).anyMatch { "XDK 0.5.0" in it && "bundled XDK 0.4.4" in it && "is kept" in it }

            val step = SelectBuildModeAction().step(project)
            assertThat(XtcBuildMode.entries.filter(step::isSelectable)).containsExactly(XtcBuildMode.AUTOMATIC, XtcBuildMode.GRADLE)
            assertThat(
                step.getTextFor(XtcBuildMode.EMBEDDED),
            ).isEqualTo("${XtcBuildMode.EMBEDDED.label} (not available: this project $reason)")
            assertThat(step.getTextFor(XtcBuildMode.GRADLE)).isEqualTo(XtcBuildMode.GRADLE.label)

            // once the server builds and runs the project's modules again, so does the chosen mode
            settings.replaced(project, emptyList(), emptyList())
            assertThat(XtcBuildMode.entries.filter(SelectBuildModeAction().step(project)::isSelectable)).isEqualTo(XtcBuildMode.entries)
        } finally {
            settings.replaced(project, emptyList(), emptyList())
        }
    }
}
