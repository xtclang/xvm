package org.xvm.asm.constants;

import java.io.File;

import java.net.URISyntaxException;
import java.net.URL;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.xvm.api.ModuleCompiler;

import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A class that holds a virtual child implicitly implements {@code ecstasy.reflect.Outer}, whose
 * {@code Inner} is not a super virtual child; a class's own child named {@code Inner} covers it in
 * the class's TypeInfo instead of colliding with it (VERIFY-85). The compiled XDK modules come from
 * the test resources, where the build puts them ({@code testXtcModules} in
 * javatools/build.gradle.kts).
 */
class ImplicitInnerChildTest {
    private static File xdkModules;

    @BeforeAll
    static void configure() throws URISyntaxException {
        URL modules = ImplicitInnerChildTest.class.getResource("/xdk-modules");
        assertNotNull(modules, "the build puts the compiled XDK modules in the test resources");
        xdkModules = new File(modules.toURI());
    }

    @Test
    void aVirtualChildNamedInnerCompiles() {
        compiles("""
                module Located {
                    @Test
                    void top() {
                        assert 1 + 1 == 2;
                    }

                    class Outer {
                        class Inner {
                            @Test
                            void deep() {
                                assert 2 + 2 == 4;
                            }
                        }
                    }

                    void use() {
                        Outer.Inner inner = new Outer().new Inner();
                        inner.deep();
                        Outer outer = inner.outer;
                    }
                }
                """);
    }

    @Test
    void aStaticChildNamedInnerBesideAVirtualChildCompiles() {
        compiles("""
                module Located {
                    class Shelf {
                        static class Inner {
                        }

                        class Drawer {
                        }
                    }
                }
                """);
    }

    @Test
    void aSubclassOverridingAnInheritedInnerCompiles() {
        compiles("""
                module Located {
                    class Shelf {
                        class Inner {
                        }
                    }

                    class Cabinet
                            extends Shelf {
                        @Override
                        class Inner {
                        }
                    }
                }
                """);
    }

    private static void compiles(String source) {
        ErrorList errs = new ErrorList(25);
        var module = new ModuleCompiler(new DirRepository(xdkModules, true)).compile(source, null, errs);
        assertNotNull(module, () -> "compile failed: " + errs.getErrors());
        assertFalse(errs.hasSeriousErrors(), () -> "compile failed: " + errs.getErrors());
    }
}
