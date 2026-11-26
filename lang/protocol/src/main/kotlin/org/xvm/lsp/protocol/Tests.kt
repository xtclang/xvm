package org.xvm.lsp.protocol

/**
 * A test of a module, or a container of tests, to run: its xunit [selector], the path of a package, class or test
 * method in its module, and its [kind], `package`, `class` or `test`, as [ServerTest] gives them; a `module` selects
 * every test of the module.
 */
data class TestSelector(
    val selector: String = "",
    val kind: String = ServerTest.TEST,
)

/** A source module's tests, under the module, as `xtc/tests` gives them. */
data class ModuleTests(
    val module: String = "",
    val uri: String = "",
    val tests: ServerTest = ServerTest(),
)

/**
 * A test of a module, or a container of tests, as xunit runs them: its [selector] is what xunit's discovery takes to
 * run it alone, the path of a package or class in its module, such as `tests.MathTest`, a test method's, such as
 * `tests.MathTest.adds`, or nothing for the module; [name] is its own name, and [location] its declaration's name.
 * Its [kind] is [MODULE], [PACKAGE], [CLASS] or [TEST], as a [TestSelector] takes it; a string, since lsp4j's JSON
 * writes an enum as its ordinal.
 */
data class ServerTest(
    val selector: String = "",
    val name: String = "",
    val kind: String = MODULE,
    val location: TestLocation? = null,
    val children: List<ServerTest> = emptyList(),
) {
    companion object {
        const val MODULE = "module"
        const val PACKAGE = "package"
        const val CLASS = "class"
        const val TEST = "test"
    }
}

/** Where a test's declaration is, zero-based. */
data class TestLocation(
    val uri: String = "",
    val startLine: Int = 0,
    val startColumn: Int = 0,
    val endLine: Int = 0,
    val endColumn: Int = 0,
)
