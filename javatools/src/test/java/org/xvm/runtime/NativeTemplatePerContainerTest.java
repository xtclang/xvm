package org.xvm.runtime;


import java.io.IOException;

import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.FieldInstruction;

import java.lang.reflect.AccessFlag;

import java.net.URISyntaxException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import java.io.File;

import java.util.Objects;

import java.util.concurrent.CancellationException;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Constants;
import org.xvm.asm.DirRepository;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.template._native.reflect.xRTType;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.numbers.xInt64;
import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xObject;
import org.xvm.runtime.template.xService;
import org.xvm.runtime.template._native.collections.arrays.xRTDelegate;
import org.xvm.runtime.template._native.reflect.xRTFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Coverage for replacing the per-template {@code INSTANCE} statics with the container-owned table.
 *
 * <p>The first two tests read javatools' own compiled classes - not XTC modules, and nothing from the XDK.
 * {@code compileJava} is upstream of {@code test}, so those class files exist by construction; that
 * is what lets these run everywhere without an assumption, unlike anything that needs a built
 * distribution.</p>
 *
 * <p>Reading the class files rather than the source is the point of the exercise. A reviewer of a
 * change spread over 139 sites can read a handful, recognise the pattern, and have the scan
 * guarantee the rest did not deviate - including a site that kept the old shape under a different
 * spelling, which reading text would miss.</p>
 */
public class NativeTemplatePerContainerTest {
    /**
     * No native template declares an {@code INSTANCE} static, and no code in the template tree
     * reads one. Together those are the invariant the change establishes: a template is reached
     * through its container's table, never through a JVM-global field.
     *
     * <p>Deliberately narrow. The template tree still holds other mutable statics, some of them
     * templates under different names ({@code xPackage.LIST_MAP_TEMPLATE},
     * {@code xEnum.RANGE_TEMPLATE}, {@code xArray.MUTABILITY} among them). They are the same shape
     * and worth converting, but they are not what this change touched, and a test should pin what
     * was done rather than fail on work nobody claimed to have finished.</p>
     */
    @Test
    public void theTemplateTreeIsFreeOfInstanceStatics() throws IOException, URISyntaxException {
        var listDeclared = new ArrayList<String>();
        var listRead     = new ArrayList<String>();

        int cScanned = forEachTemplateClassFile((sClass, abBytes) -> {
            var model = ClassFile.of().parse(abBytes);

            model.fields().forEach(field -> {
                if (field.flags().has(AccessFlag.STATIC)
                        && "INSTANCE".equals(field.fieldName().stringValue())) {
                    listDeclared.add(sClass);
                }
            });

            model.methods().forEach(method ->
                method.code().ifPresent(code -> code.elementList().stream()
                        .filter(FieldInstruction.class::isInstance)
                        .map(FieldInstruction.class::cast)
                        .filter(field -> "INSTANCE".equals(field.name().stringValue()))
                        .forEach(field -> listRead.add(
                                sClass + '.' + method.methodName().stringValue()))));
        });

        assertTrue(cScanned > 100,
                "expected the whole template tree; only " + cScanned + " classes were scanned");
        assertEquals(List.of(), listDeclared,
                "native templates must not publish themselves through an INSTANCE static");
        assertEquals(List.of(), listRead,
                "these sites still reach a template through a static INSTANCE field");
    }

    /**
     * The class-to-component-name rule the container's table resolves through - the mechanism that
     * replaced the statics, so the thing all 139 sites now depend on.
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
                    Utils.translate(new CancellationException());
            assertNotNull(hEx, "a native throwable must translate without a frame");

            TypeConstant type = container.getTemplate("Object").getCanonicalType();
            assertNotNull(xRTType.makeForeignHandle(container, type),
                    "a foreign type handle must be creatable");
        } finally {
            runtime.shutdownXVM();
        }
    }

    // ----- helpers -----------------------------------------------------------------------------

    @FunctionalInterface
    private interface ClassFileVisitor {
        void accept(String className, byte[] bytes);
    }

    /**
     * Visit every compiled native template class.
     *
     * <p>Failures here are assertions rather than assumptions on purpose: if the classes cannot be
     * scanned the test has proved nothing, and saying so quietly is how a suite comes to report a
     * coverage it does not have.</p>
     *
     * @return the number of classes scanned
     */
    private static int forEachTemplateClassFile(ClassFileVisitor visitor)
            throws IOException, URISyntaxException {
        Path pathAnchor = Path.of(xObject.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        assertTrue(Files.isDirectory(pathAnchor),
                "the templates must be scannable as exploded classes, but the code source is "
                        + pathAnchor);

        Path pathTemplates = pathAnchor.resolve("org/xvm/runtime/template");
        assertTrue(Files.isDirectory(pathTemplates),
                "the compiled template tree is missing at " + pathTemplates);

        int cScanned = 0;
        try (Stream<Path> files = Files.walk(pathTemplates)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                visitor.accept(pathAnchor.relativize(path).toString(), Files.readAllBytes(path));
                cScanned++;
            }
        }
        return cScanned;
    }
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
