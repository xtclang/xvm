package org.xvm.runtime;


import java.lang.reflect.Field;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constants;
import org.xvm.asm.FileStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.VersionConstant;

import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.template.text.xString.StringHandle;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests for runtime owner graph validation.
 */
public class OwnershipDiagnosticsTest {
    /**
     * The validator must accept a clean synthetic owner graph. Otherwise diagnostics would be too
     * noisy to use as a stress guard for real cross-container leaks.
     */
    @Test
    public void cleanSyntheticContainersValidate() {
        TestContainer containerA = newContainer("DiagA");
        TestContainer containerB = newContainer("DiagB");

        OwnershipDiagnostics.Validation validation =
                OwnershipDiagnostics.validate(containerA, containerB);

        assertTrue(validation.isValid(), validation::message);
        assertDoesNotThrow(() -> OwnershipDiagnostics.assertValid(containerA, containerB));
    }

    /**
     * The dump must show registry and explicit-owner helper state so hidden global ownership
     * problems are visible when same-JVM or parallel-container stress fails.
     */
    @Test
    public void dumpShowsRuntimeRegistryAndExplicitOwnerHelpers() {
        var runtime = new Runtime();
        try {
            var container = runtime.registerContainer(
                    new TestContainer(runtime, new FileStructure("DiagRegistered")));

            var dump = OwnershipDiagnostics.dump(container);

            assertTrue(dump.contains("runtimeRegistry = contains=true size=1"));
            assertTrue(dump.contains("constHeap = ConstHeap@"));
            assertTrue(dump.contains("owner=explicit-parameter"));
            assertTrue(dump.contains("nativeTemplates = Lazy.Owner[deferred]"));
            assertFalse(dump.contains("nativeTemplates = Lazy.Owner[computed]"));
        } finally {
            runtime.shutdownXVM();
        }
    }

    /**
     * A container-owned cache must not contain a template from another owner. This is the core
     * runtime failure mode caused by mutable static INSTANCE fields.
     */
    @Test
    public void validatorRejectsForeignTemplateInOwnerCache() throws Exception {
        TestContainer containerA = newContainer("DiagA");
        TestContainer containerB = newContainer("DiagB");

        TestTemplate templateFromB = new TestTemplate(containerB,
                createClass(containerB.file, "ForeignTemplate"));
        cacheTemplate(containerA, templateFromB);

        OwnershipDiagnostics.Validation validation =
                OwnershipDiagnostics.validate(containerA, containerB);

        assertFalse(validation.isValid());
        assertFalse(validation.ownerMismatches().isEmpty());
        assertThrows(IllegalStateException.class,
                () -> OwnershipDiagnostics.assertValid(containerA, containerB));
    }

    /**
     * Root handles are owner-bearing runtime state. The validator must reject a root handle from
     * another container before such sharing is mistaken for a valid cache hit.
     */
    @Test
    public void validatorRejectsForeignRootHandle() {
        TestContainer containerA = newContainer("DiagA");
        TestContainer containerB = newContainer("DiagB");
        GenericHandle handleB = newHandle(containerB, "ForeignHandle");

        OwnershipDiagnostics.Validation validation =
                OwnershipDiagnostics.validateHandle(containerA, "root", handleB);

        assertFalse(validation.isValid());
        assertFalse(validation.ownerMismatches().isEmpty());
        assertThrows(IllegalStateException.class,
                () -> OwnershipDiagnostics.assertHandleValid(containerA, "root", handleB));
    }

    /**
     * Ownership validation must walk handle fields, not only top-level cache entries. Wrong-owner
     * values can hide inside object graphs created by reentrant runtime operations.
     */
    @Test
    public void validatorWalksHandleFieldGraph() throws Exception {
        TestContainer containerA = newContainer("DiagA");
        TestContainer containerB = newContainer("DiagB");
        GenericHandle handleA = newHandle(containerA, "RootHandle");
        GenericHandle handleB = newHandle(containerB, "LeakedHandle");

        setFields(handleA, handleB);

        OwnershipDiagnostics.Validation validation =
                OwnershipDiagnostics.validateHandle(containerA, "root", handleA);

        assertFalse(validation.isValid());
        assertFalse(validation.ownerMismatches().isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static void cacheTemplate(Container container, ClassTemplate template)
            throws Exception {
        Field field = Container.class.getDeclaredField("f_mapTemplatesByType");
        field.setAccessible(true);

        Map<TypeConstant, ClassTemplate> templates =
                (Map<TypeConstant, ClassTemplate>) field.get(container);
        templates.put(template.getStructure().getIdentityConstant().getType(), template);
    }

    private static TestContainer newContainer(String moduleName) {
        FileStructure file = new FileStructure(moduleName);
        return new TestContainer(new Runtime(), file);
    }

    private static GenericHandle newHandle(TestContainer container, String className) {
        return new GenericHandle(new TestComposition(container));
    }

    private static void setFields(GenericHandle handle, ObjectHandle... fields)
            throws Exception {
        Field field = GenericHandle.class.getDeclaredField("m_aFields");
        field.setAccessible(true);
        field.set(handle, fields);
    }

    private static ClassStructure createClass(FileStructure file, String name) {
        return file.getModule().createClass(
                Constants.Access.PUBLIC, Component.Format.CLASS, name, null);
    }

    private static final class TestContainer
            extends Container {
        private TestContainer(Runtime runtime, FileStructure file) {
            super(runtime, null, file.getModuleId());
            this.file = file;
        }

        @Override
        public boolean isSpecified(String name) {
            return false;
        }

        @Override
        public boolean isPresent(IdentityConstant id) {
            return false;
        }

        @Override
        public boolean isVersionMatch(ModuleConstant module, VersionConstant version) {
            return false;
        }

        @Override
        public boolean isVersion(VersionConstant version) {
            return false;
        }

        @Override
        public ObjectHandle getInjectable(Frame frame, String name, TypeConstant type,
                                          ObjectHandle opts) {
            return null;
        }

        private final FileStructure file;
    }

    private static final class TestTemplate
            extends ClassTemplate {
        private TestTemplate(Container container, ClassStructure structure) {
            super(container, structure);
        }
    }

    private static final class TestComposition
            implements TypeComposition {
        private TestComposition(Container container) {
            this.container = container;
        }

        @Override
        public Container getContainer() {
            return container;
        }

        @Override
        public OpSupport getSupport() {
            return null;
        }

        @Override
        public ClassTemplate getTemplate() {
            return null;
        }

        @Override
        public TypeConstant getType() {
            return null;
        }

        @Override
        public TypeConstant getInceptionType() {
            return null;
        }

        @Override
        public TypeConstant getBaseType() {
            return null;
        }

        @Override
        public TypeComposition maskAs(TypeConstant type) {
            return null;
        }

        @Override
        public TypeComposition revealAs(TypeConstant type) {
            return null;
        }

        @Override
        public ObjectHandle ensureOrigin(ObjectHandle handle) {
            return handle;
        }

        @Override
        public ObjectHandle ensureAccess(ObjectHandle handle, Constants.Access access) {
            return handle;
        }

        @Override
        public TypeComposition ensureAccess(Constants.Access access) {
            return this;
        }

        @Override
        public boolean isStruct() {
            return false;
        }

        @Override
        public MethodStructure ensureAutoInitializer() {
            return null;
        }

        @Override
        public ObjectHandle[] initializeStructure() {
            return new ObjectHandle[0];
        }

        @Override
        public ClassComposition.FieldInfo getFieldInfo(Object id) {
            return null;
        }

        @Override
        public boolean makeStructureImmutable(ObjectHandle[] fields) {
            return true;
        }

        @Override
        public boolean hasOuter() {
            return false;
        }

        @Override
        public boolean isInjected(PropertyConstant idProp) {
            return false;
        }

        @Override
        public boolean isAtomic(PropertyConstant idProp) {
            return false;
        }

        @Override
        public CallChain getMethodCallChain(Object nidMethod) {
            return null;
        }

        @Override
        public CallChain getPropertyGetterChain(PropertyConstant idProp) {
            return null;
        }

        @Override
        public CallChain getPropertySetterChain(PropertyConstant idProp) {
            return null;
        }

        @Override
        public Map<Object, ClassComposition.FieldInfo> getFieldLayout() {
            return Map.of();
        }

        @Override
        public StringHandle[] getFieldNameArray() {
            return new StringHandle[0];
        }

        @Override
        public ObjectHandle[] getFieldValueArray(Frame frame, GenericHandle handle) {
            return new ObjectHandle[0];
        }

        private final Container container;
    }
}
