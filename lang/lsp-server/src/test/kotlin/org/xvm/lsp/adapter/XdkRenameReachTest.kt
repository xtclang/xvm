package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.lsp.adapter.xdk.XdkDependencies
import org.xvm.lsp.adapter.xdk.XdkProject
import org.xvm.lsp.adapter.xdk.XdkProjectQueries
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.logged
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/**
 * After a rename's edit, its proof compiles only the modules that depend on the edited ones that use what the edit
 * changes ([org.xvm.lsp.adapter.xdk.XdkMemberUseTracker]): those that write a name of a declaration whose API it
 * changes, use a type whose members it changes as a whole, or name a declaration whose place it moves. Each case is a
 * module that writes neither name, and must either be compiled, as its bindings can change, or keep its compile. Each
 * checks the rename's outcome, which is the one that compiling every dependent module gives, and the modules compiled
 * after the edit, counted by name, never timed.
 */
class XdkRenameReachTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `a module that writes neither name nor uses the renamed method's class keeps its compile`() {
        val rename =
            rename(
                mapOf("Lib" to LIB, "Uses" to USES, "Far" to FAR, "Top" to TOP),
                mapOf("Uses" to setOf("Lib"), "Far" to setOf("Lib"), "Top" to setOf("Uses")),
            )
        // Uses calls the method; Far uses only Lib's function, and Top only Uses' method, on a line the edit leaves
        assertThat(rename.edited).containsExactlyInAnyOrder("Lib", "Uses")
        assertThat(rename.compiled).containsExactly("Lib", "Uses")
    }

    @Test
    fun `a module that matches the renamed method's class with an interface by its members is compiled`() {
        // Duck assigns what Maker makes, a Lib.C, to Iface's HasFoo, which C matches by its foo
        val maker = "module Maker {\n    package lib import Lib;\n\n    static lib.C make() = new lib.C();\n}\n"
        val duck =
            "module Duck {\n    package iface import Iface;\n    package maker import Maker;\n\n" +
                "    Int run() {\n        iface.HasFoo h = maker.make();\n        return 0;\n    }\n}\n"
        val rename =
            rename(
                mapOf("Lib" to LIB, "Iface" to HAS_FOO, "Maker" to maker, "Duck" to duck),
                mapOf("Maker" to setOf("Lib"), "Duck" to setOf("Iface", "Maker")),
            )
        // C no longer matches HasFoo, so Duck does not compile
        assertThat(rename.edited).isNull()
        assertThat(rename.compiled).containsExactly("Lib", "Maker", "Duck")
    }

    @Test
    fun `a module whose interface the renamed method's class is matched with is compiled`() {
        val lib = "module Lib {\n    interface I {\n        Int foo();\n    }\n}\n"
        val impl = "module Impl {\n    class Y {\n        Int foo() = 1;\n    }\n}\n"
        val assign =
            "module Assign {\n    package lib import Lib;\n    package impl import Impl;\n\n" +
                "    Int run() {\n        lib.I i = new impl.Y();\n        return 0;\n    }\n}\n"
        val rename = rename(mapOf("Lib" to lib, "Impl" to impl, "Assign" to assign), mapOf("Assign" to setOf("Lib", "Impl")))
        assertThat(rename.edited).isNull()
        assertThat(rename.compiled).containsExactly("Lib", "Assign")
    }

    @Test
    fun `a module whose class extends the renamed method's class is compiled, as its dispatch can change`() {
        // D gets the default bar of HasBar; after the rename, C's own bar
        val hasBar = "module Iface {\n    interface HasBar {\n        Int bar() = 0;\n    }\n}\n"
        val sub =
            "module Sub {\n    package lib import Lib;\n    package iface import Iface;\n\n" +
                "    class D\n            extends lib.C\n            implements iface.HasBar {\n    }\n}\n"
        val rename = rename(mapOf("Lib" to LIB, "Iface" to hasBar, "Sub" to sub), mapOf("Sub" to setOf("Lib", "Iface")))
        assertThat(rename.edited).isNull()
        assertThat(rename.compiled).containsExactly("Lib", "Sub")
    }

    @Test
    fun `a module that uses the renamed method's class through a typedef is compiled`() {
        val alias =
            "module Alias {\n    package lib import Lib;\n\n    typedef lib.C as Thing;\n\n" +
                "    static Thing make() = new lib.C();\n}\n"
        val typed =
            "module Typed {\n    package alias import Alias;\n    package iface import Iface;\n\n" +
                "    Int run() {\n        alias.Thing thing = alias.make();\n        iface.HasFoo h = thing;\n        return 0;\n    }\n}\n"
        val rename =
            rename(
                mapOf("Lib" to LIB, "Iface" to HAS_FOO, "Alias" to alias, "Typed" to typed),
                mapOf("Alias" to setOf("Lib"), "Typed" to setOf("Alias", "Iface")),
            )
        assertThat(rename.edited).isNull()
        assertThat(rename.compiled).containsExactly("Lib", "Alias", "Typed")
    }

    @Test
    fun `a module that reaches the renamed method's class through a property is compiled, and one that calls it renamed`() {
        val holder = "module Holder {\n    package lib import Lib;\n\n    class Box {\n        lib.C c = new lib.C();\n    }\n}\n"
        val through =
            "module Through {\n    package holder import Holder;\n    package iface import Iface;\n\n" +
                "    Int run() {\n        iface.HasFoo h = new holder.Box().c;\n        return 0;\n    }\n}\n"
        val refused =
            rename(
                mapOf("Lib" to LIB, "Iface" to HAS_FOO, "Holder" to holder, "Through" to through),
                mapOf("Holder" to setOf("Lib"), "Through" to setOf("Holder", "Iface")),
            )
        assertThat(refused.edited).isNull()
        assertThat(refused.compiled).containsExactly("Lib", "Holder", "Through")

        // a call through the property writes the name, and is renamed with it
        val call =
            "module Call {\n    package holder import Holder;\n\n" +
                "    Int run() {\n        return new holder.Box().c.foo();\n    }\n}\n"
        val renamed =
            rename(mapOf("Lib" to LIB, "Holder" to holder, "Call" to call), mapOf("Holder" to setOf("Lib"), "Call" to setOf("Holder")))
        assertThat(renamed.edited).containsExactlyInAnyOrder("Lib", "Call")
        assertThat(renamed.compiled).containsExactly("Lib", "Holder", "Call")
    }

    @Test
    fun `a module whose generic call infers the renamed method's class is compiled`() {
        val gen = "module Gen {\n    package iface import Iface;\n\n    static <Value extends iface.HasFoo> Int take(Value value) = 0;\n}\n"
        val infer =
            "module Infer {\n    package gen import Gen;\n    package lib import Lib;\n\n" +
                "    Int run() = gen.take(new lib.C());\n}\n"
        val rename =
            rename(
                mapOf("Lib" to LIB, "Iface" to HAS_FOO, "Gen" to gen, "Infer" to infer),
                mapOf("Gen" to setOf("Iface"), "Infer" to setOf("Gen", "Lib")),
            )
        assertThat(rename.edited).isNull()
        assertThat(rename.compiled).containsExactly("Lib", "Infer")
    }

    @Test
    fun `a module that imports the renamed method's class under another name is compiled`() {
        val imported =
            "module Imported {\n    package lib import Lib;\n    package iface import Iface;\n\n    import lib.C as Thing;\n\n" +
                "    Int run() {\n        iface.HasFoo h = new Thing();\n        return 0;\n    }\n}\n"
        val rename =
            rename(mapOf("Lib" to LIB, "Iface" to HAS_FOO, "Imported" to imported), mapOf("Imported" to setOf("Lib", "Iface")))
        assertThat(rename.edited).isNull()
        assertThat(rename.compiled).containsExactly("Lib", "Imported")
    }

    @Test
    fun `a module that gets the renamed method's class from a conditional return is compiled`() {
        val lib =
            LIB.replace(
                "    static Int other() = 2;",
                "    static Int other() = 2;\n\n    static conditional C find() = (True, new C());",
            )
        val cond =
            "module Cond {\n    package lib import Lib;\n    package iface import Iface;\n\n" +
                "    Int run() {\n        if (lib.C c := lib.find()) {\n            iface.HasFoo h = c;\n        }\n" +
                "        return 0;\n    }\n}\n"
        val rename = rename(mapOf("Lib" to lib, "Iface" to HAS_FOO, "Cond" to cond), mapOf("Cond" to setOf("Lib", "Iface")))
        assertThat(rename.edited).isNull()
        assertThat(rename.compiled).containsExactly("Lib", "Cond")
    }

    @Test
    fun `a module that names a declaration on an edited line is compiled, as the edit moves it`() {
        // E, which has no member of either name, is declared after C on the line that the rename edits
        val lib = "module Lib {\n    class C { Int foo() = 1; } class E { Int baz() = 2; }\n}\n"
        val pos = "module Pos {\n    package lib import Lib;\n\n    Int run() = new lib.E().baz();\n}\n"
        val far = "module Far {\n    Int run() = 3;\n}\n"
        val rename = rename(mapOf("Lib" to lib, "Pos" to pos, "Far" to far), mapOf("Pos" to setOf("Lib"), "Far" to setOf("Lib")), "first")
        assertThat(rename.edited).containsExactly("Lib")
        assertThat(rename.compiled).containsExactly("Lib", "Pos")
    }

    @Test
    fun `a rename to a name that is not a plain identifier keeps the compile of the modules it does not reach, and says why`() {
        val lines = CopyOnWriteArrayList<String>()
        val rename =
            logged(lines) {
                rename(
                    mapOf("Lib" to LIB, "Uses" to USES, "Far" to FAR, "Top" to TOP),
                    mapOf("Uses" to setOf("Lib"), "Far" to setOf("Lib"), "Top" to setOf("Uses")),
                    "föo",
                )
            }
        // Far and Top keep their compile, as for any other name: the tracker reads names of any letters
        assertThat(rename.edited).containsExactlyInAnyOrder("Lib", "Uses")
        assertThat(rename.compiled).containsExactly("Lib", "Uses")
        assertThat(
            lines,
        ).contains("compile: of the 2 modules that depend on those compiled again, 2 keep their compile (Far, Top) and 0 compile again")
    }

    /** What a rename did: the modules it edits, or null when refused, and those its proof compiled after the edit. */
    private class Renamed(
        val edited: List<String>?,
        val compiled: List<String>,
    )

    /**
     * A rename of Lib's first `foo` to [name], over the modules of [texts], by name, each depending on those that
     * [dependencies] gives it.
     */
    private fun rename(
        texts: Map<String, String>,
        dependencies: Map<String, Set<String>>,
        name: String = "bar",
    ): Renamed {
        CompilerTestSupport.configure()
        val root = directory.toRealPath().toFile()
        val files = texts.mapValues { (module, text) -> root.resolve("$module.x").apply { writeText(text) } }
        val modules = texts.keys.map { XdkSourceModule(it, files.getValue(it).uri(), dependencies[it].orEmpty()) }
        val compiled = CopyOnWriteArrayList<String>()
        val queries =
            XdkProjectQueries(
                XdkProject(modules),
                emptyMap(),
                XdkDependencies(emptyList()),
                { sources, repository, errors ->
                    EmbeddingSupport.instance().compileModule(sources, repository, errors).also { compiled += sources.qualifiedModuleName }
                },
                { false },
            )
        val lib = texts.getValue("Lib")
        val line = lib.lines().indexOfFirst { "foo" in it }
        val edit = queries.rename(files.getValue("Lib").uri(), line, lib.lines()[line].indexOf("foo"), name)
        val edited = edit?.changes?.keys?.map { uri -> files.entries.single { it.value.uri() == uri }.key }
        // the revision's compile of every module, then the proof's after the edit
        return Renamed(edited, compiled.drop(texts.size))
    }

    private fun File.uri(): String = toURI().toString()

    private companion object {
        val LIB = "module Lib {\n    class C {\n        Int foo() = 1;\n    }\n\n    static Int other() = 2;\n}\n"

        /** A call of foo, on a line of its own, below Uses' method. */
        val USES = "module Uses {\n    package lib import Lib;\n\n    Int run() {\n        return new lib.C().foo();\n    }\n}\n"

        val FAR = "module Far {\n    package lib import Lib;\n\n    Int run() = lib.other();\n}\n"

        val TOP = "module Top {\n    package uses import Uses;\n\n    Int go() = uses.run();\n}\n"

        val HAS_FOO = "module Iface {\n    interface HasFoo {\n        Int foo();\n    }\n}\n"
    }
}
