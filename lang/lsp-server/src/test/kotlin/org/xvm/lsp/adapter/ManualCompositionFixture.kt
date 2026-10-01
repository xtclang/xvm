package org.xvm.lsp.adapter

import java.nio.file.Path

/** Reuse the actual manual compiler programs; only turn their package into a standalone module. */
internal fun manualCompositionFixture(name: String): String =
    Path
        .of(requireNotNull(System.getProperty("xtc.composite.root")))
        .resolve("manualTests/src/main/x/jit/jit_tests/basic/$name.x")
        .toFile()
        .readText()
        .replaceFirst("package $name", "module $name")

internal fun String.positionOf(
    anchor: String,
    within: String = anchor,
): Position {
    val at = indexOf(anchor).also { require(it >= 0) { "Missing fixture anchor: $anchor" } } + anchor.indexOf(within)
    val preceding = take(at)
    return Position(preceding.count { it == '\n' }, preceding.substringAfterLast('\n').length)
}
