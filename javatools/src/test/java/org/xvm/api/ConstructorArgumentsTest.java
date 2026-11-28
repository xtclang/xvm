package org.xvm.api;

import java.io.File;
import java.net.URISyntaxException;
import java.util.List;

import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener.ErrorInfo;
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
 * matches is allowed. These are the cases that compile; the ones that must not are negative
 * compiler tests in the XDK's {@code compiler-errors} suite, {@code contributionArguments.x} and
 * {@code constructorDefaults.x}.
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
    void contributionArgumentsOfAClassWithoutConstructorsCompile() {
        // with neither a shorthand declaration nor any constructor, the synthetic default
        // constructor passes the contribution's arguments, so a valid one is no error
        assertEquals(List.of(), diagnostics("""
                module Test {
                    class Base(Int seed) {}
                    class Implicit extends Base(1) {}
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
