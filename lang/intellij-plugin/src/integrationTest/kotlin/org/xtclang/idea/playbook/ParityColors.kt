package org.xtclang.idea.playbook

import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Editor
import com.intellij.driver.sdk.invokeAction
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

internal fun ParityScenarios.colorCases() {
    listOf("X273", "X274", "X275").forEach { id ->
        case(id) { data ->
            val values = common.getAsJsonObject("colorPrototype")
            val source = values.string("source")
            val document = open(values.string("file"), source)
            clean(document)
            check(protocol.capabilities().asJsonObject["colorProvider"].asBoolean)
            val expected = values["colors"].rows()

            fun colors() =
                query("textDocument/documentColor", document).rows().sortedWith(
                    compareBy(
                        { it["range"].asJsonObject["start"].asJsonObject.int("line") },
                        { it["range"].asJsonObject["start"].asJsonObject.int("character") },
                    ),
                )

            fun rgba() =
                colors().map { color ->
                    listOf("red", "green", "blue", "alpha").map { (color["color"].asJsonObject[it].asDouble * 255).roundToInt() }
                }
            check(rgba() == expected.map { it["rgba"].asJsonArray.map { channel -> channel.asInt } })
            with(driver) {
                val editor = document.editor
                val probe = utility(ColorProbe::class)

                fun offsets() = withContext(OnDispatcher.EDT) { probe.offsets(editor.editor) }

                fun visible() = withContext(OnDispatcher.EDT) { probe.visible() }

                fun renderedColors(expected: List<List<Int>>) {
                    awaitUi(
                        "rendered swatches match current constructor values",
                        15.seconds,
                        errorMessage = { "Expected rendered RGBA $expected, actual $it" },
                        getter = { withContext(OnDispatcher.EDT) { probe.rgba(editor.editor) }.chunked(4) },
                        // Java2D alpha compositing can round a channel by one byte.
                        checker = { actual ->
                            actual.size == expected.size &&
                                actual.zip(expected).all { (painted, wanted) ->
                                    painted.zip(wanted).all { (channel, value) -> abs(channel - value) <= 1 }
                                }
                        },
                    )
                }

                fun openPicker(index: Int) {
                    focusEditor(editor)
                    withContext(OnDispatcher.EDT) { probe.open(editor.editor, offsets()[index]) }
                    awaitUi("native color chooser opens", 15.seconds) { visible() }
                }

                fun dismiss() {
                    withContext(OnDispatcher.EDT) { probe.dismiss() }
                    awaitUi("native color chooser closes", 10.seconds) { !visible() }
                }
                val expectedOffsets = expected.map { source.indexOf(it.string("anchor")) }
                awaitUi("exactly two rendered swatches, none on dynamic values or ordinary strings", 30.seconds) {
                    offsets() == expectedOffsets
                }
                renderedColors(rgba())
                withContext(OnDispatcher.EDT) { probe.screenshot(editor.editor, directory.resolve("$id-swatches.png").toString()) }
                if (data.string("mode") == "recovery") {
                    val invalid = values.getAsJsonObject("invalid")
                    replace(document, source.replace(invalid.string("from"), invalid.string("to")))
                    diagnostics(document) { it.isNotEmpty() }
                    check(colors().isEmpty())
                    awaitUi("failed compilation retires native swatches", 30.seconds) { offsets().isEmpty() }
                    replace(document, source)
                    clean(document)
                    awaitUi("source repair restores native swatches", 30.seconds) { offsets() == expectedOffsets }
                    renderedColors(rgba())
                    return@case
                }
                openPicker(0)
                withContext(OnDispatcher.EDT) { probe.screenshot(editor.editor, directory.resolve("$id-picker.png").toString()) }
                if (data.string("mode") == "history") {
                    dismiss()
                    check(document.text == source) { "Dismissing the picker must not edit source" }
                    openPicker(0)
                }
                withContext(OnDispatcher.EDT) { probe.change("alpha") }
                val changed = source.replace(expected[0].string("anchor"), "new Rgba(255, 128, 0, alpha = 128)")
                awaitUi("native alpha field applies exact source-preserving edit", 15.seconds) { document.text == changed }
                dismiss()
                clean(document)
                check(rgba()[0] == listOf(255, 128, 0, 128))
                renderedColors(rgba())
                if (data.string("mode") == "history") {
                    focusEditor(editor)
                    invokeAction("\$Undo", component = editor.component)
                    awaitUi("one Undo restores the original constructor", 15.seconds) { document.text == source }
                    settle(document)
                    check(rgba()[0] == listOf(255, 128, 0, 255))
                    renderedColors(rgba())
                    invokeAction("\$Redo", component = editor.component)
                    awaitUi("one Redo restores the picker edit", 15.seconds) { document.text == changed }
                    clean(document)
                    renderedColors(rgba())
                } else {
                    openPicker(1)
                    withContext(OnDispatcher.EDT) { probe.change("rgb") }
                    val named = changed.replace(expected[1].string("anchor"), "new Rgba(blue = 192, red = 32, alpha = 128, green = 96)")
                    awaitUi("native RGB picker preserves named argument order", 15.seconds) { document.text == named }
                    dismiss()
                    clean(document)
                    check(rgba()[1] == listOf(32, 96, 192, 128))
                    renderedColors(rgba())
                    captureColorScheme(document)
                }
            }
        }
    }
}

/** Capture the installed scheme without cycling the visible editor through other palettes. */
internal fun ParityWorkspace.captureColorScheme(document: ParityWorkspace.Document) {
    with(driver) {
        withContext(OnDispatcher.EDT) {
            utility(ColorProbe::class).screenshot(document.editor.editor, directory.resolve("$id-current-theme.png").toString())
        }
    }
}

@Remote("org.xtclang.idea.playbook.probe.ColorUi", plugin = "org.xtclang.playbook.probe")
internal interface ColorProbe {
    fun offsets(editor: Editor): List<Int>

    fun rgba(editor: Editor): List<Int>

    fun open(
        editor: Editor,
        offset: Int,
    )

    fun visible(): Boolean

    fun change(channel: String)

    fun dismiss()

    fun screenshot(
        editor: Editor,
        file: String,
    )
}
