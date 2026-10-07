package org.xtclang.idea.playbook.probe

import com.intellij.codeInsight.hints.LinearOrderInlayRenderer
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.colorpicker.ColorValuePanel
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Point
import java.awt.Window
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

/** Exercise the rendered inlay handler and real chooser fields without moving the desktop mouse. */
object ColorUi {
    private fun swatches(editor: Editor) =
        editor.inlayModel.getInlineElementsInRange(0, editor.document.textLength).filter {
            it.renderer is LinearOrderInlayRenderer<*> && "ColorInlayPresentation" in it.renderer.toString()
        }

    @JvmStatic
    fun offsets(editor: Editor): List<Int> = swatches(editor).filter { it.bounds != null }.map { it.offset }

    @JvmStatic
    fun rgba(editor: Editor): List<Int> =
        swatches(editor).flatMap { inlay ->
            val bounds = requireNotNull(inlay.bounds)
            // Paint the installed renderer on transparency, retaining the swatch's actual alpha.
            val image = BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB)
            val graphics = image.createGraphics()
            try {
                graphics.font = editor.contentComponent.font
                inlay.renderer.paint(
                    inlay,
                    graphics,
                    Rectangle2D.Float(0f, 0f, bounds.width.toFloat(), bounds.height.toFloat()),
                    TextAttributes(),
                )
            } finally {
                graphics.dispose()
            }
            val color = Color(image.getRGB(bounds.width / 2, bounds.height / 2), true)
            listOf(color.red, color.green, color.blue, color.alpha)
        }

    @JvmStatic
    fun open(
        editor: Editor,
        offset: Int,
    ) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val inlay = swatches(editor).single { it.offset == offset }
        val bounds = requireNotNull(inlay.bounds) { "Color swatch is not rendered" }
        val event =
            MouseEvent(
                editor.contentComponent,
                MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(),
                0,
                bounds.x + 2,
                bounds.y + bounds.height / 2,
                1,
                false,
                MouseEvent.BUTTON1,
            )
        val renderer = inlay.renderer as LinearOrderInlayRenderer<*>
        val point = Point(2, bounds.height / 2)
        // referenceOnHover installs its clickable presentation only after receiving a hover.
        // Match the context provided by normal IDE input; Driver EDT calls do not acquire it.
        WriteIntentReadAction.run {
            renderer.mouseMoved(event, point)
            renderer.mouseClicked(event, point)
        }
    }

    private fun descendants(component: Component): Sequence<Component> =
        sequenceOf(component) +
            (component as? Container)
                ?.components
                .orEmpty()
                .asSequence()
                .flatMap(::descendants)

    private fun picker(): ColorValuePanel? =
        Window
            .getWindows()
            .asSequence()
            .filter { it.isShowing }
            .flatMap(::descendants)
            .filterIsInstance<ColorValuePanel>()
            .singleOrNull { it.isShowing }

    @JvmStatic
    fun visible(): Boolean = picker() != null

    @JvmStatic
    fun change(channel: String) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val panel = requireNotNull(picker()) { "Native color chooser is not visible" }
        val field = if (channel == "alpha") panel.alphaField else panel.hexField
        field.requestFocusInWindow()
        // These are actual Swing text controls; their normal document listener updates the picker.
        field.text =
            when (channel) {
                "alpha" -> {
                    when (field.text) {
                        "100" -> "50"
                        "255" -> "128"
                        else -> error("Unexpected initial native alpha: ${field.text}")
                    }
                }

                "rgb" -> {
                    // The native hex control accepts RRGGBBAA; six digits reset alpha to opaque.
                    "2060C080"
                }

                else -> {
                    error("Unknown picker control: $channel")
                }
            }
    }

    @JvmStatic
    fun dismiss() {
        val field = requireNotNull(picker()).hexField
        IdeEventQueue.getInstance().postEvent(
            KeyEvent(field, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED),
        )
    }

    @JvmStatic
    fun screenshot(
        editor: Editor,
        file: String,
    ) {
        capture(SwingUtilities.getWindowAncestor(editor.contentComponent), file)
        picker()?.let { panel ->
            capture(SwingUtilities.getWindowAncestor(panel), file.removeSuffix(".png") + "-chooser.png")
        }
    }

    private fun capture(
        window: Window,
        file: String,
    ) {
        val image = BufferedImage(window.width, window.height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            window.paintAll(graphics)
        } finally {
            graphics.dispose()
        }
        check(ImageIO.write(image, "png", Path.of(file).toFile()))
    }
}
