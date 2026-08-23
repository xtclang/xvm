package org.xvm.asm.constants;


import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.FileStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.MethodBody.Implementation;
import org.xvm.asm.constants.PropertyBody.Effect;
import org.xvm.asm.constants.TypeInfo.Progress;

import org.xvm.util.ListMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests for {@link PropertyInfo}, {@link PropertyBody}, and {@link ChildInfo} ownership.
 */
public class TypeInfoMemberOwnershipTest {
    /**
     * PropertyInfo and ChildInfo objects must be copied per TypeInfo owner. The old attachment
     * model could let the first owner mutate shared source metadata and leak it to later owners.
     */
    @Test
    public void propertyAndChildInfoHaveExclusiveOwners() {
        FileStructure  file   = new FileStructure("test");
        ClassStructure struct = file.getModule().createClass(
                Access.PUBLIC, Format.CLASS, "Test", null);

        PropertyStructure structProperty = struct.createProperty(false, Access.PUBLIC,
                Access.PUBLIC, struct.getCanonicalType(), "value");
        PropertyConstant idProperty = structProperty.getIdentityConstant();
        PropertyBody body = new PropertyBody(structProperty, Implementation.Native, null,
                structProperty.getType(), true, false, false, Effect.None, Effect.None,
                false, false, null, null);
        PropertyInfo property = PropertyInfo.create(body, 0);

        ClassStructure structChild = struct.createClass(
                Access.PUBLIC, Format.CLASS, "Child", null);
        ChildInfo child = new ChildInfo(structChild);
        var childCopy = new ChildInfo(structChild);

        assertEquals(child, childCopy);
        assertEquals(child.hashCode(), childCopy.hashCode());

        TypeInfo info1 = createTypeInfo(struct, idProperty, property, child);
        TypeInfo info2 = createTypeInfo(struct, idProperty, property, child);

        PropertyInfo property1 = info1.getProperties().get(idProperty);
        PropertyInfo property2 = info2.getProperties().get(idProperty);

        assertNull(property.getTypeInfo());
        assertNull(child.getTypeInfo());
        assertSame(info1, property1.getTypeInfo());
        assertSame(info2, property2.getTypeInfo());
        assertNotSame(property1, property2);

        assertSame(property1, property1.getHead().getPropertyInfo());
        assertSame(property2, property2.getHead().getPropertyInfo());
        assertNotSame(property1.getHead(), property2.getHead());

        assertSame(property1, info1.getVirtProperties().get(idProperty.getNestedIdentity()));
        assertSame(property2, info2.getVirtProperties().get(idProperty.getNestedIdentity()));

        assertFalse(property1.getHead().isExploded());
        assertFalse(property2.getHead().isExploded());
        property1.getHead().markExploded();
        assertTrue(property1.getHead().isExploded());
        assertFalse(property2.getHead().isExploded());

        ChildInfo child1 = info1.getChildInfosByName().get("Child");
        ChildInfo child2 = info2.getChildInfosByName().get("Child");

        assertSame(info1, child1.getTypeInfo());
        assertSame(info2, child2.getTypeInfo());
        assertNotSame(child1, child2);
        assertNotSame(child1.getAllIdentities(), child2.getAllIdentities());

        assertSame(child1, info1.getChildInfosByName().get("alias.Child"));
        assertSame(child2, info2.getChildInfosByName().get("alias.Child"));
    }

    /**
     * PropertyInfo construction must not call overridable body-attachment hooks before the owner
     * object is fully assigned. That was a construction hazard before parallelism is involved.
     */
    @Test
    public void propertyInfoFactoryDoesNotCallOverridableBodyAttachment() {
        FileStructure  file   = new FileStructure("test");
        ClassStructure struct = file.getModule().createClass(
                Access.PUBLIC, Format.CLASS, "Test", null);

        PropertyStructure structProperty = struct.createProperty(false, Access.PUBLIC,
                Access.PUBLIC, struct.getCanonicalType(), "value");
        var body = new OwnerInspectingPropertyBody(structProperty, 11, 1);
        PropertyInfo property = PropertyInfo.create(body, 11);

        assertEquals(11, property.getRank());
        assertEquals(1, property.getPropertyBodies().length);
        assertSame(property, property.getHead().getPropertyInfo());
        assertNotSame(body, property.getHead());
        assertNull(body.getPropertyInfo());
    }

    /**
     * Property metadata owner lookup must not require ambient current-pool state. The duck-typed
     * identity repair path now has a receiver-owner pool available even when no scope is bound.
     */
    @Test
    public void propertyInfoPoolHelperUsesOwnerWithoutAmbientPool() throws Exception {
        FileStructure  file   = new FileStructure("test");
        ConstantPool   pool   = file.getConstantPool();
        ClassStructure struct = file.getModule().createClass(
                Access.PUBLIC, Format.CLASS, "Test", null);

        PropertyStructure structProperty = struct.createProperty(false, Access.PUBLIC,
                Access.PUBLIC, struct.getCanonicalType(), "value");
        PropertyConstant idProperty = structProperty.getIdentityConstant();
        PropertyBody body = new PropertyBody(structProperty, Implementation.Native, null,
                structProperty.getType(), true, false, false, Effect.None, Effect.None,
                false, false, null, null);
        var child = new ChildInfo(struct.createClass(Access.PUBLIC, Format.CLASS, "Child", null));
        PropertyInfo property = createTypeInfo(struct, idProperty, PropertyInfo.create(body, 0), child)
                .getProperties().get(idProperty);

        try (var _ = ConstantPool.withPool(null)) {
            assertSame(pool, invokePool(property));
            assertTrue(property.isIdentityValid(idProperty));
        }
    }

    /**
     * Parallel TypeInfo construction must not share mutable property or child metadata. This
     * covers the reentrant failure mode where two owners are built from the same source graph.
     */
    @Test
    public void typeInfoConstructionCopiesPropertyAndChildInfoInParallel()
            throws Exception {
        FileStructure  file   = new FileStructure("test");
        ClassStructure struct = file.getModule().createClass(
                Access.PUBLIC, Format.CLASS, "Test", null);

        PropertyStructure structProperty = struct.createProperty(false, Access.PUBLIC,
                Access.PUBLIC, struct.getCanonicalType(), "value");
        PropertyConstant idProperty = structProperty.getIdentityConstant();
        PropertyBody body = new PropertyBody(structProperty, Implementation.Native, null,
                structProperty.getType(), true, false, false, Effect.None, Effect.None,
                false, false, null, null);
        PropertyInfo property = PropertyInfo.create(body, 0);
        ChildInfo child = new ChildInfo(struct.createClass(
                Access.PUBLIC, Format.CLASS, "Child", null));
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = IntStream.range(0, 8)
                    .mapToObj(i -> executor.submit(() -> {
                        start.await();
                        return createTypeInfo(struct, idProperty, property, child);
                    }))
                    .toList();

            start.countDown();

            var props = Collections.newSetFromMap(new IdentityHashMap<PropertyInfo, Boolean>());
            var children = Collections.newSetFromMap(new IdentityHashMap<ChildInfo, Boolean>());
            for (var future : futures) {
                TypeInfo info = future.get(10, TimeUnit.SECONDS);
                PropertyInfo ownedProperty = info.getProperties().get(idProperty);
                ChildInfo ownedChild = info.getChildInfosByName().get("Child");

                assertSame(info, ownedProperty.getTypeInfo());
                assertSame(ownedProperty, ownedProperty.getHead().getPropertyInfo());
                assertSame(info, ownedChild.getTypeInfo());
                assertSame(ownedChild, info.getChildInfosByName().get("alias.Child"));
                assertTrue(props.add(ownedProperty));
                assertTrue(children.add(ownedChild));
            }
        }

        assertNull(property.getTypeInfo());
        assertNull(child.getTypeInfo());
    }

    /**
     * Optimized property accessor chains are runtime metadata. The old get/set caches were plain
     * lazy arrays even though building them can create field-access bodies and generated delegation
     * methods. Parallel first access must publish one complete top-level chain per accessor.
     */
    @Test
    public void optimizedPropertyAccessorChainsAreSafelyPublishedInParallel() throws Exception {
        var getField = PropertyInfo.class.getDeclaredField("m_chainGet");
        var setField = PropertyInfo.class.getDeclaredField("m_chainSet");
        assertTrue(Modifier.isVolatile(getField.getModifiers()));
        assertTrue(Modifier.isVolatile(setField.getModifiers()));
        getField.setAccessible(true);
        setField.setAccessible(true);

        var file = new FileStructure("test");
        var struct = file.getModule().createClass(
                Access.PUBLIC, Format.CLASS, "Test", null);

        var structProperty = struct.createProperty(false, Access.PUBLIC,
                Access.PUBLIC, struct.getCanonicalType(), "value");
        var idProperty = structProperty.getIdentityConstant();
        var body = new PropertyBody(structProperty, Implementation.Explicit, null,
                structProperty.getType(), false, true, false, Effect.None, Effect.None,
                true, false, null, null);
        var property = PropertyInfo.create(body, 0);
        var child = new ChildInfo(struct.createClass(Access.PUBLIC, Format.CLASS, "Child", null));
        var info = createTypeInfo(struct, idProperty, property, child);
        var owned = info.getProperties().get(idProperty);
        var start = new CountDownLatch(1);
        record AccessorChains(MethodBody[] getter, MethodBody[] setter) {}

        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = IntStream.range(0, 8)
                    .mapToObj(i -> executor.submit(() -> {
                        start.await();
                        return new AccessorChains(
                                owned.ensureOptimizedGetChain(info, null),
                                owned.ensureOptimizedSetChain(info, null));
                    }))
                    .toList();

            start.countDown();

            MethodBody[] getChain = null;
            MethodBody[] setChain = null;
            for (var future : futures) {
                var chains = future.get(10, TimeUnit.SECONDS);
                if (getChain == null) {
                    getChain = chains.getter();
                    setChain = chains.setter();
                } else {
                    assertSame(getChain, chains.getter());
                    assertSame(setChain, chains.setter());
                }
            }

            assertSame(getChain, getField.get(owned));
            assertSame(setChain, setField.get(owned));
            assertEquals(Implementation.Field, getChain[0].getImplementation());
            assertEquals(Implementation.Field, setChain[0].getImplementation());
        }
    }

    /**
     * TypeInfoReal derived lookup caches are runtime metadata. The old implementation published
     * mutable HashMaps, delegate TypeInfo graphs, and readiness booleans through plain fields. The
     * first parallel lookup must publish immutable snapshots and completed state through a memory
     * edge while preserving the same per-TypeInfo cache identity.
     */
    @Test
    public void derivedTypeInfoCachesAreSafelyPublishedInParallel() throws Exception {
        var propsField    = TypeInfoReal.class.getDeclaredField("m_mapPropertiesByName");
        var methodsField  = TypeInfoReal.class.getDeclaredField("m_mapMethodsBySignature");
        var delegates     = TypeInfoReal.class.getDeclaredField("m_delegates");
        var cacheReady    = TypeInfoReal.class.getDeclaredField("m_fCacheReady");
        var childrenReady = TypeInfoReal.class.getDeclaredField("m_fChildrenChecked");
        assertTrue(Modifier.isVolatile(propsField.getModifiers()));
        assertTrue(Modifier.isVolatile(methodsField.getModifiers()));
        assertTrue(Modifier.isVolatile(delegates.getModifiers()));
        assertTrue(Modifier.isVolatile(cacheReady.getModifiers()));
        assertTrue(Modifier.isVolatile(childrenReady.getModifiers()));
        propsField.setAccessible(true);
        methodsField.setAccessible(true);
        delegates.setAccessible(true);
        cacheReady.setAccessible(true);
        childrenReady.setAccessible(true);

        var file   = new FileStructure("test");
        var pool   = file.getConstantPool();
        var struct = file.getModule().createClass(
                Access.PUBLIC, Format.CLASS, "Test", null);

        var structProperty = struct.createProperty(false, Access.PUBLIC,
                Access.PUBLIC, struct.getCanonicalType(), "value");
        var idProperty = structProperty.getIdentityConstant();
        var bodyProperty = new PropertyBody(structProperty, Implementation.Native, null,
                structProperty.getType(), true, false, false, Effect.None, Effect.None,
                false, false, null, null);
        var property = PropertyInfo.create(bodyProperty, 0);
        var sig = pool.ensureSignatureConstant(
                "run", ConstantPool.NO_TYPES, ConstantPool.NO_TYPES);
        var idMethod = pool.ensureMethodConstant(struct.getIdentityConstant(), sig);
        var method = MethodInfo.create(new MethodBody(idMethod, sig, Implementation.Native), 0);
        var info = createTypeInfo(struct, idProperty, property, null, idMethod, sig, method);
        var start = new CountDownLatch(1);
        record DerivedCaches(
                Map<String, PropertyInfo> props,
                Map<SignatureConstant, MethodInfo> methods,
                TypeInfo delegates,
                boolean abstractType) {}

        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = IntStream.range(0, 8)
                    .mapToObj(i -> executor.submit(() -> {
                        start.await();
                        return new DerivedCaches(
                                info.ensurePropertiesByName(),
                                info.ensureMethodsBySignature(),
                                info.asDelegates(),
                                info.isAbstract());
                    }))
                    .toList();

            start.countDown();

            DerivedCaches first = null;
            for (var future : futures) {
                var caches = future.get(10, TimeUnit.SECONDS);
                if (first == null) {
                    first = caches;
                } else {
                    assertSame(first.props(), caches.props());
                    assertSame(first.methods(), caches.methods());
                    assertSame(first.delegates(), caches.delegates());
                    assertEquals(first.abstractType(), caches.abstractType());
                }
            }

            assertSame(first.props(), propsField.get(info));
            assertSame(first.methods(), methodsField.get(info));
            assertSame(first.delegates(), delegates.get(info));
            assertTrue(cacheReady.getBoolean(info));
            assertFalse(childrenReady.getBoolean(info));
            assertSame(info.getProperties().get(idProperty), first.props().get("value"));
            assertSame(info.getMethods().get(idMethod), first.methods().get(sig));
            var firstCaches = first;
            assertThrows(UnsupportedOperationException.class,
                    () -> firstCaches.props().put("other", property));
            assertThrows(UnsupportedOperationException.class,
                    () -> firstCaches.methods().put(sig, method));
        }
    }

    /**
     * The constructor-escape refactor must preserve the original parent-validation behavior for
     * property and formal-child constants while removing overridable constructor callbacks.
     */
    @Test
    public void propertyConstantValidationKeepsNormalAndFormalChildRules() {
        FileStructure  file   = new FileStructure("test");
        ClassStructure struct = file.getModule().createClass(
                Access.PUBLIC, Format.CLASS, "Test", null);
        var pool = file.getConstantPool();
        var formal = struct.addTypeParam("Element", pool.typeObject()).getIdentityConstant();
        var child = pool.ensureFormalTypeChildConstant(formal, "Value");

        assertSame(formal, child.getParentConstant());

        var property = pool.ensurePropertyConstant(struct.getIdentityConstant(), "value");
        assertThrows(IllegalArgumentException.class,
                () -> pool.ensureFormalTypeChildConstant(property, "Bad"));
    }

    private static ConstantPool invokePool(PropertyInfo property) throws Exception {
        Method method = PropertyInfo.class.getDeclaredMethod("pool");
        method.setAccessible(true);
        return (ConstantPool) method.invoke(property);
    }

    private TypeInfoReal createTypeInfo(
            ClassStructure   struct,
            PropertyConstant idProperty,
            PropertyInfo     property,
            ChildInfo        child) {
        return createTypeInfo(struct, idProperty, property, child, null, null, null);
    }

    private TypeInfoReal createTypeInfo(
            ClassStructure    struct,
            PropertyConstant  idProperty,
            PropertyInfo      property,
            ChildInfo         child,
            MethodConstant    idMethod,
            SignatureConstant sig,
            MethodInfo        method) {
        ListMap<String, ChildInfo> children = new ListMap<>();
        if (child != null) {
            children.put("Child", child);
            children.put("alias.Child", child);
        }

        Map<MethodConstant, MethodInfo> methods = idMethod == null
                ? Collections.emptyMap()
                : Map.of(idMethod, method);
        Map<Object, MethodInfo> virtualMethods = sig == null
                ? Collections.emptyMap()
                : Map.of(sig, method);

        return new TypeInfoReal(
                struct.getCanonicalType(), 0, struct, 0, false,
                Collections.emptyMap(), Annotation.NO_ANNOTATIONS, Annotation.NO_ANNOTATIONS,
                null, null, null, Collections.emptyList(), new ListMap<>(), new ListMap<>(),
                Map.of(idProperty, property), methods,
                Map.of(idProperty.getNestedIdentity(), property), virtualMethods,
                children, null, Progress.Complete);
    }

    private static final class OwnerInspectingPropertyBody extends PropertyBody {
        private final int expectedRank;
        private final int expectedBodies;

        OwnerInspectingPropertyBody(
                PropertyStructure struct,
                int               expectedRank,
                int               expectedBodies) {
            super(struct, Implementation.Native, null, struct.getType(), true, false, false,
                    Effect.None, Effect.None, false, false, null, null);

            this.expectedRank   = expectedRank;
            this.expectedBodies = expectedBodies;
        }

        @Override
        synchronized PropertyBody forProperty(PropertyInfo property) {
            if (property.getRank() != expectedRank
                    || property.getPropertyBodies().length != expectedBodies) {
                throw new IllegalStateException("property owner was observed too early");
            }
            return super.forProperty(property);
        }
    }
}
