package org.xvm.asm;

import java.io.ByteArrayOutputStream;
import java.io.File;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Constants.Access;

import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Return_0;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for read-only XVM structure trees and mutable whole-file copies.
 */
public class XvmStructureTest {
    @Test
    public void testEnsureReadOnlyCascadesAndGuardsMutations() {
        Fixture fixture = createFixture();
        assertTrue(!fixture.file.isReadOnly());
        Parameter[]  params           = fixture.method.getParamArray();
        Annotation[] methodAnnos      = fixture.method.getAnnotations();
        Annotation[] parameterAnnos   = fixture.parameter.getAnnotations();
        Op[]         assembledOps     = fixture.codeMethod.getOps();

        FileStructure readOnly = fixture.file.ensureReadOnly();
        assertSame(fixture.file, readOnly);
        assertSame(fixture.file, fixture.file.ensureReadOnly());

        assertTrue(fixture.file.isReadOnly());
        assertTrue(fixture.pool.isReadOnly());
        assertTrue(fixture.module.isReadOnly());
        assertTrue(fixture.pkg.isReadOnly());
        assertTrue(fixture.clz.isReadOnly());
        assertTrue(fixture.property.isReadOnly());
        assertTrue(fixture.method.isReadOnly());
        assertTrue(fixture.parameter.isReadOnly());
        assertTrue(fixture.constant.isReadOnly());

        assertThrows(IllegalStateException.class, () -> fixture.clz.setSynthetic(true));
        assertFalse(fixture.clz.isSynthetic());

        assertThrows(IllegalStateException.class,
                () -> fixture.property.setInitialValue(fixture.constant));
        assertNull(fixture.property.getInitialValue());

        assertThrows(IllegalStateException.class, fixture.parameter::markDefaultValue);
        assertFalse(fixture.parameter.hasDefaultValue());

        params[0] = null;
        assertSame(fixture.parameter, fixture.method.getParam(0));
        methodAnnos[0] = null;
        assertSame(fixture.annotation, fixture.method.getAnnotation(0));
        parameterAnnos[0] = null;
        assertSame(fixture.annotation, fixture.parameter.getAnnotations()[0]);
        assembledOps[0] = null;
        assertSame(Return_0.INSTANCE, fixture.codeMethod.getOps()[0]);
        assertThrows(UnsupportedOperationException.class,
                () -> fixture.method.getParams().set(0, null));

        assertSame(fixture.constant, fixture.pool.ensureStringConstant("immutable"));
        assertThrows(IllegalStateException.class,
                () -> fixture.pool.ensureStringConstant("new constant"));
        assertThrows(IllegalStateException.class, () -> fixture.file.ensureModule("Other"));
    }

    @Test
    public void testEnsureMutableCopiesEntireFileAndFindsCounterparts()
            throws Exception {
        Fixture fixture = createFixture();

        FileStructure library = new FileStructure("Library");
        fixture.file.merge(library.getModule(), false, false);
        fixture.file.getChild("Library").markEmbedded();
        fixture.file.ensureReadOnly();

        FileStructure fileCopy = fixture.file.ensureMutable();
        assertNotSame(fixture.file, fileCopy);
        assertFalse(fileCopy.isReadOnly());
        assertEquals(moduleNames(fixture.file), moduleNames(fileCopy));

        ModuleStructure moduleCopy = fixture.module.ensureMutable();
        assertNotSame(fixture.module, moduleCopy);
        assertFalse(moduleCopy.isReadOnly());
        assertEquals(moduleNames(fixture.file), moduleNames(moduleCopy.getFileStructure()));

        PackageStructure packageCopy = (PackageStructure) moduleCopy.getChild(fixture.pkg.getName());
        ClassStructure   classCopy   = (ClassStructure) packageCopy.getChild(fixture.clz.getName());
        assertNotSame(fixture.clz, classCopy);
        assertFalse(classCopy.isReadOnly());
        classCopy.setSynthetic(true);
        assertTrue(classCopy.isSynthetic());
        assertFalse(fixture.clz.isSynthetic());

        MethodStructure methodCopy    = classCopy.findMethod(fixture.method.getName(), 1);
        Parameter       parameterCopy = methodCopy.getParam(0);
        assertNotSame(fixture.parameter, parameterCopy);
        assertFalse(parameterCopy.isReadOnly());
        assertTrue(parameterCopy.getContaining() instanceof MethodStructure);
        parameterCopy.markDefaultValue();
        assertTrue(parameterCopy.hasDefaultValue());
        assertFalse(fixture.parameter.hasDefaultValue());

        Annotation methodAnnoCopy = methodCopy.getAnnotation(0);
        methodCopy.getAnnotations()[0] = null;
        assertNull(methodCopy.getAnnotation(0));
        assertSame(fixture.annotation, fixture.method.getAnnotation(0));
        methodCopy.getAnnotations()[0] = methodAnnoCopy;

        Annotation parameterAnnoCopy = parameterCopy.getAnnotations()[0];
        parameterCopy.getAnnotations()[0] = null;
        assertNull(parameterCopy.getAnnotations()[0]);
        assertSame(fixture.annotation, fixture.parameter.getAnnotations()[0]);
        parameterCopy.getAnnotations()[0] = parameterAnnoCopy;

        MethodStructure codeMethodCopy = classCopy.findMethod(fixture.codeMethod.getName(), 0);
        Op              codeOpCopy     = codeMethodCopy.getOps()[0];
        codeMethodCopy.getOps()[0] = null;
        assertNull(codeMethodCopy.getOps()[0]);
        assertSame(Return_0.INSTANCE, fixture.codeMethod.getOps()[0]);
        codeMethodCopy.getOps()[0] = codeOpCopy;

        StringConstant constantCopy = (StringConstant)
                moduleCopy.getConstantPool().getConstant(fixture.constant);
        assertNotSame(fixture.constant, constantCopy);
        assertFalse(constantCopy.isReadOnly());
        assertNotSame(fixture.pool, constantCopy.getConstantPool());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertDoesNotThrow(() -> fixture.file.writeTo(out));
        assertTrue(out.size() > 0);
        assertTrue(fixture.file.isReadOnly());

        assertSame(fileCopy, fileCopy.ensureMutable());
        assertSame(moduleCopy, moduleCopy.ensureMutable());
        assertSame(classCopy, packageCopy.getChild(fixture.clz.getName()));
    }

    @Test
    public void testMutableCopyOwnsContributionInjectionList() {
        FileStructure   file       = new FileStructure("Test");
        ConstantPool    pool       = file.getConstantPool();
        ModuleStructure module     = file.getModule();
        FileStructure   dependencyFile = new FileStructure("Dependency");
        file.merge(dependencyFile.getModule(), false, false);
        ModuleStructure dependency = (ModuleStructure) file.getChild("Dependency");
        dependency.markEmbedded();

        PackageStructure importPackage = module.createPackage(
                Access.PUBLIC, "dependency", null);
        importPackage.setImportedModule(dependency);

        ClassStructure injectorClass = module.createClass(
                Access.PUBLIC, Component.Format.CONST, "Injector", null);
        SingletonConstant injector =
                pool.ensureSingletonConstConstant(injectorClass.getIdentityConstant());
        Component.Injection injection = new Component.Injection(
                injectorClass.getIdentityConstant().getType(),
                pool.ensureStringConstant("resource"));
        List<Component.Injection> injections = new ArrayList<>();
        injections.add(injection);
        importPackage.setImportedModuleInjector(injector, injections);

        file.ensureReadOnly();
        List<Component.Injection> readOnlyInjections = importPackage.getModuleInjections();
        assertThrows(UnsupportedOperationException.class,
                () -> readOnlyInjections.add(injection));

        FileStructure fileCopy = file.ensureMutable();
        PackageStructure importPackageCopy = (PackageStructure)
                fileCopy.getModule().getChild(importPackage.getName());
        Component.Contribution contributionCopy =
                importPackageCopy.findContribution(Component.Composition.Import);
        List<Component.Injection> injectionsCopy = importPackageCopy.getModuleInjections();

        assertSame(importPackageCopy, contributionCopy.getComponent());
        assertNotSame(importPackage, contributionCopy.getComponent());
        injectionsCopy.add(injectionsCopy.get(0));
        assertEquals(2, injectionsCopy.size());
        assertEquals(1, readOnlyInjections.size());
    }

    @Test
    public void testCompositeEnsureMutableRebuildsFromFileCopy() {
        FileStructure   file   = new FileStructure("Test");
        ModuleStructure module = file.getModule();
        PackageStructure first = module.createPackage(Access.PUBLIC, "first", null);
        PackageStructure second = module.createPackage(Access.PUBLIC, "second", null);
        CompositeComponent composite =
                new CompositeComponent(module, List.of(first, second));

        composite.ensureReadOnly();
        CompositeComponent copy = (CompositeComponent) composite.ensureMutable();
        List<Component> siblingCopies = copy.components();

        assertNotSame(composite, copy);
        assertFalse(copy.isReadOnly());
        assertNotSame(module, copy.getContaining());
        assertEquals(2, siblingCopies.size());
        assertNotSame(first, siblingCopies.get(0));
        assertNotSame(second, siblingCopies.get(1));
        assertSame(copy.getContaining(), siblingCopies.get(0).getContaining());
        assertSame(copy.getContaining(), siblingCopies.get(1).getContaining());
    }

    @Test
    public void testOnlyPublicStructuresDeclareCovariantMutableReturns()
            throws Exception {
        Set<Class<? extends XvmStructure>> publicStructures =
                Set.of(FileStructure.class, ModuleStructure.class);

        for (Class<? extends XvmStructure> clz : findStructureClasses()) {
            if (publicStructures.contains(clz)) {
                Method ensureMutable = clz.getDeclaredMethod("ensureMutable");
                assertEquals(clz, ensureMutable.getReturnType(), clz.getName());
                assertTrue(Modifier.isPublic(ensureMutable.getModifiers()), clz.getName());
            } else {
                assertThrows(NoSuchMethodException.class,
                        () -> clz.getDeclaredMethod("ensureMutable"), clz.getName());
            }
        }
    }

    @Test
    public void testOnlyPublicStructuresDeclareCovariantReadOnlyReturns()
            throws Exception {
        Set<Class<? extends XvmStructure>> publicStructures =
                Set.of(FileStructure.class, ModuleStructure.class);

        for (Class<? extends XvmStructure> clz : findStructureClasses()) {
            if (publicStructures.contains(clz)) {
                Method ensureReadOnly = clz.getDeclaredMethod("ensureReadOnly");
                assertEquals(clz, ensureReadOnly.getReturnType(), clz.getName());
                assertTrue(Modifier.isPublic(ensureReadOnly.getModifiers()), clz.getName());
            } else {
                assertThrows(NoSuchMethodException.class,
                        () -> clz.getDeclaredMethod("ensureReadOnly"), clz.getName());
            }
        }
    }

    private static Fixture createFixture() {
        FileStructure   file   = new FileStructure("Test");
        ConstantPool    pool   = file.getConstantPool();
        ModuleStructure module = file.getModule();
        PackageStructure pkg   = module.createPackage(Access.PUBLIC, "example", null);
        ClassStructure clz = pkg.createClass(Access.PUBLIC, Component.Format.CLASS, "Sample", null);

        TypeConstant type = clz.getIdentityConstant().getType();
        PropertyStructure property = clz.createProperty(
                false, Access.PUBLIC, Access.PUBLIC, type, "value");
        Parameter parameter = new Parameter(pool, type, "arg", null, false, 0, false);
        Annotation annotation = pool.ensureAnnotation(clz.getIdentityConstant());
        parameter.addAnnotation(annotation);
        MethodStructure method = clz.createMethod(false, Access.PUBLIC,
                new Annotation[] {annotation}, Parameter.NO_PARAMS,
                "run", new Parameter[] {parameter}, false, false);
        MethodStructure codeMethod = clz.createMethod(false, Access.PUBLIC, null,
                Parameter.NO_PARAMS, "code", Parameter.NO_PARAMS, true, false);
        codeMethod.ensureCode().add(Return_0.INSTANCE);
        StringConstant constant = pool.ensureStringConstant("immutable");

        return new Fixture(file, pool, module, pkg, clz, property, method, parameter, codeMethod,
                annotation, constant);
    }

    private static Set<String> moduleNames(FileStructure file) {
        return file.moduleIds().stream()
                .map(ModuleConstant::getName)
                .collect(Collectors.toSet());
    }

    private static List<Class<? extends XvmStructure>> findStructureClasses()
            throws Exception {
        Path classes = Path.of(XvmStructure.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        try (Stream<Path> paths = Files.walk(classes.resolve("org/xvm/asm"))) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(classes::relativize)
                    .map(Path::toString)
                    .filter(name -> name.endsWith(".class") && !name.contains("$"))
                    .map(name -> name.substring(0, name.length() - ".class".length())
                            .replace(File.separatorChar, '.'))
                    .map(XvmStructureTest::loadClass)
                    .filter(clz -> clz != XvmStructure.class
                            && XvmStructure.class.isAssignableFrom(clz))
                    .<Class<? extends XvmStructure>>map(XvmStructureTest::asStructureClass)
                    .toList();
        }
    }

    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name, false, XvmStructure.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    private static Class<? extends XvmStructure> asStructureClass(Class<?> clz) {
        return clz.asSubclass(XvmStructure.class);
    }

    private record Fixture(
            FileStructure file,
            ConstantPool pool,
            ModuleStructure module,
            PackageStructure pkg,
            ClassStructure clz,
            PropertyStructure property,
            MethodStructure method,
            Parameter parameter,
            MethodStructure codeMethod,
            Annotation annotation,
            StringConstant constant) {}
}
