package org.xvm.api;

import java.io.File;
import java.net.URISyntaxException;
import java.util.List;

import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener.ErrorInfo;
import org.xvm.compiler.Compiler;
import org.xvm.util.Severity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A class's contribution arguments, as in {@code extends Base(seed)}, and the default values of its
 * shorthand constructor parameters reach the compiled class only through the synthetic constructor
 * that the shorthand declaration implies. Where explicit constructors stand in its place, the
 * compiler reports them as unused, rather than accept code that it never compiles, unless they
 * match the explicit constructor that takes the synthetic one's place: redundant information that
 * matches is allowed.
 */
class ConstructorArgumentsTest {
    private EmbeddingSupport support;

    @BeforeEach
    void configure() throws URISyntaxException {
        var modules = ConstructorArgumentsTest.class.getResource("/xdk-modules");
        assertNotNull(modules, "the build puts the compiled XDK modules in the test resources");
        support = new EmbeddingSupport().configure(new DirRepository(new File(modules.toURI()), true), null);
    }

    @Test
    void extendsArgumentsBesideExplicitConstructorsAreUnused() {
        // the arguments name nothing: no constructor passes them, so nothing used to compile them
        assertEquals(List.of(Compiler.CONTRIBUTION_PARAMS_UNEXPECTED), errors("""
                module Test {
                    class Base {
                        construct(Int n) {}
                    }
                    class Derived extends Base(noSuchName, alsoMissing) {
                        construct(Int n) {
                            construct Base(n);
                        }
                    }
                }
                """));
    }

    @Test
    void extendsArgumentsWithoutAnExplicitShorthandOrDefaultConstructorAreReported() {
        // the shape of jsondb's JsonLogStoreBase: the names look like the constructor's, but no
        // constructor takes the synthetic one's place, so they name no parameter
        assertEquals(List.of(Compiler.CONTRIBUTION_PARAMS_UNEXPECTED), errors("""
                module Test {
                    class Base {
                        construct(Int n) {}
                    }
                    class Store extends Base(n) {
                        construct(Int n) {
                            construct Base(n);
                        }
                    }
                }
                """));
    }

    @Test
    void contributionArgumentsThatTheExplicitConstructorPassesAreAllowed() {
        // the same parameter, at the same position even if renamed, the same literal, and the
        // same name that is no parameter
        assertEquals(List.of(), diagnostics("""
                module Test {
                    class Base {
                        construct(Int n, String s = "", Boolean flag = False) {}
                    }
                    class SameParameter(Int x, Int y) extends Base(y) {
                        construct(Int x, Int y) {
                            construct Base(y);
                            this.x = x;
                            this.y = y;
                        }
                    }
                    class RenamedParameter(Int x) extends Base(x) {
                        construct(Int n) {
                            construct Base(n);
                            this.x = n;
                        }
                    }
                    class SameLiteralAndName(Int x) extends Base(x, "base", False) {
                        construct(Int x) {
                            construct Base(x, "base", False);
                            this.x = x;
                        }
                    }
                    class Default extends Base(1, "base") {
                        construct() {
                            construct Base(1, "base");
                        }
                    }
                }
                """));
    }

    @Test
    void contributionArgumentsThatDifferFromTheExplicitConstructorAreReported() {
        // another parameter, another literal, a name that is a parameter only of the constructor,
        // and an expression, which never matches
        assertEquals(List.of(Compiler.CONTRIBUTION_PARAMS_UNEXPECTED,
                             Compiler.CONTRIBUTION_PARAMS_UNEXPECTED,
                             Compiler.CONTRIBUTION_PARAMS_UNEXPECTED,
                             Compiler.CONTRIBUTION_PARAMS_UNEXPECTED), errors("""
                module Test {
                    class Base {
                        construct(Int n) {}
                    }
                    class OtherParameter(Int x, Int y) extends Base(x) {
                        construct(Int x, Int y) {
                            construct Base(y);
                            this.x = x;
                            this.y = y;
                        }
                    }
                    class OtherLiteral extends Base(1) {
                        construct() {
                            construct Base(2);
                        }
                    }
                    class ConstructorParameter extends Base(n) {
                        construct(Int n = 0) {
                            construct Base(n);
                        }
                    }
                    class SameExpression(Int x) extends Base(x + 1) {
                        construct(Int x) {
                            construct Base(x + 1);
                            this.x = x;
                        }
                    }
                }
                """));
    }

    @Test
    void shorthandDefaultsThatMatchTheExplicitConstructorAreAllowed() {
        // the shapes of ecstasy's ConsoleAppender and PseudoRandom and xml's Instruction, and the
        // same value spelled differently
        assertEquals(List.of(), diagnostics("""
                module Test {
                    class ConsoleAppender(Console console, Boolean flushAlways = False) {
                        construct(Console console, Boolean flushAlways = False) {
                            this.console     = console;
                            this.flushAlways = flushAlways;
                        }
                        protected Console console;
                        protected Boolean flushAlways;
                    }
                    service PseudoRandom(UInt64 seed = 0) {
                        construct(UInt64 seed = 0) {
                            this.seed = seed == 0 ? 42 : seed;
                        }
                        private UInt64 seed;
                    }
                    class Instruction(String target, String? text = Null) {
                        construct(String target, String? text = Null) {
                            this.target = target;
                            this.text   = text;
                        }
                        construct(Instruction that) {
                            this.target = that.target;
                            this.text   = that.text;
                        }
                        String target;
                        String? text;
                    }
                    class Spelling(Int width = 0x10) {
                        construct(Int width = 16) {
                            this.width = width;
                        }
                        Int width;
                    }
                }
                """));
    }

    @Test
    void shorthandDefaultsThatDifferFromTheExplicitConstructorAreReported() {
        // the class declares the property itself; the explicit constructor has another default,
        // or none
        assertEquals(List.of(Compiler.CONSTRUCTOR_PARAM_DEFAULT_MISMATCH,
                             Compiler.CONSTRUCTOR_PARAM_DEFAULT_MISMATCH), errors("""
                module Test {
                    class Appender(Int width, Boolean flush = True) {
                        construct(Int width, Boolean flush = False) {
                            this.width = width;
                            this.flush = flush;
                        }
                        Int width;
                        Boolean flush;
                    }
                    class Required(Int width = 4) {
                        construct(Int width) {
                            this.width = width;
                        }
                        Int width;
                    }
                }
                """));
    }

    @Test
    void argumentsAndDefaultsThatTheSyntheticConstructorOrAPropertyUsesCompile() {
        assertEquals(List.of(), errors("""
                module Test {
                    class Base {
                        construct(Int n, Boolean flag = False) {}
                    }
                    class Shorthand(Int seed, Boolean flag = False) extends Base(seed, flag) {}
                    class Implicit extends Base(1) {}
                    class Initialized(Int width = 4) {
                        construct(Int width = 4) {
                            this.width = width;
                        }
                    }
                }
                """));
    }

    @Test
    void enumValuesWithExplicitConstructorsHaveNoContributionsToCheck() {
        // an enum value has no compositions, which the check failed on with an internal error; the
        // error that remains is that an enum value needs a no-parameter constructor
        assertEquals(List.of(Compiler.DEFAULT_CONSTRUCTOR_REQUIRED), errors("""
                module Test {
                    enum Color {
                        Red {
                            construct(Int n = 0) {}
                        },
                        Green {
                            construct(Int n) {}
                        }
                    }
                }
                """));
    }

    /**
     * @return the codes of the errors that compiling the module reports
     */
    private List<String> errors(String source) {
        return reports(source, Severity.ERROR);
    }

    /**
     * @return the codes of the errors and warnings that compiling the module reports
     */
    private List<String> diagnostics(String source) {
        return reports(source, Severity.WARNING);
    }

    private List<String> reports(String source, Severity severity) {
        var errs = new ErrorList(25);
        support.compile(source, null, errs);
        return errs.getErrors().stream()
                .filter(error -> error.getSeverity().compareTo(severity) >= 0)
                .map(ErrorInfo::getCode)
                .toList();
    }
}
