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
 * compiler reports them as unused, rather than accept code that it never compiles.
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
    void shorthandDefaultsThatAnExplicitConstructorReplacesAreUnused() {
        // the explicit constructor has its own default, and the class declares the property itself
        assertEquals(List.of(Compiler.CONSTRUCTOR_PARAM_DEFAULT_UNUSED), errors("""
                module Test {
                    class Appender(Int width, Boolean flush = True) {
                        construct(Int width, Boolean flush = False) {
                            this.width = width;
                            this.flush = flush;
                        }
                        Int width;
                        Boolean flush;
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
        var errs = new ErrorList(25);
        support.compile(source, null, errs);
        return errs.getErrors().stream()
                .filter(error -> error.getSeverity().compareTo(Severity.ERROR) >= 0)
                .map(ErrorInfo::getCode)
                .toList();
    }
}
