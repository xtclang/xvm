package org.xvm.lsp.adapter

import java.nio.file.Path

/** Reuse a manual compiler program as a standalone module, including its named support types. */
internal fun manualCompositionFixture(
    name: String,
    vararg supportTypes: String,
): String {
    val root =
        Path
            .of(requireNotNull(System.getProperty("xtc.composite.root")))
            .resolve("manualTests/src/main/x/jit/jit_tests")
    val source = root.resolve("basic/$name.x").toFile().readText()
    val support = supportTypes.joinToString("") { "\n" + root.resolve("$it.x").toFile().readText() }
    return source.replaceFirst("package $name {", "module $name {$support")
}

internal fun String.positionOf(
    anchor: String,
    within: String = anchor,
): Position {
    val at = indexOf(anchor).also { require(it >= 0) { "Missing fixture anchor: $anchor" } } + anchor.indexOf(within)
    val preceding = take(at)
    return Position(preceding.count { it == '\n' }, preceding.substringAfterLast('\n').length)
}
