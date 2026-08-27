package org.xvm.asm;


import java.io.ByteArrayOutputStream;
import java.io.File;

import java.lang.reflect.Method;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Constants.Access;

import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

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
        assertTrue(fixture.file.verifyMutable());
        Parameter[] params = fixture.method.getParamArray();

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

        assertThrows(IllegalStateException.class, fixture.clz::verifyMutable);
        assertThrows(IllegalStateException.class, () -> fixture.clz.setSynthetic(true));
        assertFalse(fixture.clz.isSynthetic());

        assertThrows(IllegalStateException.class,
                () -> fixture.property.setInitialValue(fixture.constant));
        assertNull(fixture.property.getInitialValue());

        assertThrows(IllegalStateException.class, fixture.parameter::markDefaultValue);
        assertFalse(fixture.parameter.hasDefaultValue());

        params[0] = null;
        assertSame(fixture.parameter, fixture.method.getParam(0));
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

        ClassStructure classCopy = fixture.clz.ensureMutable();
        assertNotSame(fixture.clz, classCopy);
        assertFalse(classCopy.isReadOnly());
        classCopy.setSynthetic(true);
        assertTrue(classCopy.isSynthetic());
        assertFalse(fixture.clz.isSynthetic());

        Parameter parameterCopy = fixture.parameter.ensureMutable();
        assertNotSame(fixture.parameter, parameterCopy);
        assertFalse(parameterCopy.isReadOnly());
        assertTrue(parameterCopy.getContaining() instanceof MethodStructure);
        parameterCopy.markDefaultValue();
        assertTrue(parameterCopy.hasDefaultValue());
        assertFalse(fixture.parameter.hasDefaultValue());

        StringConstant constantCopy = fixture.constant.ensureMutable();
        assertNotSame(fixture.constant, constantCopy);
        assertFalse(constantCopy.isReadOnly());
        assertNotSame(fixture.pool, constantCopy.getConstantPool());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertDoesNotThrow(() -> fixture.file.writeTo(out));
        assertTrue(out.size() > 0);
        assertTrue(fixture.file.isReadOnly());

        assertSame(fileCopy, fileCopy.ensureMutable());
        assertSame(classCopy, classCopy.ensureMutable());
    }

    @Test
    public void testStructureSubclassesDeclareCovariantReturns()
            throws Exception {
        List<Class<? extends XvmStructure>> classes = findStructureClasses();
        assertFalse(classes.isEmpty());

        for (Class<? extends XvmStructure> clz : classes) {
            Method ensureMutable  = clz.getDeclaredMethod("ensureMutable");
            Method ensureReadOnly = clz.getDeclaredMethod("ensureReadOnly");
            assertEquals(clz, ensureMutable.getReturnType(), clz.getName());
            assertEquals(clz, ensureReadOnly.getReturnType(), clz.getName());
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
        MethodStructure method = clz.createMethod(false, Access.PUBLIC, null, Parameter.NO_PARAMS,
                "run", new Parameter[] {parameter}, false, false);
        StringConstant constant = pool.ensureStringConstant("immutable");

        return new Fixture(file, pool, module, pkg, clz, property, method, parameter, constant);
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
                    .map(XvmStructureTest::asStructureClass)
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
            StringConstant constant) {}
}
