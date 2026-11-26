package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.lsp.adapter.xdk.XdkCodeActionCache
import org.xvm.lsp.adapter.xdk.XdkDependencies
import org.xvm.lsp.adapter.xdk.XdkProject
import org.xvm.lsp.adapter.xdk.XdkProjectQueries
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The member-use tracker ([org.xvm.lsp.adapter.xdk.XdkMemberUseTracker]) is sound: after each edit of a corpus over
 * modules that depend on each other, the next revision's compile, which starts from the one before and keeps the compile
 * of each module the tracker keeps, gives every module the facts, artifact and errors that compiling every module from
 * nothing gives it; and so does a proof's compile of the same edit, which starts from the revision's. Each case also says
 * which modules the tracker compiles again, by name, never timed. Most edits break or change a module that they reach,
 * which the comparison finds when the tracker keeps that module's compile. Some do not, and only the modules compiled
 * carry them: the edits that compile the edited module alone; a changed default value, as a call that leaves the
 * argument out passes the callee's default; and a private method added to a class that another module's class extends,
 * which no case shows that module to see.
 */
class XdkMemberUseTrackerTest {
    @TempDir lateinit var directory: Path

    /** The cases a test made so far, each in a directory of its own. */
    private var cases = 0

    @Test
    fun `a renamed method compiles the module that matches its class with an interface, which writes neither name`() {
        val duck =
            """
            module Duck {
                package lib import Lib;
                package iface import Iface;

                Int run() {
                    iface.HasFoo h = lib.make();
                    return 0;
                }
            }
            """.trimIndent()
        assertFooRenameBreaksDuck(LIB, duck)
    }

    @Test
    fun `an overload with the name of a method a module calls compiles that module, whose call now binds to it`() {
        val lib =
            """
            module Lib {
                static Int other() = 2;

                class C {
                    Int foo(Number n) = 1;
                }
            }
            """.trimIndent()
        val use =
            """
            module Use {
                package lib import Lib;

                Int run() = new lib.C().foo(1);
            }
            """.trimIndent()
        val outcome =
            edit(
                mapOf("Lib" to lib, "Use" to use, "Far" to FAR),
                mapOf("Use" to setOf("Lib"), "Far" to setOf("Lib")),
                "Lib" to lib.replace("Int foo(Number n) = 1;", "Int foo(Number n) = 1;\n\n        Int foo(Int n) = 2;"),
            )
        assertThat(outcome.compiled).containsExactly("Lib", "Use")
    }

    @Test
    fun `a removed method compiles the modules that call it and those that use its class`() {
        val use =
            """
            module Use {
                package lib import Lib;

                Int run() = new lib.C().foo();
            }
            """.trimIndent()
        val whole =
            """
            module Whole {
                package lib import Lib;

                Int run() = new lib.C().more();
            }
            """.trimIndent()
        val outcome =
            edit(
                mapOf("Lib" to LIB_C, "Use" to use, "Whole" to whole, "Far" to FAR),
                mapOf("Use" to setOf("Lib"), "Whole" to setOf("Lib"), "Far" to setOf("Lib")),
                "Lib" to LIB_C.replace("        Int foo() = 1;\n", ""),
            )
        assertThat(outcome.compiled).containsExactly("Lib", "Use", "Whole")
        assertThat(outcome.errors("Use")).isNotEmpty()
    }

    @Test
    fun `a changed signature and a changed property type compile the module that uses them`() {
        val use =
            """
            module Use {
                package lib import Lib;

                Int run() {
                    Int x = new lib.C().foo();
                    Int y = new lib.C().size;
                    return x + y;
                }
            }
            """.trimIndent()
        val modules = mapOf("Lib" to LIB_C, "Use" to use, "Far" to FAR)
        val dependencies = mapOf("Use" to setOf("Lib"), "Far" to setOf("Lib"))
        val signature = edit(modules, dependencies, "Lib" to LIB_C.replace("Int foo() = 1;", "String foo() = \"1\";"))
        assertThat(signature.compiled).containsExactly("Lib", "Use")
        assertThat(signature.errors("Use")).isNotEmpty()

        val property = edit(modules, dependencies, "Lib" to LIB_C.replace("Int size = 1;", "String size = \"1\";"))
        assertThat(property.compiled).containsExactly("Lib", "Use")
        assertThat(property.errors("Use")).isNotEmpty()
    }

    @Test
    fun `a renamed parameter and a changed default value compile the modules that call the function`() {
        val lib =
            """
            module Lib {
                static Int other() = 2;

                static Int scaled(Int factor = 1) = factor * 2;
            }
            """.trimIndent()
        val named =
            """
            module Named {
                package lib import Lib;

                Int run() = lib.scaled(factor = 3);
            }
            """.trimIndent()
        val defaulted =
            """
            module Defaulted {
                package lib import Lib;

                Int run() = lib.scaled();
            }
            """.trimIndent()
        val modules = mapOf("Lib" to lib, "Named" to named, "Defaulted" to defaulted, "Far" to FAR)
        val dependencies = mapOf("Named" to setOf("Lib"), "Defaulted" to setOf("Lib"), "Far" to setOf("Lib"))
        val parameter = edit(modules, dependencies, "Lib" to lib.replace("Int factor = 1) = factor * 2", "Int by = 1) = by * 2"))
        assertThat(parameter.compiled).containsExactly("Lib", "Defaulted", "Named")
        assertThat(parameter.errors("Named")).isNotEmpty()

        val value = edit(modules, dependencies, "Lib" to lib.replace("Int factor = 1", "Int factor = 2"))
        assertThat(value.compiled).containsExactly("Lib", "Defaulted", "Named")
    }

    @Test
    fun `a parameter that moves compiles the module whose named argument goes to it, and not one that only calls its function`() {
        val lib =
            """
            module Lib {
                static Int other() = 2;

                static Int scaled(Int factor = 1) = factor * 2;
            }
            """.trimIndent()
        val named =
            """
            module Named {
                package lib import Lib;

                Int run() = lib.scaled(factor = 3);
            }
            """.trimIndent()
        val called =
            """
            module Called {
                package lib import Lib;

                Int run() = lib.scaled(3);
            }
            """.trimIndent()
        val outcome =
            edit(
                mapOf("Lib" to lib, "Named" to named, "Called" to called),
                mapOf("Named" to setOf("Lib"), "Called" to setOf("Lib")),
                "Lib" to lib.replace("(Int factor = 1)", "(Int  factor = 1)"),
            )
        assertThat(outcome.compiled).containsExactly("Lib", "Named")
    }

    @Test
    fun `an abstract method added to an interface compiles the module whose class implements it, which writes no name of it`() {
        val lib =
            """
            module Lib {
                static Int other() = 2;

                interface Shape {
                    Int area();
                }
            }
            """.trimIndent()
        val impl =
            """
            module Impl {
                package lib import Lib;

                class Square
                        implements lib.Shape {
                    @Override
                    Int area() = 4;
                }

                static lib.Shape make() = new Square();
            }
            """.trimIndent()
        val outcome =
            edit(
                mapOf("Lib" to lib, "Impl" to impl, "Far" to FAR),
                mapOf("Impl" to setOf("Lib"), "Far" to setOf("Lib")),
                "Lib" to lib.replace("Int area();", "Int area();\n\n        Int perimeter();"),
            )
        assertThat(outcome.compiled).containsExactly("Lib", "Impl")
        assertThat(outcome.errors("Impl")).isNotEmpty()
    }

    @Test
    fun `a value added to an enum compiles the module whose switch over it has no default`() {
        val lib =
            """
            module Lib {
                static Int other() = 2;

                enum Color {
                    Red,
                    Green
                }
            }
            """.trimIndent()
        val switch =
            """
            module Switch {
                package lib import Lib;

                Int run(lib.Color color) {
                    return switch (color) {
                        case Red  : 1;
                        case Green: 2;
                    };
                }
            }
            """.trimIndent()
        val outcome =
            edit(
                mapOf("Lib" to lib, "Switch" to switch, "Far" to FAR),
                mapOf("Switch" to setOf("Lib"), "Far" to setOf("Lib")),
                "Lib" to lib.replace("        Green\n", "        Green,\n        Blue\n"),
            )
        assertThat(outcome.compiled).containsExactly("Lib", "Switch")
        assertThat(outcome.errors("Switch")).isNotEmpty()
    }

    @Test
    fun `an operator added under another name compiles the module that uses the operator, which writes no name of it`() {
        val lib =
            """
            module Lib {
                static Int other() = 2;

                const Money(Int cents) {
                    @Op("+") Money add(Money more) = new Money(cents + more.cents);
                }
            }
            """.trimIndent()
        val sum =
            """
            module Sum {
                package lib import Lib;

                lib.Money total(lib.Money a, lib.Money b) = a + b;
            }
            """.trimIndent()
        val outcome =
            edit(
                mapOf("Lib" to lib, "Sum" to sum, "Far" to FAR),
                mapOf("Sum" to setOf("Lib"), "Far" to setOf("Lib")),
                "Lib" to lib.replace("cents + more.cents);", "cents + more.cents);\n\n        @Op(\"+\") Money plus(Money more) = this;"),
            )
        assertThat(outcome.compiled).containsExactly("Lib", "Sum")
    }

    @Test
    fun `a typedef given another type compiles the module that names it`() {
        val lib =
            """
            module Lib {
                static Int other() = 2;

                typedef Int as Count;
            }
            """.trimIndent()
        val half =
            """
            module Half {
                package lib import Lib;

                lib.Count half(lib.Count count) = count / 2;
            }
            """.trimIndent()
        val outcome =
            edit(
                mapOf("Lib" to lib, "Half" to half, "Far" to FAR),
                mapOf("Half" to setOf("Lib"), "Far" to setOf("Lib")),
                "Lib" to lib.replace("typedef Int as Count;", "typedef String as Count;"),
            )
        assertThat(outcome.compiled).containsExactly("Lib", "Half")
        assertThat(outcome.errors("Half")).isNotEmpty()
    }

    @Test
    fun `a class made abstract compiles the module that instantiates it`() {
        val lib =
            """
            module Lib {
                static Int other() = 2;

                class Box {
                    Int size() = 1;
                }
            }
            """.trimIndent()
        val make =
            """
            module Make {
                package lib import Lib;

                Int run() = new lib.Box().size();
            }
            """.trimIndent()
        val outcome =
            edit(
                mapOf("Lib" to lib, "Make" to make, "Far" to FAR),
                mapOf("Make" to setOf("Lib"), "Far" to setOf("Lib")),
                "Lib" to lib.replace("Int size() = 1;", "Int size() = 1;\n\n        @Abstract Int extra();"),
            )
        assertThat(outcome.compiled).containsExactly("Lib", "Make")
        assertThat(outcome.errors("Make")).isNotEmpty()
    }

    @Test
    fun `a member added to a base class compiles the module whose class extends a subclass of it`() {
        // inside Mine, helper() binds to the module's function, and to the base class's method once it has one
        val ext =
            """
            module Ext {
                package lib import Lib;

                static Int helper() = 1;

                class Mine
                        extends lib.Sub {
                    Int run() = helper();
                }
            }
            """.trimIndent()
        val outcome =
            edit(
                mapOf("Lib" to BASE, "Ext" to ext, "Far" to FAR),
                mapOf("Ext" to setOf("Lib"), "Far" to setOf("Lib")),
                "Lib" to BASE.replace("        Int baz() = 0;\n", "        Int baz() = 0;\n\n        Int helper() = 2;\n"),
            )
        assertThat(outcome.compiled).containsExactly("Lib", "Ext")
    }

    @Test
    fun `a member removed from a base class compiles the module whose class takes it through a subclass, which writes no name of it`() {
        // Mine implements HasBaz with the baz it takes from Base, through Sub
        val ext =
            """
            module Ext {
                package lib import Lib;
                package iface import Iface;

                class Mine
                        extends lib.Sub
                        implements iface.HasBaz {
                }

                static iface.HasBaz make() = new Mine();
            }
            """.trimIndent()
        val outcome =
            edit(
                mapOf("Lib" to BASE, "Iface" to HAS_BAZ, "Ext" to ext, "Far" to FAR),
                mapOf("Ext" to setOf("Lib", "Iface"), "Far" to setOf("Lib")),
                "Lib" to BASE.replace("        Int baz() = 0;\n", ""),
            )
        assertThat(outcome.compiled).containsExactly("Lib", "Ext")
        assertThat(outcome.errors("Ext")).isNotEmpty()
    }

    @Test
    fun `a member removed from a base class compiles the module that matches a subclass of it with an interface`() {
        // Sub matches HasBaz with the baz it takes from Base; Duck names Sub, but neither Base nor baz
        val duck =
            """
            module Duck {
                package lib import Lib;
                package iface import Iface;

                Int run() {
                    iface.HasBaz h = new lib.Sub();
                    return 0;
                }
            }
            """.trimIndent()
        val outcome =
            edit(
                mapOf("Lib" to BASE, "Iface" to HAS_BAZ, "Duck" to duck, "Far" to FAR),
                mapOf("Duck" to setOf("Lib", "Iface"), "Far" to setOf("Lib")),
                "Lib" to BASE.replace("        Int baz() = 0;\n", ""),
            )
        assertThat(outcome.compiled).containsExactly("Lib", "Duck")
        assertThat(outcome.errors("Duck")).isNotEmpty()
    }

    @Test
    fun `a class that stops being an R compiles the module that matches a class whose method returns it with an interface`() {
        // C matches HasR while its get returns an R, which RImpl is: matching C checks RImpl, which Deep names nowhere,
        // nor get, nor x; only that C's get names RImpl shows that the edit reaches Deep
        val iface =
            """
            module Iface {
                interface R {
                    Int x();
                }

                interface HasR {
                    R get();
                }
            }
            """.trimIndent()
        val lib =
            """
            module Lib {
                package iface import Iface;

                static Int other() = 2;

                class C {
                    RImpl get() = new RImpl();
                }

                class RImpl
                        implements iface.R {
                    @Override
                    Int x() = 1;
                }
            }
            """.trimIndent()
        val deep =
            """
            module Deep {
                package lib import Lib;
                package iface import Iface;

                Int run() {
                    iface.HasR h = new lib.C();
                    return 0;
                }
            }
            """.trimIndent()
        val outcome =
            edit(
                mapOf("Iface" to iface, "Lib" to lib, "Deep" to deep, "Far" to FAR),
                mapOf("Lib" to setOf("Iface"), "Deep" to setOf("Lib", "Iface"), "Far" to setOf("Lib")),
                "Lib" to
                    lib.replace(
                        "    class RImpl\n            implements iface.R {\n        @Override\n        Int x() = 1;",
                        "    class RImpl {\n        Int y() = 1;",
                    ),
            )
        assertThat(outcome.before.errors["Deep"].orEmpty()).noneMatch { it.startsWith("ERROR") }
        assertThat(outcome.compiled).containsExactly("Lib", "Deep")
        assertThat(outcome.errors("Deep")).isNotEmpty()
    }

    @Test
    fun `a method renamed in a virtual child compiles the module that matches the child, which a method returns, with an interface`() {
        // Duck's constants name Base and its make, whose type is Base's virtual child, named by Base and its name alone
        val duck =
            """
            module Duck {
                package lib import Lib;
                package iface import Iface;

                Int run() {
                    iface.HasFoo h = new lib.Base().make();
                    return 0;
                }
            }
            """.trimIndent()
        assertFooRenameBreaksDuck(CHILD, duck)
    }

    @Test
    fun `a method renamed in a virtual child compiles the module that names the child's type and matches it with an interface`() {
        val duck =
            """
            module Duck {
                package lib import Lib;
                package iface import Iface;

                Int run(lib.Base.Child child) {
                    iface.HasFoo h = child;
                    return 0;
                }
            }
            """.trimIndent()
        assertFooRenameBreaksDuck(CHILD, duck)
    }

    @Test
    fun `a method renamed in a virtual child compiles the module that matches the child, which a property holds, with an interface`() {
        val lib = CHILD.replace("        Child make() = new Child();", "        Child child.get() = new Child();")
        val duck =
            """
            module Duck {
                package lib import Lib;
                package iface import Iface;

                Int run() {
                    iface.HasFoo h = new lib.Base().child;
                    return 0;
                }
            }
            """.trimIndent()
        assertFooRenameBreaksDuck(lib, duck)
    }

    @Test
    fun `a method renamed in a virtual child compiles the module that matches another module's override of the child with an interface`() {
        // Sub's Child overrides Base's, and takes its foo; Duck names Sub and its make, neither Base nor its Child
        val mid =
            """
            module Mid {
                package lib import Lib;

                class Sub
                        extends lib.Base {
                    @Override
                    class Child {
                        Int bar() = 2;
                    }

                    @Override
                    Child make() = new Child();
                }
            }
            """.trimIndent()
        val duck =
            """
            module Duck {
                package mid import Mid;
                package iface import Iface;

                Int run() {
                    iface.HasFoo h = new mid.Sub().make();
                    return 0;
                }
            }
            """.trimIndent()
        assertFooRenameBreaksDuck(CHILD, duck, mid)
    }

    @Test
    fun `a method renamed in a mixin's virtual child compiles the module that matches it through a class that incorporates the mixin`() {
        val lib =
            """
            module Lib {
                static Int other() = 2;

                mixin Maker
                        into Object {
                    class Child {
                        Int foo() = 1;
                    }

                    Child make() = new Child();
                }

                class C
                        incorporates Maker {
                }
            }
            """.trimIndent()
        val duck =
            """
            module Duck {
                package lib import Lib;
                package iface import Iface;

                Int run() {
                    iface.HasFoo h = new lib.C().make();
                    return 0;
                }
            }
            """.trimIndent()
        assertFooRenameBreaksDuck(lib, duck)
    }

    @Test
    fun `a method renamed in a virtual child of a virtual child compiles the module that matches it through another module's override`() {
        // Sub overrides Base's Middle, whose Leaf it takes; Duck names Sub and the methods, neither Base nor Leaf
        val lib =
            """
            module Lib {
                static Int other() = 2;

                class Base {
                    class Middle {
                        class Leaf {
                            Int foo() = 1;
                        }

                        Leaf leaf() = new Leaf();
                    }
                }
            }
            """.trimIndent()
        val mid =
            """
            module Mid {
                package lib import Lib;

                class Sub
                        extends lib.Base {
                    @Override
                    class Middle {
                        Int bar() = 2;
                    }

                    Middle middle() = new Middle();
                }
            }
            """.trimIndent()
        val duck =
            """
            module Duck {
                package mid import Mid;
                package iface import Iface;

                Int run() {
                    iface.HasFoo h = new mid.Sub().middle().leaf();
                    return 0;
                }
            }
            """.trimIndent()
        assertFooRenameBreaksDuck(lib, duck, mid)
    }

    @Test
    fun `a method renamed in a virtual child of a parameterized class compiles the module that matches the child with an interface`() {
        val lib =
            """
            module Lib {
                static Int other() = 2;

                class Outer<Element> {
                    class Child {
                        Int foo() = 1;
                    }

                    Child make() = new Child();
                }
            }
            """.trimIndent()
        val duck =
            """
            module Duck {
                package lib import Lib;
                package iface import Iface;

                Int run() {
                    iface.HasFoo h = new lib.Outer<String>().make();
                    return 0;
                }
            }
            """.trimIndent()
        assertFooRenameBreaksDuck(lib, duck)
    }

    @Test
    fun `a method renamed in a virtual child compiles the module that matches it through another module's typedef of the child`() {
        // Duck names Mid's make alone, whose type is Mid's typedef of Base's Child
        val mid =
            """
            module Mid {
                package lib import Lib;

                typedef lib.Base.Child as Kid;

                static Kid make() = new lib.Base().make();
            }
            """.trimIndent()
        val duck =
            """
            module Duck {
                package mid import Mid;
                package iface import Iface;

                Int run() {
                    iface.HasFoo h = mid.make();
                    return 0;
                }
            }
            """.trimIndent()
        assertFooRenameBreaksDuck(CHILD, duck, mid)
    }

    @Test
    fun `a member removed from a class compiles the module that matches the class with its own interface`() {
        val own =
            """
            module Own {
                package lib import Lib;

                interface HasFoo {
                    Int foo();
                }

                Int run() {
                    HasFoo h = lib.make();
                    return 0;
                }
            }
            """.trimIndent()
        val outcome =
            edit(
                mapOf("Lib" to LIB, "Own" to own, "Far" to FAR),
                mapOf("Own" to setOf("Lib"), "Far" to setOf("Lib")),
                "Lib" to LIB.replace("        Int foo() = 1;\n", ""),
            )
        assertThat(outcome.compiled).containsExactly("Lib", "Own")
        assertThat(outcome.errors("Own")).isNotEmpty()
    }

    @Test
    fun `a class added with a name that a module writes, which did not compile, compiles that module, which now does`() {
        val capture =
            """
            module Capture {
                package lib import Lib;

                Int run() {
                    lib.Missing m = new lib.Missing();
                    return 0;
                }
            }
            """.trimIndent()
        val outcome =
            edit(
                mapOf("Lib" to LIB, "Capture" to capture, "Far" to FAR),
                mapOf("Capture" to setOf("Lib"), "Far" to setOf("Lib")),
                "Lib" to LIB.replace("    static Int other() = 2;", "    static Int other() = 2;\n\n    class Missing {\n    }"),
            )
        assertThat(outcome.compiled).containsExactly("Lib", "Capture")
        assertThat(outcome.before.errors["Capture"].orEmpty()).isNotEmpty()
        assertThat(outcome.errors("Capture")).isEmpty()
    }

    @Test
    fun `an edit in a method body, of a comment, or adding a private method to a class that others use compiles the edited module alone`() {
        val use =
            """
            module Use {
                package lib import Lib;

                Int run() = new lib.C().foo() + lib.other();
            }
            """.trimIndent()
        val modules = mapOf("Lib" to LIB_C, "Use" to use, "Far" to FAR)
        val dependencies = mapOf("Use" to setOf("Lib"), "Far" to setOf("Lib"))
        val body =
            edit(modules, dependencies, "Lib" to LIB_C.replace("Int foo() = 1;", "Int foo() {\n            return 1 + 0;\n        }"))
        assertThat(body.compiled).containsExactly("Lib")
        val comment = edit(modules, dependencies, "Lib" to LIB_C.replace("// the class", "// the one class"))
        assertThat(comment.compiled).containsExactly("Lib")
        val hidden =
            edit(
                modules,
                dependencies,
                "Lib" to LIB_C.replace("Int more() = 2;", "Int more() = 2;\n\n        private Int hidden() = 3;"),
            )
        assertThat(hidden.compiled).containsExactly("Lib")
    }

    @Test
    fun `a private method added to a class compiles the module whose class extends it`() {
        val use =
            """
            module Use {
                package lib import Lib;

                Int run() = new lib.C().foo();
            }
            """.trimIndent()
        val ext =
            """
            module Ext {
                package lib import Lib;

                class Mine
                        extends lib.C {
                }
            }
            """.trimIndent()
        val outcome =
            edit(
                mapOf("Lib" to LIB_C, "Use" to use, "Ext" to ext),
                mapOf("Use" to setOf("Lib"), "Ext" to setOf("Lib")),
                "Lib" to LIB_C.replace("Int more() = 2;", "Int more() = 2;\n\n        private Int hidden() = 3;"),
            )
        assertThat(outcome.compiled).containsExactly("Lib", "Ext")
    }

    @Test
    fun `lines inserted above declarations compile the modules whose facts name a declaration they move`() {
        val lib =
            """
            module Lib {
                class A {
                    Int a() = 1;
                }

                class C {
                    Int foo() = 1;
                }
            }
            """.trimIndent()
        val above =
            """
            module Above {
                package lib import Lib;

                Int run() = new lib.A().a();
            }
            """.trimIndent()
        val below =
            """
            module Below {
                package lib import Lib;

                Int run() = new lib.C().foo();
            }
            """.trimIndent()
        val outcome =
            edit(
                mapOf("Lib" to lib, "Above" to above, "Below" to below),
                mapOf("Above" to setOf("Lib"), "Below" to setOf("Lib")),
                "Lib" to lib.replace("    class C {", "    // a comment\n    // and another\n    class C {"),
            )
        assertThat(outcome.compiled).containsExactly("Lib", "Below")
    }

    @Test
    fun `a rename on a line that holds another declaration compiles the module that names the other one, which moves`() {
        val lib =
            """
            module Lib {
                static Int other() = 2;

                class C { Int foo() = 1; } class E { Int baz() = 2; }
            }
            """.trimIndent()
        val pos =
            """
            module Pos {
                package lib import Lib;

                Int run() = new lib.E().baz();
            }
            """.trimIndent()
        val outcome =
            edit(
                mapOf("Lib" to lib, "Pos" to pos, "Far" to FAR),
                mapOf("Pos" to setOf("Lib"), "Far" to setOf("Lib")),
                "Lib" to lib.replace("Int foo()", "Int first()"),
            )
        assertThat(outcome.compiled).containsExactly("Lib", "Pos")
    }

    @Test
    fun `a module that compares consts of the edited module, with an equals the compiler adds, keeps its compile after a body edit`() {
        val lib =
            """
            module Lib {
                static Int other() = 2;

                const Point(Int x, Int y);
            }
            """.trimIndent()
        // the const's equals, which the compiler adds, has no declaration of its own
        val eq =
            """
            module Eq {
                package lib import Lib;

                Boolean same(lib.Point a, lib.Point b) = a == b;
            }
            """.trimIndent()
        val outcome =
            edit(
                mapOf("Lib" to lib, "Eq" to eq, "Far" to FAR),
                mapOf("Eq" to setOf("Lib"), "Far" to setOf("Lib")),
                "Lib" to lib.replace("static Int other() = 2;", "static Int other() = 3;"),
            )
        assertThat(outcome.compiled).containsExactly("Lib")
    }

    @Test
    fun `a module that reaches a class only through a property's type is compiled when the class changes`() {
        val holder =
            """
            module Holder {
                package lib import Lib;

                class Box {
                    lib.C c = new lib.C();
                }
            }
            """.trimIndent()
        val through =
            """
            module Through {
                package holder import Holder;
                package iface import Iface;

                Int run() {
                    iface.HasFoo h = new holder.Box().c;
                    return 0;
                }
            }
            """.trimIndent()
        val outcome =
            edit(
                mapOf("Lib" to LIB, "Iface" to HAS_FOO, "Holder" to holder, "Through" to through),
                mapOf("Holder" to setOf("Lib"), "Through" to setOf("Holder", "Iface")),
                "Lib" to LIB.replace("Int foo() = 1;", "Int renamed() = 1;"),
            )
        assertThat(outcome.compiled).containsExactly("Lib", "Holder", "Through")
        assertThat(outcome.errors("Through")).isNotEmpty()
    }

    /** What an edit compiled, by module name in build order, and the compiles before it and, from nothing, after it. */
    private class Outcome(
        val compiled: List<String>,
        val before: XdkCodeActionCache.Base,
        val after: XdkCodeActionCache.Base,
    ) {
        fun errors(module: String): List<String> = after.errors[module].orEmpty().filter { it.startsWith("ERROR") }
    }

    /**
     * Renames foo in [lib], which Far imports and matches nothing in, and checks that the edit compiles Lib, then [mid], which
     * imports Lib, if there is one, and [duck], which imports Iface and Mid or Lib, alone, and that Duck no longer compiles.
     */
    private fun assertFooRenameBreaksDuck(
        lib: String,
        duck: String,
        mid: String? = null,
    ) {
        val outcome =
            edit(
                buildMap {
                    put("Lib", lib)
                    mid?.let { put("Mid", it) }
                    putAll(mapOf("Iface" to HAS_FOO, "Duck" to duck, "Far" to FAR))
                },
                if (mid == null) {
                    mapOf("Duck" to setOf("Lib", "Iface"), "Far" to setOf("Lib"))
                } else {
                    mapOf("Mid" to setOf("Lib"), "Duck" to setOf("Mid", "Iface"), "Far" to setOf("Lib"))
                },
                "Lib" to lib.replace("Int foo() = 1;", "Int renamed() = 1;"),
            )
        assertThat(outcome.compiled).containsExactlyElementsOf(listOfNotNull("Lib", mid?.let { "Mid" }, "Duck"))
        assertThat(outcome.errors("Duck")).isNotEmpty()
    }

    /**
     * Compiles [modules], by name, each depending on those that [dependencies] gives it, then [edit]s one module's source
     * and compiles them as the next revision does, from the first compile, and as a proof does; and asserts that each
     * module has the facts, artifact and errors that compiling every module from nothing gives it.
     */
    private fun edit(
        modules: Map<String, String>,
        dependencies: Map<String, Set<String>>,
        edit: Pair<String, String>,
    ): Outcome {
        CompilerTestSupport.configure()
        val root =
            directory
                .toRealPath()
                .toFile()
                .resolve("case${cases++}")
                .apply { mkdirs() }
        val files = modules.mapValues { (module, text) -> root.resolve("$module.x").apply { writeText(text + "\n") } }
        val uris = files.mapValues { it.value.toURI().toString() }
        val compiled = CopyOnWriteArrayList<String>()

        fun queries() =
            XdkProjectQueries(
                XdkProject(modules.keys.map { XdkSourceModule(it, uris.getValue(it), dependencies[it].orEmpty()) }),
                emptyMap(),
                XdkDependencies(emptyList()),
                { sources, repository, errors ->
                    EmbeddingSupport.instance().compileModule(sources, repository, errors).also { compiled += sources.qualifiedModuleName }
                },
                { false },
            )
        val first = queries()
        val before = requireNotNull(first.revisionCompile())
        val text = edit.second + "\n"
        val proposed = first.sourceTexts() + (files.getValue(edit.first).path to text)

        files.getValue(edit.first).writeText(text)
        compiled.clear()
        val tracked = requireNotNull(queries().revisionCompile(before))
        val tracking = compiled.toList()
        val scratch = requireNotNull(queries().revisionCompile())
        modules.keys.forEach { module -> assertSame(module, uris.getValue(module), tracked, scratch, "the next revision's compile") }

        // a proof's compile of the same edit, from the revision's, which fails where a module does not compile
        compiled.clear()
        val proof = first.proofCompile(proposed, before)
        val proving = compiled.toList()
        if (scratch.errors.values
                .flatten()
                .none { it.startsWith("ERROR") }
        ) {
            assertThat(proof).describedAs("a proof's compile of an edit that compiles").isNotNull()
            assertThat(proving).describedAs("the modules a proof compiles").isEqualTo(tracking)
            modules.keys.filter { it !in proving }.forEach { module ->
                assertSame(module, uris.getValue(module), requireNotNull(proof), scratch, "a proof's compile")
            }
        }
        return Outcome(tracking, before, scratch)
    }

    /** Asserts that [module] has the same facts, artifact, errors and missing-method destinations in [actual] as in [expected]. */
    private fun assertSame(
        module: String,
        uri: String,
        actual: XdkCodeActionCache.Base,
        expected: XdkCodeActionCache.Base,
        what: String,
    ) {
        assertThat(factsText(actual.facts.module(uri)))
            .describedAs("$module's facts after $what")
            .isEqualTo(factsText(expected.facts.module(uri)))
        assertThat(actual.artifacts[module]?.revision)
            .describedAs("$module's artifact after $what")
            .isEqualTo(expected.artifacts[module]?.revision)
        assertThat(actual.errors[module]).describedAs("$module's errors after $what").isEqualTo(expected.errors[module])
        assertThat(actual.destinations[module].toString())
            .describedAs("$module's missing-method destinations after $what")
            .isEqualTo(expected.destinations[module].toString())
    }

    private companion object {
        /** Foo of C, which make returns, and a function of the module. */
        val LIB =
            """
            module Lib {
                static Int other() = 2;

                class C {
                    Int foo() = 1;
                }

                static C make() = new C();
            }
            """.trimIndent()

        /** A class with a virtual child, which a method makes, and a function of the module. */
        val CHILD =
            """
            module Lib {
                static Int other() = 2;

                class Base {
                    class Child {
                        Int foo() = 1;
                    }

                    Child make() = new Child();
                }
            }
            """.trimIndent()

        /** A class with two methods and a property, below a comment. */
        val LIB_C =
            """
            module Lib {
                static Int other() = 2;

                // the class
                class C {
                    Int foo() = 1;

                    Int more() = 2;

                    Int size = 1;
                }
            }
            """.trimIndent()

        /** A class with a method, which a subclass takes; the base class last, so that an edit of it moves nothing else. */
        val BASE =
            """
            module Lib {
                static Int other() = 2;

                class Sub
                        extends Base {
                }

                class Base {
                    Int baz() = 0;
                }
            }
            """.trimIndent()

        /** A module that uses Lib's function alone. */
        val FAR =
            """
            module Far {
                package lib import Lib;

                Int run() = lib.other();
            }
            """.trimIndent()

        val HAS_FOO =
            """
            module Iface {
                interface HasFoo {
                    Int foo();
                }
            }
            """.trimIndent()

        val HAS_BAZ =
            """
            module Iface {
                interface HasBaz {
                    Int baz();
                }
            }
            """.trimIndent()
    }
}
