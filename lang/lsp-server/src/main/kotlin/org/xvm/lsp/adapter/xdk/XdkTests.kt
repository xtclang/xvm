package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ClassStructure
import org.xvm.asm.ConstantPool
import org.xvm.asm.MethodStructure
import org.xvm.asm.ModuleStructure
import org.xvm.asm.PackageStructure
import org.xvm.asm.XvmStructure
import org.xvm.asm.constants.IdentityConstant
import org.xvm.compiler.Token
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.compiler.ast.TypeCompositionStatement
import org.xvm.lsp.model.Location

/**
 * A test of a module, or a container of tests, as xunit runs them: its [selector] is what xunit's discovery takes to
 * run it alone, the path of a package or class in its module, such as `tests.MathTest`, a test method's, such as
 * `tests.MathTest.adds`, or nothing for the module; [name] is its own name, and [location] its declaration's name.
 * Its [kind] is [MODULE], [PACKAGE], [CLASS] or [TEST], as a launch's test selector takes it; a string, since the
 * protocol's JSON writes an enum as its ordinal.
 */
data class XdkTest(
    val selector: String,
    val name: String,
    val kind: String,
    val location: Location?,
    val children: List<XdkTest> = emptyList(),
) {
    companion object {
        const val MODULE = "module"
        const val PACKAGE = "package"
        const val CLASS = "class"
        const val TEST = "test"
    }
}

/** A module's tests, as its compilation declares them. */
internal object XdkTests {
    /** The annotations that make a method a test: the XDK's `@Test`, and xunit's own. */
    private val TEST_ANNOTATIONS = setOf("Test", "ParameterizedTest", "RepeatedTest")

    /** The modules that declare them. */
    private val TEST_MODULES = setOf("ecstasy.xtclang.org", "xunit.xtclang.org")

    /**
     * The tests of [module], under it, from its compiled sources [roots], whose declarations are at [locations]; null
     * when it has none. Runs on the compiler worker, which owns the module's structures.
     */
    fun of(
        module: ModuleStructure,
        roots: Collection<AstNode>,
        locations: Map<XvmStructure, Location>,
    ): XdkTest? {
        val tests =
            roots
                .asSequence()
                .flatMap { it.descendants() }
                .filterIsInstance<MethodDeclarationStatement>()
                .mapNotNull { it.component as? MethodStructure }
                .filter(::isTest)
                .map { Declared(owners(it), it) }
                .toList()
        return tests.takeIf { it.isNotEmpty() }?.let {
            XdkTest("", module.simpleName(), XdkTest.MODULE, locations[module], children(it, 0, locations))
        }
    }

    /**
     * The tests of the module that the compiled sources [roots] declare, whose declarations are at [locations]; null
     * when it has none, or the sources declare no module. Runs on the compiler worker, which owns the module's
     * structures.
     */
    fun of(
        roots: Collection<AstNode>,
        locations: Map<XvmStructure, Location>,
    ): XdkTest? {
        val module =
            roots
                .asSequence()
                .flatMap { it.descendants() }
                .filterIsInstance<TypeCompositionStatement>()
                .firstOrNull { it.category.id == Token.Id.MODULE }
                ?.component as? ModuleStructure
        return module?.let { ConstantPool.withPool(it.constantPool).use { _ -> of(it, roots, locations) } }
    }

    /** A test method, and the packages and classes it is in, from the module's child down to its own class. */
    private data class Declared(
        val owners: List<ClassStructure>,
        val method: MethodStructure,
    )

    /**
     * The tests, and the containers of tests, [depth] levels under the module, in the order of their first test; the
     * methods of one name are one test, as xunit's selector runs them all.
     */
    private fun children(
        tests: List<Declared>,
        depth: Int,
        locations: Map<XvmStructure, Location>,
    ): List<XdkTest> =
        tests
            .groupBy { test -> test.owners.getOrNull(depth) ?: test.method.name }
            .map { (owner, group) ->
                if (owner is ClassStructure) {
                    val kind = if (owner is PackageStructure) XdkTest.PACKAGE else XdkTest.CLASS
                    XdkTest(path(owner), owner.name, kind, locations[owner], children(group, depth + 1, locations))
                } else {
                    val (owners, method) = group.first()
                    val selector = owners.lastOrNull()?.let { "${path(it)}.${method.name}" } ?: method.name
                    XdkTest(selector, method.name, XdkTest.TEST, locations[method])
                }
            }

    /** The packages and classes that [method] is in, under its module, outermost first. */
    private fun owners(method: MethodStructure): List<ClassStructure> =
        generateSequence(method.containingClass) { it.parent as? ClassStructure }
            .takeWhile { it !is ModuleStructure }
            .toList()
            .asReversed()

    private fun isTest(method: MethodStructure): Boolean =
        method.annotations.any { annotation ->
            (annotation.annotationClass as? IdentityConstant)?.let {
                it.name in TEST_ANNOTATIONS && it.moduleConstant.name in TEST_MODULES
            } ==
                true
        }

    /** The path of a package or class in its module, as xunit's discovery takes it. */
    private fun path(owner: ClassStructure): String = owner.identityConstant.pathString

    /** A module's name without its qualifier, as its source declares it. */
    private fun ModuleStructure.simpleName(): String = name.substringBefore('.')
}
