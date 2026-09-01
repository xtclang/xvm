package org.xvm.runtime;


import java.io.File;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Objects;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Constants;
import org.xvm.asm.DirRepository;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.template._native.reflect.xRTType;

import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xObject;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.collections.xArray;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xString;

import org.xvm.runtime.template._native.collections.arrays.xRTDelegate;

import org.xvm.runtime.template._native.reflect.xRTFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * A native template must belong to the container that asks for it.
 *
 * <p>Native templates are built per container: {@link NativeContainer} constructs a fresh set for
 * every instance, and a single JVM can build several (the interpreter connector does exactly that).
 * The templates therefore carry an owner - {@link ClassTemplate#f_container} - and every lookup
 * has to resolve to the asking container's copy.</p>
 *
 * <p>These tests build two containers <em>in series</em>, with no concurrency at all, and check
 * that the earlier container still resolves its own templates after the later one exists.</p>
 */
public class NativeTemplatePerContainerTest {
    /**
     * Baseline: two containers built in series own two disjoint sets of templates, and the first
     * container stays live and keeps resolving its own templates by name.
     */
    @Test
    public void containersBuiltInSeriesOwnDistinctTemplates() {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        var runtime = new Runtime();
        try {
            var containerA = new NativeContainer(runtime, systemRepository());
            var containerB = new NativeContainer(runtime, systemRepository());

            for (String sName : new String[] {"Object", "Const", "Enum", "Service"}) {
                ClassTemplate templateA = containerA.getTemplate(sName);
                ClassTemplate templateB = containerB.getTemplate(sName);

                assertNotSame(templateA, templateB,
                        () -> "each container builds its own \"" + sName + "\" template");
                assertSame(containerA, templateA.f_container,
                        () -> "container A's \"" + sName + "\" template must be owned by A");
                assertSame(containerB, templateB.f_container,
                        () -> "container B's \"" + sName + "\" template must be owned by B");
            }
        } finally {
            runtime.shutdownXVM();
        }
    }

    /**
     * The defect, in series and fully deterministic.
     *
     * <p>A {@link TypeComposition} reports both the container it belongs to and the template that
     * implements it, and those two must agree - the template a composition hands out is used to
     * build handles, resolve types and pick a constant pool. {@link ProxyComposition} resolves its
     * template through the native template cache, so this asserts the invariant for the container
     * built first while a second container exists.</p>
     *
     * <p>When the cache is a process-global mutable static, the last container to be constructed
     * wins it and the first container is handed a foreign template - and with it a foreign
     * container, pool and type system.</p>
     */
    @Test
    public void aCompositionResolvesTheTemplateOfItsOwnContainer() {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        var runtime = new Runtime();
        try {
            var containerA = new NativeContainer(runtime, systemRepository());
            var containerB = new NativeContainer(runtime, systemRepository());

            assertCompositionOwnsItsTemplate(containerB, "B (built last)");
            assertCompositionOwnsItsTemplate(containerA, "A (built first)");
        } finally {
            runtime.shutdownXVM();
        }
    }

    private static void assertCompositionOwnsItsTemplate(NativeContainer container, String sLabel) {
        ClassTemplate   templateObject = container.getTemplate("Object");
        TypeComposition clzOrigin      = templateObject.getCanonicalClass();

        assertSame(container, clzOrigin.getContainer(),
                () -> "the origin composition must belong to container " + sLabel);

        var clzProxy = new ProxyComposition(clzOrigin, templateObject.getCanonicalType());

        assertSame(container, clzProxy.getContainer(),
                () -> "the proxy composition must belong to container " + sLabel);
        assertSame(container, clzProxy.getTemplate().f_container,
                () -> "container " + sLabel + " must resolve its own Proxy template, but got one"
                        + " owned by a different container");
        assertSame(clzProxy.getTemplate(), clzProxy.getSupport(),
                () -> "template and op-support must be the same object for container " + sLabel);
    }

    /**
     * The table finds most templates by deriving a component name from the template class, so that
     * derivation has to agree with the rule NativeContainer's scanner used to register them:
     * strip the package prefix, then drop the leading 'x' from the simple name.
     *
     * <p>This is the one stringly-typed step in the lookup, and it needs no container to check.</p>
     */
    @Test
    public void componentNamesFollowTheLoadersRule() {
        assertEquals("Object",            NativeTemplates.componentNameOf(xObject.class));
        assertEquals("Enum",              NativeTemplates.componentNameOf(xEnum.class));
        assertEquals("Const",             NativeTemplates.componentNameOf(xConst.class));
        assertEquals("Service",           NativeTemplates.componentNameOf(xService.class));
        assertEquals("numbers.Int64",     NativeTemplates.componentNameOf(xInt64.class));
        assertEquals("text.String",       NativeTemplates.componentNameOf(xString.class));
        assertEquals("collections.Array", NativeTemplates.componentNameOf(xArray.class));
        assertEquals("_native.reflect.RTFunction",
                NativeTemplates.componentNameOf(xRTFunction.class));
        assertEquals("_native.collections.arrays.RTDelegate",
                NativeTemplates.componentNameOf(xRTDelegate.class));
    }

    /**
     * Threading a container through the static factories must not break the paths that have no
     * frame and no container to give.
     *
     * <p>{@code Utils.translate} turns a native throwable into an exception handle with a null
     * frame, and a "foreign" type handle is built for a type outside the asking type system.
     * Both used to read a process-global template and so never needed an owner; both now take one
     * from the composition they are building against.</p>
     */
    @Test
    public void ownerlessFactoryPathsStillWork() {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        var runtime = new Runtime();
        try {
            var container = new NativeContainer(runtime, systemRepository());

            ObjectHandle.ExceptionHandle hEx =
                    Utils.translate(new java.util.concurrent.CancellationException());
            assertNotNull(hEx, "a native throwable must translate without a frame");

            TypeConstant type = container.getTemplate("Object").getCanonicalType();
            assertNotNull(xRTType.makeForeignHandle(container, type),
                    "a foreign type handle must be creatable");
        } finally {
            runtime.shutdownXVM();
        }
    }

    // ----- helpers -----------------------------------------------------------------------------

    private static boolean systemModulesAvailable() {
        var repository = systemRepository();
        return repository != null
            && repository.loadModule(Constants.ECSTASY_MODULE) != null
            && repository.loadModule(Constants.TURTLE_MODULE)  != null
            && repository.loadModule(Constants.NATIVE_MODULE)  != null;
    }

    private static ModuleRepository systemRepository() {
        var repositories = Stream.of(
                "lib_ecstasy/build/xtc/main/lib",
                "javatools_bridge/build/xtc/main/lib",
                "xdk/build/install/xdk/lib")
                .map(NativeTemplatePerContainerTest::repositoryFor)
                .filter(Objects::nonNull)
                .toList();

        return switch (repositories.size()) {
        case 0  -> null;
        case 1  -> repositories.getFirst();
        default -> new LinkedRepository(repositories.toArray(ModuleRepository.NO_REPOS));
        };
    }

    private static ModuleRepository repositoryFor(String sPath) {
        File dir = checkoutRoot().resolve(sPath).toFile();
        return dir.isDirectory()
                ? new DirRepository(dir, true)
                : null;
    }

    private static Path checkoutRoot() {
        var path = Path.of("").toAbsolutePath();
        while (path != null) {
            if (Files.isDirectory(path.resolve("javatools"))
                    && Files.isDirectory(path.resolve("lib_ecstasy"))) {
                return path;
            }
            path = path.getParent();
        }
        throw new IllegalStateException("Cannot locate checkout root");
    }
}
