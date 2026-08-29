package org.xvm.runtime;


import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Constants;
import org.xvm.asm.FileStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.VersionConstant;

import org.xvm.runtime.ObjectHandle.GenericHandle;

import org.xvm.runtime.template.reflect.xRef.RefHandle;

import org.xvm.runtime.template.text.xString.StringHandle;
import org.xvm.asm.constants.Nid;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;


/**
 * Regression tests for {@link GenericHandle#cloneAs(TypeComposition)} access-view backing.
 */
public class GenericHandleCloneAsTest {
    /**
     * Revealed/struct access views share one runtime object; they are not deep copies. Master
     * preserved that by sharing {@code GenericHandle.m_aFields}, but then rewrote the inflated
     * {@code RefHandle.$outer} inside that shared array. Creating the clone therefore changed what
     * the source view observed. The fixed model keeps the shared regular field array and gives only
     * the inflated ref a view-local outer, so write-through still works without corrupting either
     * view's holder.
     */
    @Test
    public void sameOwnerCloneKeepsInflatedRefOuterViewLocal() {
        var container = new TestContainer(new Runtime(), new FileStructure("InflatedRefOwner"));

        var refClz    = TestComposition.ref(container);
        var fieldProp = new ClassComposition.FieldInfo(
Nid.of("prop"), 0, null, refClz, false, false, false, false);
        var fieldData = new ClassComposition.FieldInfo(
Nid.of("data"), 1, null, null, false, false, false, false);
        var sourceClz = new TestComposition(container, true, fieldProp, fieldData);
        var targetClz = new TestComposition(container, false, fieldProp, fieldData);

        var source = new GenericHandle(sourceClz);
        var value1 = new GenericHandle(new TestComposition(container));
        var value2 = new GenericHandle(new TestComposition(container));
        var data1  = new GenericHandle(new TestComposition(container));
        var data2  = new GenericHandle(new TestComposition(container));
        var ref    = new TestRefHandle(refClz, "prop", value1);

        ref.setField(null, GenericHandle.OUTER, source);
        source.setField(0, ref);
        source.setField(1, data1);

        var clone = (GenericHandle) source.cloneAs(targetClz);
        var sourceRef = (RefHandle) source.getField(null, fieldProp);
        var cloneRef = (RefHandle) clone.getField(null, fieldProp);

        assertSame(source, sourceRef.getField(null, GenericHandle.OUTER));
        assertSame(clone, cloneRef.getField(null, GenericHandle.OUTER));
        assertNotSame(sourceRef, cloneRef);

        cloneRef.setReferent(value2);
        assertSame(value2, sourceRef.getReferent());
        assertSame(value2, cloneRef.getReferent());

        clone.setField(1, data2);
        assertSame(data2, source.getField(1));
        assertSame(data2, clone.getField(1));
    }

    private static final class TestContainer
            extends Container {
        private TestContainer(Runtime runtime, FileStructure file) {
            super(runtime, null, file.getModuleId());
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
    }

    private static final class TestComposition
            implements TypeComposition {
        private final Container container;
        private final boolean struct;
        private final TypeComposition clzMask;
        private final Map<Nid, ClassComposition.FieldInfo> fields;

        private TestComposition(Container container) {
            this(container, false, null, Map.of());
        }

        private TestComposition(Container container, boolean struct,
                                ClassComposition.FieldInfo... fields) {
            this(container, struct, null, fieldMap(fields));
        }

        private TestComposition(Container container, boolean struct, TypeComposition clzMask,
                                Map<Nid, ClassComposition.FieldInfo> fields) {
            this.container = container;
            this.struct    = struct;
            this.clzMask   = clzMask;
            this.fields    = fields;
        }

        private static TestComposition ref(Container container) {
            var fieldOuter = new ClassComposition.FieldInfo(
                    Nid.of(GenericHandle.OUTER), 0, null, null, true, false, false, false);
            var fieldValue = new ClassComposition.FieldInfo(
                    Nid.of(RefHandle.REFERENT), 1, null, null, true, false, false, false);
            return new TestComposition(container, false, fieldOuter, fieldValue);
        }

        private static Map<Nid, ClassComposition.FieldInfo> fieldMap(
                ClassComposition.FieldInfo... fields) {
            return Collections.unmodifiableMap(Arrays.stream(fields)
                    .collect(Collectors.toMap(ClassComposition.FieldInfo::getNid,
                            Function.identity(), (first, second) -> second, LinkedHashMap::new)));
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
            return struct;
        }

        @Override
        public MethodStructure ensureAutoInitializer() {
            return null;
        }

        @Override
        public ObjectHandle[] initializeStructure() {
            int cFields = fields.values().stream()
                    .mapToInt(ClassComposition.FieldInfo::getIndex)
                    .max()
                    .orElse(-1) + 1;
            return new ObjectHandle[cFields];
        }

        @Override
        public ClassComposition.FieldInfo getFieldInfo(Nid id) {
            return fields.get(id);
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
            return fields;
        }

        @Override
        public StringHandle[] getFieldNameArray() {
            return new StringHandle[0];
        }

        @Override
        public ObjectHandle[] getFieldValueArray(Frame frame, GenericHandle handle) {
            return new ObjectHandle[0];
        }
    }

    private static final class TestRefHandle
            extends RefHandle {
        private TestRefHandle(TypeComposition clazz, String name, ObjectHandle referent) {
            super(clazz, name);
            setField(null, REFERENT, referent);
        }
    }
}
