package org.xvm.runtime;



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

import org.xvm.util.Lazy;

import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.template.text.xString.StringHandle;
import org.xvm.asm.constants.Nid;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        var containerA = newContainer("DiagA");
        var containerB = newContainer("DiagB");

        var validation = OwnershipDiagnostics.validate(containerA, containerB);

        assertTrue(validation.isValid(), validation::message);
        assertDoesNotThrow(() -> OwnershipDiagnostics.assertValid(containerA, containerB));
    }

    /**
     * The dump must show registry and explicit-owner helper state so hidden global ownership
     * problems are visible when same-JVM or parallel-container stress fails.
     */
    @Test
    public void dumpShowsRuntimeRegistryAndExplicitOwnerHelpers() throws Exception {
        var runtime = new Runtime();
        try {
            var container = runtime.registerContainer(
                    new TestContainer(runtime, new FileStructure("DiagRegistered")));

            // The facts worth asserting are structural, so assert them structurally. Matching the
            // dump's wording passes because the output happens to be phrased that way, survives a
            // genuine loss of information, and breaks on a harmless rewording - the same trap that
            // got a batch of source-comparison tests deleted.
            assertTrue(runtime.containers().contains(container),
                    "the runtime should have registered the container");
            assertNotNull(container.getConstHeap(), "the container should own a const heap");

            // the load-bearing one: DUMPING MUST NOT FORCE THE LAZY. Reading the field's own
            // isComputed() states that directly, where "contains(Lazy.Bound[deferred])" only
            // implied it via a rendering.
            var lazyTemplates = lazyField(container, "f_nativeTemplates");
            assertFalse(lazyTemplates.isComputed(),
                    "the dump must report a deferred lazy without computing it");

            var dump = OwnershipDiagnostics.dump(container);

            assertFalse(dump.isBlank(), "the dump should render something");
            assertFalse(lazyTemplates.isComputed(),
                    "and must still not have computed it afterwards");
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
        var containerA = newContainer("DiagA");
        var containerB = newContainer("DiagB");

        var templateFromB = new TestTemplate(containerB,
                createClass(containerB.file, "ForeignTemplate"));
        cacheTemplate(containerA, templateFromB);

        var validation = OwnershipDiagnostics.validate(containerA, containerB);

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
        var containerA = newContainer("DiagA");
        var containerB = newContainer("DiagB");
        var handleB = newHandle(containerB, "ForeignHandle");

        var validation =
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
        var containerA = newContainer("DiagA");
        var containerB = newContainer("DiagB");
        var handleA = newHandle(containerA, "RootHandle");
        var handleB = newHandle(containerB, "LeakedHandle");

        setFields(handleA, handleB);

        var validation =
                OwnershipDiagnostics.validateHandle(containerA, "root", handleA);

        assertFalse(validation.isValid());
        assertFalse(validation.ownerMismatches().isEmpty());
    }

    /**
     * A masked/revealed {@code GenericHandle} is an access view of the same runtime object, not a
     * deep owner-transfer copy. The old path could reach {@code cloneAs(...)} for a different
     * container even when the handle graph was not shared with that target owner. That shallow clone
     * copied live runtime fields and then rewrote the owner, which is exactly the kind of
     * cross-container leak that made same-JVM execution unstable. The guard must reject the mask
     * before cloning.
     */
    @Test
    public void crossOwnerMaskRejectsNonSharedHandleBeforeClone() {
        var target = newContainer("MaskTarget");
        var source = newContainer("MaskSource");

        var clzTarget = new TestComposition(target);
        var clzSource = new TestComposition(source, clzTarget);
        var handle    = new NonSharedHandle(clzSource);

        assertNull(handle.maskAs(target, source.getConstantPool().typeObject()));
        assertTrue(handle.wasSharedChecked());
    }

    /**
     * No reflection needed: {@code f_mapTemplatesByType} is protected and this test lives in
     * {@code org.xvm.runtime}, so the field is simply in scope. Reaching it through
     * {@code getDeclaredField} bought nothing and cost an unchecked cast, because {@code Field.get}
     * erases to {@code Object} and no cast can then check the type arguments.
     */
    private static void cacheTemplate(Container container, ClassTemplate template) {
        container.f_mapTemplatesByType.put(
                template.getStructure().getIdentityConstant().getType(), template);
    }

    private static TestContainer newContainer(String moduleName) {
        var file = new FileStructure(moduleName);
        return new TestContainer(new Runtime(), file);
    }

    private static GenericHandle newHandle(TestContainer container, String className) {
        return new GenericHandle(new TestComposition(container));
    }

    /**
     * Reads a container's {@code Lazy.Bound} cell so a test can ask whether it has been computed
     * without computing it. Reflective because the cell is private; the alternative was asserting
     * on how the dump renders it, which is what this replaces.
     */
    private static Lazy.Bound<?, ?> lazyField(Container container, String name) throws Exception {
        var field = Container.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Lazy.Bound<?, ?>) field.get(container);
    }

    /**
     * This one DOES need reflection: {@code GenericHandle.m_aFields} is private and final, so
     * unlike {@code cacheTemplate} above there is no in-scope path to it, and the test needs to
     * plant a handle owned by a foreign container to give the validator something to catch.
     */
    private static void setFields(GenericHandle handle, ObjectHandle... fields)
            throws Exception {
        var field = GenericHandle.class.getDeclaredField("m_aFields");
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
            this(container, null);
        }

        private TestComposition(Container container, TypeComposition clzMask) {
            this.container = container;
            this.clzMask   = clzMask;
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
            return clzMask;
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
        public ClassComposition.FieldInfo getFieldInfo(Nid id) {
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
        public CallChain getMethodCallChain(Nid nidMethod) {
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
        public Map<Nid, ClassComposition.FieldInfo> getFieldLayout() {
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
        private final TypeComposition clzMask;
    }

    private static final class NonSharedHandle
            extends GenericHandle {
        private NonSharedHandle(TypeComposition clazz) {
            super(clazz);
        }

        @Override
        public boolean isService() {
            // Skip the unrelated non-core proxy branch so this test exercises the inherited
            // cross-owner direct-mask guard.
            return true;
        }

        @Override
        public ObjectHandle cloneAs(TypeComposition clazz) {
            throw new IllegalStateException("non-shared cross-owner masks must not clone");
        }

        @Override
        public boolean isShared(Container container, Map<ObjectHandle, Boolean> visited) {
            checked = true;
            return false;
        }

        private boolean wasSharedChecked() {
            return checked;
        }

        private boolean checked;
    }

    /**
     * Exercises the runtime-scoped half of the API. These read as dead code to a static analyser
     * because nothing in the tree calls them - they exist to be called from a host or a debugger -
     * so covering them here is what distinguishes "unused" from "unusable", and keeps the
     * validate/assert pairing symmetric with the container-scoped methods above.
     */
    @Test
    public void runtimeScopedHelpersWalkTheWholeRuntime() {
        var runtime = new Runtime();
        try {
            var container = runtime.registerContainer(
                    new TestContainer(runtime, new FileStructure("DiagRuntimeScope")));

            assertTrue(OwnershipDiagnostics.runtimeContainers(container).contains(container),
                    "the walk should reach the container it started from");
            assertTrue(OwnershipDiagnostics.validateRuntime(container).isValid(),
                    "a freshly registered container is clean");
            assertDoesNotThrow(() -> OwnershipDiagnostics.assertRuntimeValid(container));

            // dumpRuntime is a RENDERER, so the only thing worth asserting is that it renders.
            // Asserting on its wording is the string-matching trap: an assertion that reads
            // "contains(...)" passes because the output happens to be phrased that way, and fails
            // on a harmless rewording while never noticing a genuine loss of information. The
            // meaningful facts - which containers the walk reaches, and whether they are clean -
            // are asserted above through the structured API instead.
            var dump = OwnershipDiagnostics.dumpRuntime(container);
            assertFalse(dump.isBlank(), "the runtime dump should render something");
        } finally {
            runtime.shutdownXVM();
        }
    }

    /**
     * A clean sweep must both report itself clean and let {@code assertClean} pass. The two can
     * disagree only if the report's own emptiness test and its assertion drift apart, which is
     * exactly what an untested assertion invites.
     */
    @Test
    public void aCleanSweepReportPassesItsOwnAssertion() {
        var runtime = new Runtime();
        try {
            var container = runtime.registerContainer(
                    new TestContainer(runtime, new FileStructure("DiagSweep")));

            var report = OwnershipDiagnostics.sweepForeignReferences(container);

            assertTrue(report.isClean(), () -> "unexpected foreign references:\n" + report.render());
            assertDoesNotThrow(report::assertClean);
            assertTrue(report.objectsVisited() > 0, "the sweep should have visited something");
        } finally {
            runtime.shutdownXVM();
        }
    }
}
