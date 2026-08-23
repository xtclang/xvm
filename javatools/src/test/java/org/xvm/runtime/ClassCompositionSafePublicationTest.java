package org.xvm.runtime;


import java.io.File;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Constants;
import org.xvm.asm.DirRepository;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleRepository;

import org.xvm.asm.Constants.Access;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.util.Lazy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * Tests for ClassComposition lazy runtime metadata that carries container-owned handles or
 * owner-pool synthetic methods.
 */
public class ClassCompositionSafePublicationTest {
    /**
     * Field layout, field-name handles, and synthetic structure initializers belong to the
     * inception composition's container. The old access-view clones copied field-layout side fields
     * at construction time and could race separate plain lazy cells. Parallel first access through
     * canonical and protected views must observe one safely published inception-owned cache identity.
     */
    @Test
    public void accessViewsShareSafelyPublishedInceptionRuntimeCaches() throws Exception {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        var runtime = new Runtime();
        try {
            var container = NativeContainer.create(runtime, systemRepository());
            var pool      = container.getConstantPool();
            var clz       = new ClassComposition(container, container.getTemplate("Object"),
                    pool.typeObject());

            var protectedView   = clz.ensureAccess(Access.PROTECTED);
            installSyntheticFieldLayout(clz, pool.typeObject());

            var fieldLayoutCell = finalField(ClassComposition.class, "f_fieldLayout");
            var fieldNamesCell  = finalField(ClassComposition.class, "f_fieldNames");
            var initializerCell = finalField(ClassComposition.class, "f_methodInit");
            var start           = new CountDownLatch(1);
            record Caches(StringHandle[] names, MethodStructure initializer) {}

            try (var executor = Executors.newFixedThreadPool(8)) {
                var futures = IntStream.range(0, 8)
                        .mapToObj(i -> executor.submit(() -> {
                            start.await();
                            var composition = i % 2 == 0 ? clz : protectedView;
                            return new Caches(
                                    composition.getFieldNameArray(),
                                    composition.ensureAutoInitializer());
                        }))
                        .toList();

                start.countDown();

                Caches first = null;
                for (var future : futures) {
                    var caches = future.get(10, TimeUnit.SECONDS);
                    if (first == null) {
                        first = caches;
                    } else {
                        assertSame(first.names(), caches.names());
                        assertSame(first.initializer(), caches.initializer());
                    }
                }

                assertNotNull(first);
                assertEquals(1, first.names().length);
                assertSame(container, first.names()[0].getComposition().getContainer());
                assertSame(clz.getFieldLayout(), protectedView.getFieldLayout());
                assertSame(first.names(), protectedView.getFieldNameArray());
                assertSame(first.initializer(), protectedView.ensureAutoInitializer());
                assertThrows(UnsupportedOperationException.class, () ->
                        clz.getFieldLayout().clear());

                var fieldLayout = (Lazy.Owner<?, ?>) fieldLayoutCell.get(clz);
                assertTrue(fieldLayout.isComputed());

                var fieldNames = (Lazy.Owner<?, ?>) fieldNamesCell.get(clz);
                assertTrue(fieldNames.isComputed());
                assertSame(first.names(), fieldNames.get(clz, StringHandle[].class));

                var initializerLazy = (Lazy.Owner<?, ?>) initializerCell.get(clz);
                assertTrue(initializerLazy.isComputed());
                var initializer = initializerLazy.get(clz, MethodStructure.class);
                assertNotNull(initializer);
                if (first.initializer() != null) {
                    assertSame(first.initializer(), initializer);
                }
            }
        } finally {
            runtime.shutdownXVM();
        }
    }

    /**
     * PropertyComposition struct access is also a runtime view cache. The old plain field could
     * publish duplicate struct view identities under parallel first access. The fixed code uses
     * Lazy.Owner so the cache stays final, owner-derived, and free of constructor-time `this`
     * capture while preserving one lazy struct view per property composition.
     */
    @Test
    public void propertyCompositionStructViewIsOwnerLazyAndShared() throws Exception {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        var runtime = new Runtime();
        try {
            var container = NativeContainer.create(runtime, systemRepository());
            var pool      = container.getConstantPool();
            var clzString = (ClassComposition) container.resolveClass(pool.typeString());
            var infoSize  = pool.typeString().ensureTypeInfo().findProperty("size");

            assertNotNull(infoSize);

            var property       = clzString.ensurePropertyComposition(infoSize);
            var structViewCell = PropertyComposition.class.getDeclaredField("f_structView");
            assertTrue(Modifier.isFinal(structViewCell.getModifiers()), "f_structView");
            assertTrue(Modifier.isFinal(PropertyComposition.class
                    .getDeclaredField("f_fStruct")
                    .getModifiers()), "f_fStruct");
            assertTrue(Modifier.isFinal(PropertyComposition.class
                    .getDeclaredField("f_clzInception")
                    .getModifiers()), "f_clzInception");
            structViewCell.setAccessible(true);

            var start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(8)) {
                var futures = IntStream.range(0, 8)
                        .mapToObj(i -> executor.submit(() -> {
                            start.await();
                            return property.ensureAccess(Access.STRUCT);
                        }))
                        .toList();

                start.countDown();

                PropertyComposition first = null;
                for (var future : futures) {
                    var struct = future.get(10, TimeUnit.SECONDS);
                    if (first == null) {
                        first = struct;
                    } else {
                        assertSame(first, struct);
                    }
                }

                assertNotNull(first);
                assertTrue(first.isStruct());
                assertSame(first, property.ensureAccess(Access.STRUCT));
                assertSame(property, first.ensureAccess(Access.PUBLIC));
                var lazy = (Lazy.Owner<?, ?>) structViewCell.get(property);
                assertTrue(lazy.isComputed());
            }
        } finally {
            runtime.shutdownXVM();
        }
    }

    private static void installSyntheticFieldLayout(ClassComposition clz, TypeConstant type)
            throws Exception {
        var fields = new LinkedHashMap<Object, ClassComposition.FieldInfo>();
        fields.put("value", new ClassComposition.FieldInfo(
                "value", 0, type, null, false, false, false, false));

        setOwnerLazyValue(clz, "f_fieldLayout", newFieldLayout(fields));
    }

    private static Object newFieldLayout(
            LinkedHashMap<Object, ClassComposition.FieldInfo> fields) throws Exception {
        var layoutClass = Stream.of(ClassComposition.class.getDeclaredClasses())
                .filter(clz -> clz.getSimpleName().equals("FieldLayout"))
                .findFirst()
                .orElseThrow();
        var constructor = layoutClass.getDeclaredConstructor(
                Map.class, int.class, boolean.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                Collections.unmodifiableMap(new LinkedHashMap<>(fields)), fields.size(),
                false, false);
    }

    /**
     * Seed a synthetic layout through the final owner-lazy holder so the test can reproduce the
     * old stale access-view copy without depending on whichever fields Object happens to expose.
     */
    @SuppressWarnings("unchecked")
    private static void setOwnerLazyValue(ClassComposition clz, String name, Object value)
            throws Exception {
        var lazy       = (Lazy.Owner<?, ?>) finalField(ClassComposition.class, name).get(clz);
        var ownerField = Lazy.Owner.class.getDeclaredField("owner");
        ownerField.setAccessible(true);
        ownerField.set(lazy, clz);

        var valueRefField = Lazy.Owner.class.getDeclaredField("valueRef");
        valueRefField.setAccessible(true);
        var valueRef = (AtomicReference<Object>) valueRefField.get(lazy);
        valueRef.set(value);
    }

    private static Field finalField(Class<?> clz, String name) throws Exception {
        var field = clz.getDeclaredField(name);
        assertTrue(Modifier.isFinal(field.getModifiers()), name);
        field.setAccessible(true);
        return field;
    }

    private static boolean systemModulesAvailable() {
        var repository = systemRepository();
        return repository != null
            && repository.loadModule(Constants.ECSTASY_MODULE) != null
            && repository.loadModule(Constants.TURTLE_MODULE)  != null
            && repository.loadModule(Constants.NATIVE_MODULE)  != null;
    }

    private static ModuleRepository systemRepository() {
        var manualRepository = repositoryFor("manualTests/build/xtc/xdk/lib");
        if (manualRepository != null) {
            return manualRepository;
        }

        var repositories = Stream.of(
                "lib_ecstasy/build/xtc/main/lib",
                "javatools_bridge/build/xtc/main/lib",
                "xdk/build/install/xdk/lib")
                .map(ClassCompositionSafePublicationTest::repositoryFor)
                .filter(Objects::nonNull)
                .toList();

        return switch (repositories.size()) {
        case 0  -> null;
        case 1  -> repositories.get(0);
        default -> new LinkedRepository(repositories.toArray(ModuleRepository.NO_REPOS));
        };
    }

    private static ModuleRepository repositoryFor(String path) {
        var directory = checkoutFile(path);
        return directory.isDirectory()
                ? new DirRepository(directory, true)
                : null;
    }

    private static File checkoutFile(String path) {
        return checkoutRoot().resolve(path).toFile();
    }

    private static Path checkoutRoot() {
        var path = Path.of("").toAbsolutePath();
        while (path != null) {
            if (Files.isDirectory(path.resolve("javatools")) &&
                    Files.isDirectory(path.resolve("manualTests"))) {
                return path;
            }
            path = path.getParent();
        }
        throw new IllegalStateException("Cannot locate checkout root");
    }
}
