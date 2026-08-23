package org.xvm.asm;


import java.lang.ref.WeakReference;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import java.nio.file.attribute.FileTime;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import org.xvm.asm.constants.AllCondition;
import org.xvm.asm.constants.AnyCondition;
import org.xvm.asm.constants.AccessTypeConstant;
import org.xvm.asm.constants.AnnotatedTypeConstant;
import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.AnonymousClassTypeConstant;
import org.xvm.asm.constants.BFloat16Constant;
import org.xvm.asm.constants.ByteConstant;
import org.xvm.asm.constants.CastTypeConstant;
import org.xvm.asm.constants.CharConstant;
import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.DecimalAutoConstant;
import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.DecimalConstant;
import org.xvm.asm.constants.DifferenceTypeConstant;
import org.xvm.asm.constants.DynamicFormalConstant;
import org.xvm.asm.constants.FPNConstant;
import org.xvm.asm.constants.FSNodeConstant;
import org.xvm.asm.constants.FileStoreConstant;
import org.xvm.asm.constants.Float128Constant;
import org.xvm.asm.constants.Float16Constant;
import org.xvm.asm.constants.Float32Constant;
import org.xvm.asm.constants.Float64Constant;
import org.xvm.asm.constants.Float8e4Constant;
import org.xvm.asm.constants.Float8e5Constant;
import org.xvm.asm.constants.FormalTypeChildConstant;
import org.xvm.asm.constants.HandleConstant;
import org.xvm.asm.constants.IntConstant;
import org.xvm.asm.constants.InnerChildTypeConstant;
import org.xvm.asm.constants.IntersectionTypeConstant;
import org.xvm.asm.constants.ImmutableTypeConstant;
import org.xvm.asm.constants.LiteralConstant;
import org.xvm.asm.constants.MapConstant;
import org.xvm.asm.constants.MatchAnyConstant;
import org.xvm.asm.constants.MethodBindingConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.NamedCondition;
import org.xvm.asm.constants.NotCondition;
import org.xvm.asm.constants.ParameterizedTypeConstant;
import org.xvm.asm.constants.PendingTypeConstant;
import org.xvm.asm.constants.PresentCondition;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyClassTypeConstant;
import org.xvm.asm.constants.RangeConstant;
import org.xvm.asm.constants.RegExConstant;
import org.xvm.asm.constants.RegisterConstant;
import org.xvm.asm.constants.RecursiveTypeConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.ServiceTypeConstant;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TerminalTypeConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeParameterConstant;
import org.xvm.asm.constants.TypeSequenceTypeConstant;
import org.xvm.asm.constants.TypedefConstant;
import org.xvm.asm.constants.UInt8ArrayConstant;
import org.xvm.asm.constants.UnionTypeConstant;
import org.xvm.asm.constants.UnresolvedNameConstant;
import org.xvm.asm.constants.UnresolvedTypeConstant;
import org.xvm.asm.constants.VersionConstant;
import org.xvm.asm.constants.VersionMatchesCondition;
import org.xvm.asm.constants.VersionedCondition;
import org.xvm.asm.constants.VirtualChildTypeConstant;

import org.xvm.runtime.ObjectHandle;

import org.xvm.type.Decimal64;

import org.xvm.util.TransientThreadLocal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests for {@link Constant#adoptedBy(ConstantPool)} ownership handoff behavior.
 */
public class ConstantAdoptionTest {
    /**
     * TypeConstant adoption must not copy owner-local helper state. A shallow clone can look fine
     * single-threaded but later reuse source-pool recursion or cache helpers in the target pool.
     */
    @Test
    public void adoptedTypeConstantClearsOwnerLocalHelperState()
            throws Exception {
        ConstantPool sourcePool = new FileStructure("source").getConstantPool();
        ConstantPool targetPool = new FileStructure("target").getConstantPool();

        ParameterizedTypeConstant source = (ParameterizedTypeConstant)
                sourcePool.ensureParameterizedTypeConstant(
                        sourcePool.typeArray(), sourcePool.typeString());
        AtomicInteger sourceDepth = fieldValue(source, "m_cRecursiveDepth");
        Object tlo = new TransientThreadLocal<Set<TypeConstant>>();

        sourceDepth.set(7);
        setField(source, "m_tloInProgress", tlo);
        setField(source, "m_mapConsumes", Map.of("source", new Object()));
        setField(source, "m_mapProduces", Map.of("source", new Object()));
        setField(source, "m_sJitName", "SourceJitName");
        setField(source, "m_typeNormalized", source);

        ParameterizedTypeConstant adopted = adopt(source, targetPool);

        assertNotSame(source, adopted);
        assertSame(sourceDepth, fieldValue(source, "m_cRecursiveDepth"));
        assertNotSame(sourceDepth, fieldValue(adopted, "m_cRecursiveDepth"));
        assertEquals(0, ((AtomicInteger) fieldValue(adopted, "m_cRecursiveDepth")).get());
        assertNull(fieldValue(adopted, "m_tloInProgress"));
        assertNull(fieldValue(adopted, "m_mapConsumes"));
        assertNull(fieldValue(adopted, "m_mapProduces"));
        assertNull(fieldValue(adopted, "m_sJitName"));
        assertNull(fieldValue(adopted, "m_typeNormalized"));
    }

    /**
     * Terminal type adoption must rebuild the type shell and register the defining identity through
     * the destination pool. Shallow clone relied on TypeConstant.setContaining(...) to clear caches
     * after copying the whole object, which was too easy to break by adding another helper field.
     */
    @Test
    public void registeredTerminalTypeConstantAdoptsSharedIdentityIntoTargetPool() {
        var sourceFile = new FileStructure("source");
        var sourcePool = sourceFile.getConstantPool();
        var struct     = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Owner", null);
        var targetPool = new FileStructure(sourceFile.getModule(), false).getConstantPool();
        var source     = (TerminalTypeConstant) struct.getIdentityConstant().getType();

        var registered = (TerminalTypeConstant) targetPool.register(source);

        assertNotSame(source, registered);
        assertSame(targetPool, registered.getConstantPool());
        assertSame(targetPool, registered.getDefiningConstant().getConstantPool());
        assertEquals(source.getValueString(), registered.getValueString());
        assertSame(registered, targetPool.ensureTerminalTypeConstant(registered.getDefiningConstant()));
    }

    /**
     * A terminal type that names an unrelated module-local identity is not target-owned logical value.
     * Direct adoption now fails in all modes instead of depending on an assertion in setContaining().
     */
    @Test
    public void terminalTypeRejectsForeignIdentityDuringAdoption() {
        var sourceFile = new FileStructure("source");
        var targetPool = new FileStructure("target").getConstantPool();
        var struct     = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Owner", null);
        var source     = (TerminalTypeConstant) struct.getIdentityConstant().getType();

        var error = assertThrows(IllegalStateException.class, () -> adopt(source, targetPool));

        assertTrue(error.getMessage().contains("foreign identity"));
    }

    /**
     * Single-child type modifiers are logical wrappers around another TypeConstant. Adoption must
     * rebuild the wrapper and let target registration intern the child, preserving the old lookup
     * cache shape without copying inherited TypeConstant helper state.
     */
    @Test
    public void registeredSingleChildTypeModifiersAdoptSharedChildIntoTargetPool() {
        var sourceFile = new FileStructure("source");
        var sourcePool = sourceFile.getConstantPool();
        var struct     = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Owner", null);
        var targetPool = new FileStructure(sourceFile.getModule(), false).getConstantPool();
        var sourceType = struct.getIdentityConstant().getType();

        List.of(sourcePool.ensureAccessTypeConstant(sourceType, Component.Access.PRIVATE),
                sourcePool.ensureImmutableTypeConstant(sourceType),
                sourcePool.ensureServiceTypeConstant(sourceType))
                .forEach(source -> {
                    var registered = targetPool.register(source);

                    assertNotSame(source, registered);
                    assertSame(targetPool, registered.getConstantPool());
                    assertSame(targetPool, registered.getUnderlyingType().getConstantPool());
                    assertEquals(source.getValueString(), registered.getValueString());
                });
    }

    /**
     * A type modifier cannot make an unrelated source-owner child look target-owned. The old shallow
     * clone path depended on TypeConstant.setContaining(...) assertions; direct adoption now throws in
     * production too.
     */
    @Test
    public void singleChildTypeModifiersRejectForeignChildDuringAdoption() {
        var sourceFile = new FileStructure("source");
        var sourcePool = sourceFile.getConstantPool();
        var targetPool = new FileStructure("target").getConstantPool();
        var struct     = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Owner", null);
        var sourceType = struct.getIdentityConstant().getType();

        List.of(sourcePool.ensureAccessTypeConstant(sourceType, Component.Access.PRIVATE),
                sourcePool.ensureImmutableTypeConstant(sourceType),
                sourcePool.ensureServiceTypeConstant(sourceType))
                .forEach(source -> {
                    var error = assertThrows(IllegalStateException.class,
                            () -> adopt(source, targetPool));

                    assertTrue(error.getMessage().contains("foreign child type"));
                });
    }

    /**
     * Relational types are logical two-child type expressions. Adoption must rebuild the expression
     * shell and let target registration intern both child types, preserving the old cache shape
     * without copying inherited TypeConstant helper state.
     */
    @Test
    public void registeredRelationalTypesAdoptSharedChildrenIntoTargetPool() {
        var sourceFile = new FileStructure("source");
        var sourcePool = sourceFile.getConstantPool();
        var left       = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Left", null);
        var right      = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Right", null);
        var targetPool = new FileStructure(sourceFile.getModule(), false).getConstantPool();
        var typeLeft   = left.getIdentityConstant().getType();
        var typeRight  = right.getIdentityConstant().getType();

        List.of(sourcePool.ensureUnionTypeConstant(typeLeft, typeRight),
                sourcePool.ensureIntersectionTypeConstant(typeLeft, typeRight),
                sourcePool.ensureDifferenceTypeConstant(typeLeft, typeRight))
                .forEach(source -> {
                    var registered = targetPool.register(source);

                    assertNotSame(source, registered);
                    assertSame(targetPool, registered.getConstantPool());
                    assertSame(targetPool, registered.getUnderlyingType().getConstantPool());
                    assertSame(targetPool, registered.getUnderlyingType2().getConstantPool());
                    assertEquals(source.getFormat(), registered.getFormat());
                    assertEquals(source.getValueString(), registered.getValueString());
                });
    }

    /**
     * A relational type cannot turn unrelated source-owner children into target-owned type state.
     * Direct adoption now fails before publication instead of copying two foreign child references.
     */
    @Test
    public void relationalTypesRejectForeignChildrenDuringAdoption() {
        var sourceFile = new FileStructure("source");
        var sourcePool = sourceFile.getConstantPool();
        var targetPool = new FileStructure("target").getConstantPool();
        var left       = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Left", null);
        var right      = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Right", null);
        var typeLeft   = left.getIdentityConstant().getType();
        var typeRight  = right.getIdentityConstant().getType();

        List.of(sourcePool.ensureUnionTypeConstant(typeLeft, typeRight),
                sourcePool.ensureIntersectionTypeConstant(typeLeft, typeRight),
                sourcePool.ensureDifferenceTypeConstant(typeLeft, typeRight))
                .forEach(source -> {
                    var error = assertThrows(IllegalStateException.class,
                            () -> adopt(source, targetPool));

                    assertTrue(error.getMessage().contains("foreign child type"));
                });
    }

    /**
     * CastTypeConstant is a transient compiler/JIT marker; the class cannot be assembled into a pool.
     * Adoption must therefore fail instead of inheriting IntersectionTypeConstant's storable copy.
     */
    @Test
    public void castTypeCannotBeAdoptedBecauseItIsTransient() {
        var sourceFile = new FileStructure("source");
        var targetPool = new FileStructure(sourceFile.getModule(), false).getConstantPool();
        var left       = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Left", null);
        var right      = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Right", null);
        var source     = new CastTypeConstant(
                sourceFile.getConstantPool(),
                left.getIdentityConstant().getType(),
                right.getIdentityConstant().getType());

        var error = assertThrows(IllegalStateException.class, () -> adopt(source, targetPool));

        assertTrue(error.getMessage().contains("transient cast type"));
    }

    /**
     * Dependant child types are logical parent+child descriptors. Adoption must rebuild the shell and
     * let target registration intern parent, child name, and child identity under the destination pool;
     * cloned child-structure and PropertyInfo caches would be source-owner metadata.
     */
    @Test
    public void registeredDependantChildTypesAdoptSharedChildrenIntoTargetPool()
            throws Exception {
        var sourceFile = new FileStructure("source");
        var sourcePool = sourceFile.getConstantPool();
        var parent     = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Parent", null);
        var method     = parent.createMethod(false, Component.Access.PUBLIC, null,
                Parameter.NO_PARAMS, "makeChild", Parameter.NO_PARAMS, true, true);
        var virtual    = parent.createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Virtual", null);
        var inner      = method.createClass(Component.Access.PUBLIC, Component.Format.CLASS,
                "Inner", null);
        var anon       = method.createClass(Component.Access.PUBLIC, Component.Format.CLASS,
                "Anon", null);
        var prop       = parent.createProperty(false, Component.Access.PUBLIC,
                Component.Access.PUBLIC, sourcePool.typeString(), "value");
        var targetPool = new FileStructure(sourceFile.getModule(), false).getConstantPool();
        var parentType = parent.getIdentityConstant().getType();

        anon.setSynthetic(true);

        List.of(sourcePool.ensureVirtualChildTypeConstant(parentType, virtual.getName()),
                sourcePool.ensureThisVirtualChildTypeConstant(parentType, virtual.getName()),
                sourcePool.ensureInnerChildTypeConstant(parentType, classId(inner)),
                sourcePool.ensureAnonymousClassTypeConstant(parentType, classId(anon)),
                sourcePool.ensurePropertyClassTypeConstant(parentType, prop.getIdentityConstant()))
                .forEach(source -> {
                    var registered = targetPool.register(source);

                    assertNotSame(source, registered);
                    assertSame(targetPool, registered.getConstantPool());
                    assertSame(targetPool, registered.getParentType().getConstantPool());
                    assertEquals(source.getFormat(), registered.getFormat());
                    assertEquals(source.getValueString(), registered.getValueString());
                });
    }

    /**
     * A dependant child type cannot copy unrelated source-owner parent/child references into another
     * pool. The old clone path had to rely on assertions in setContaining(); direct adoption now fails
     * in production before publishing an invalid target-owned type shell.
     */
    @Test
    public void dependantChildTypesRejectForeignChildrenDuringAdoption() {
        var sourceFile = new FileStructure("source");
        var sourcePool = sourceFile.getConstantPool();
        var targetPool = new FileStructure("target").getConstantPool();
        var parent     = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Parent", null);
        var method     = parent.createMethod(false, Component.Access.PUBLIC, null,
                Parameter.NO_PARAMS, "makeChild", Parameter.NO_PARAMS, true, true);
        var virtual    = parent.createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Virtual", null);
        var inner      = method.createClass(Component.Access.PUBLIC, Component.Format.CLASS,
                "Inner", null);
        var anon       = method.createClass(Component.Access.PUBLIC, Component.Format.CLASS,
                "Anon", null);
        var prop       = parent.createProperty(false, Component.Access.PUBLIC,
                Component.Access.PUBLIC, sourcePool.typeString(), "value");
        var parentType = parent.getIdentityConstant().getType();

        anon.setSynthetic(true);

        List.of(sourcePool.ensureVirtualChildTypeConstant(parentType, virtual.getName()),
                sourcePool.ensureThisVirtualChildTypeConstant(parentType, virtual.getName()),
                sourcePool.ensureInnerChildTypeConstant(parentType, classId(inner)),
                sourcePool.ensureAnonymousClassTypeConstant(parentType, classId(anon)),
                sourcePool.ensurePropertyClassTypeConstant(parentType, prop.getIdentityConstant()))
                .forEach(source -> {
                    var error = assertThrows(IllegalStateException.class,
                            () -> adopt(source, targetPool));

                    assertTrue(error.getMessage().contains("foreign"));
                });
    }

    /**
     * Transient virtual-child origin metadata participates in TypeInfo/isA calculations. Adoption must
     * preserve that in-memory logical type shape while still rebuilding all owner-local caches.
     */
    @Test
    public void adoptedTransientVirtualChildPreservesOriginParent()
            throws Exception {
        var sourceFile = new FileStructure("source");
        var sourcePool = sourceFile.getConstantPool();
        var parent     = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Parent", null);
        var targetPool = new FileStructure(sourceFile.getModule(), false).getConstantPool();
        var parentType = parent.getIdentityConstant().getType();
        var origin     = parentType.ensureAccess(Component.Access.PRIVATE);
        var source     = new VirtualChildTypeConstant(sourcePool, parentType, "Virtual", origin);

        setField(source, "m_clzChild", parent);

        var adopted = adopt(source, targetPool);

        assertSame(targetPool, adopted.getConstantPool());
        assertEquals(source.getValueString(), adopted.getValueString());
        assertNull(fieldValue(adopted, "m_clzChild"));
        assertEquals(source.getOriginParentType().getValueString(),
                adopted.getOriginParentType().getValueString());
    }

    /**
     * RecursiveTypeConstant is a TerminalTypeConstant subclass. It needs its own adoption hook so a
     * recursive typedef keeps the concrete recursive type behavior instead of becoming a plain terminal
     * type during clone-free adoption.
     */
    @Test
    public void registeredRecursiveTypeAdoptsSharedTypedefIntoTargetPool() {
        var sourceFile = new FileStructure("source");
        var sourcePool = sourceFile.getConstantPool();
        var parent     = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Parent", null);
        var typedef    = parent.createTypedef(Component.Access.PUBLIC, sourcePool.typeObject(), "Alias");
        var targetPool = new FileStructure(sourceFile.getModule(), false).getConstantPool();
        var source     = new RecursiveTypeConstant(sourcePool, typedefId(typedef));

        var registered = targetPool.register(source);

        assertEquals(RecursiveTypeConstant.class, registered.getClass());
        assertSame(targetPool, registered.getConstantPool());
        assertSame(targetPool, registered.getTypedef().getConstantPool());
        assertEquals(source.getValueString(), registered.getValueString());
    }

    /**
     * A recursive typedef from an unrelated module cannot be adopted by copying the typedef reference.
     * Failing closed keeps the target pool from publishing a recursive type backed by foreign metadata.
     */
    @Test
    public void recursiveTypeRejectsForeignTypedefDuringAdoption() {
        var sourceFile = new FileStructure("source");
        var sourcePool = sourceFile.getConstantPool();
        var targetPool = new FileStructure("target").getConstantPool();
        var parent     = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Parent", null);
        var typedef    = parent.createTypedef(Component.Access.PUBLIC, sourcePool.typeObject(), "Alias");
        var source     = new RecursiveTypeConstant(sourcePool, typedefId(typedef));

        var error = assertThrows(IllegalStateException.class, () -> adopt(source, targetPool));

        assertTrue(error.getMessage().contains("foreign typedef"));
    }

    /**
     * Annotation parameters are part of immutable logical constant identity. Constructors and
     * resolveParams(...) must not keep caller arrays that can rewrite hash/equality state later.
     */
    @Test
    public void annotationParameterInputsAreDefensivelyCopied() {
        var sourceFile  = new FileStructure("source");
        var sourcePool  = sourceFile.getConstantPool();
        var annoClass   = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.ANNOTATION, "Anno", null);
        var original    = sourcePool.ensureStringConstant("original");
        var replacement = sourcePool.ensureStringConstant("replacement");
        var params      = new Constant[] {original};
        var annotation  = new Annotation(sourcePool, classId(annoClass), params);

        params[0] = replacement;

        assertSame(original, annotation.getParams()[0]);

        var resolvedParams = new Constant[] {replacement};
        annotation.resolveParams(resolvedParams);
        resolvedParams[0] = original;

        assertSame(replacement, annotation.getParams()[0]);
    }

    /**
     * Annotated types carry annotation and underlying type identity. Adoption must register both in
     * the destination pool while dropping the derived annotation-type cache from the source owner.
     */
    @Test
    public void registeredAnnotatedTypeAdoptsAnnotationIntoTargetPool() {
        var sourceFile = new FileStructure("source");
        var sourcePool = sourceFile.getConstantPool();
        var annoClass  = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.ANNOTATION, "Anno", null);
        var target     = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Target", null);
        var targetPool = new FileStructure(sourceFile.getModule(), false).getConstantPool();
        var value      = sourcePool.ensureStringConstant("value");
        var source     = sourcePool.ensureAnnotatedTypeConstant(
                classId(annoClass), new Constant[] {value}, target.getIdentityConstant().getType());

        var registered = targetPool.register(source);

        assertNotSame(source, registered);
        assertSame(targetPool, registered.getConstantPool());
        assertSame(targetPool, registered.getUnderlyingType().getConstantPool());
        assertSame(targetPool, registered.getAnnotation().getConstantPool());
        assertSame(targetPool, registered.getAnnotationClass().getConstantPool());
        assertSame(targetPool, registered.getAnnotationParams()[0].getConstantPool());
        assertEquals(source.getValueString(), registered.getValueString());
    }

    /**
     * Annotation adoption copies the parameter array before target registration rewrites child
     * constants. Mutating the source annotation's legacy raw params array cannot mutate the adopted
     * annotation's logical value container.
     */
    @Test
    public void registeredAnnotationAdoptionDoesNotShareParameterArray() {
        var sourceFile  = new FileStructure("source");
        var sourcePool  = sourceFile.getConstantPool();
        var annoClass   = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.ANNOTATION, "Anno", null);
        var targetPool  = new FileStructure(sourceFile.getModule(), false).getConstantPool();
        var original    = sourcePool.ensureStringConstant("original");
        var replacement = sourcePool.ensureStringConstant("replacement");
        var source      = new Annotation(sourcePool, classId(annoClass), new Constant[] {original});

        var registered = targetPool.register(source);

        source.getParams()[0] = replacement;

        assertSame(targetPool, registered.getConstantPool());
        assertSame(targetPool, registered.getParams()[0].getConstantPool());
        assertEquals("original", ((StringConstant) registered.getParams()[0]).getValue());
    }

    /**
     * A live handle annotation parameter is runtime owner state, not serializable annotation identity.
     * Adoption now rejects it before publishing the annotation into the target pool.
     */
    @Test
    public void annotationRejectsOwnedRuntimeHandleParamDuringAdoption() {
        var sourceFile = new FileStructure("source");
        var sourcePool = sourceFile.getConstantPool();
        var annoClass  = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.ANNOTATION, "Anno", null);
        var targetPool = new FileStructure(sourceFile.getModule(), false).getConstantPool();
        var handle     = adopt(new HandleConstant(ObjectHandle.DEFAULT), sourcePool);
        var source     = new Annotation(sourcePool, classId(annoClass), new Constant[] {handle});

        var error = assertThrows(IllegalStateException.class, () -> adopt(source, targetPool));

        assertTrue(error.getMessage().contains("live handle parameter"));
    }

    /**
     * TypeSequenceTypeConstant is a stateless formal tuple marker. Explicit reconstruction preserves
     * the same interning behavior without inheriting the type-family shallow clone.
     */
    @Test
    public void registeredTypeSequenceReconstructsStatelessMarkerInTargetPool() {
        var sourcePool = new FileStructure("source").getConstantPool();
        var targetPool = new FileStructure("target").getConstantPool();
        var source     = sourcePool.ensureTypeSequenceTypeConstant();

        var registered = targetPool.register(source);

        assertNotSame(source, registered);
        assertSame(targetPool, registered.getConstantPool());
        assertSame(registered, targetPool.ensureTypeSequenceTypeConstant());
        assertEquals(source.getValueString(), registered.getValueString());
    }

    /**
     * Pending and unresolved type constants are mutable compiler placeholders. They are not completed
     * pool metadata and must be resolved before any owner-transfer path can publish them.
     */
    @Test
    public void pendingAndUnresolvedTypesRejectAdoptionBeforeResolution() {
        var sourcePool = new FileStructure("source").getConstantPool();
        var targetPool = new FileStructure("target").getConstantPool();
        var pending    = new PendingTypeConstant(sourcePool, sourcePool.typeObject());
        var unresolved = new UnresolvedTypeConstant(
                sourcePool, new UnresolvedNameConstant(sourcePool, "Missing"));

        var pendingError = assertThrows(IllegalStateException.class,
                () -> adopt(pending, targetPool));
        var unresolvedError = assertThrows(IllegalStateException.class,
                () -> adopt(unresolved, targetPool));

        assertTrue(pendingError.getMessage().contains("pending type"));
        assertTrue(unresolvedError.getMessage().contains("unresolved type"));
    }

    /**
     * ParameterizedTypeConstant has subclass helper state in addition to base TypeConstant state.
     * Adoption must give the target pool fresh helpers rather than copied source-owner cells.
     */
    @Test
    public void adoptedParameterizedTypeConstantGetsFreshSubclassHelpers()
            throws Exception {
        ConstantPool sourcePool = new FileStructure("source").getConstantPool();
        ConstantPool targetPool = new FileStructure("target").getConstantPool();

        ParameterizedTypeConstant source = (ParameterizedTypeConstant)
                sourcePool.ensureParameterizedTypeConstant(
                        sourcePool.typeArray(), sourcePool.typeString());
        Object sourceLock = fieldValue(source, "m_lockPrev");

        setField(source, "m_typeResolverPrev", sourcePool.typeObject());
        setField(source, "m_typeResolvedPrev", sourcePool.typeString());
        setField(source, "m_typeJitCallable", sourcePool.typeObject());

        ParameterizedTypeConstant adopted = adopt(source, targetPool);

        assertNotSame(sourceLock, fieldValue(adopted, "m_lockPrev"));
        assertNull(fieldValue(adopted, "m_typeResolverPrev"));
        assertNull(fieldValue(adopted, "m_typeResolvedPrev"));
        assertNull(fieldValue(adopted, "m_typeJitCallable"));
    }

    /**
     * SignatureConstant adoption must not copy comparison or JIT helper state. Otherwise a target
     * pool can inherit source-owner caches through the default clone path.
     */
    @Test
    public void adoptedSignatureConstantGetsFreshComparisonAndJitHelpers()
            throws Exception {
        ConstantPool sourcePool = new FileStructure("source").getConstantPool();
        ConstantPool targetPool = new FileStructure("target").getConstantPool();

        TypeConstant[] params = {sourcePool.typeString()};
        SignatureConstant source = sourcePool.ensureSignatureConstant("demo", ConstantPool.NO_TYPES, params);
        SignatureConstant previous = sourcePool.ensureSignatureConstant(
                "other", ConstantPool.NO_TYPES, ConstantPool.NO_TYPES);
        Object sourceLock = fieldValue(source, "m_lockPrev");

        setField(source, "m_sigPrev", previous);
        setField(source, "m_refSigPrev", new WeakReference<>(previous));
        setField(source, "m_nCmpPrev", 42);
        setField(source, "m_sJitName", "SourceJitMethod");
        setField(source, "m_fProperty", true);

        SignatureConstant adopted = adopt(source, targetPool);

        assertNotSame(sourceLock, fieldValue(adopted, "m_lockPrev"));
        assertNull(fieldValue(adopted, "m_sigPrev"));
        assertNull(fieldValue(adopted, "m_refSigPrev"));
        assertEquals(0, ((Integer) fieldValue(adopted, "m_nCmpPrev")).intValue());
        assertNull(fieldValue(adopted, "m_sJitName"));
        assertTrue((Boolean) fieldValue(adopted, "m_fProperty"));
    }

    /**
     * TypeParameterConstant uses a reentry cell during comparisons. Adoption must allocate a fresh
     * cell so recursive comparisons in one pool cannot share thread-local state with another.
     */
    @Test
    public void adoptedTypeParameterConstantGetsFreshReentryCell()
            throws Exception {
        FileStructure sourceFile = new FileStructure("source");
        ConstantPool  sourcePool = sourceFile.getConstantPool();
        ConstantPool  targetPool = new FileStructure("target").getConstantPool();
        ClassStructure struct = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Owner", null);
        SignatureConstant sig = sourcePool.ensureSignatureConstant(
                "method", ConstantPool.NO_TYPES, ConstantPool.NO_TYPES);
        MethodConstant method = sourcePool.ensureMethodConstant(struct.getIdentityConstant(), sig);
        TypeParameterConstant source = sourcePool.ensureRegisterConstant(method, 0, "T");
        Object sourceReentry = fieldValue(source, "f_tloReEntry");

        setField(source, "m_typeConstraint", sourcePool.typeString());

        TypeParameterConstant adopted = adopt(source, targetPool);

        assertNotSame(sourceReentry, fieldValue(adopted, "f_tloReEntry"));
        assertNull(fieldValue(adopted, "m_typeConstraint"));
    }

    /**
     * MethodConstant identity is logical ASM metadata, but its cached type and JIT method name are
     * owner/type-system helper state. Adoption must preserve parent/signature identity without
     * carrying those caches to another pool or future JIT TypeSystem.
     */
    @Test
    public void adoptedMethodConstantDropsTypeAndJitCaches() throws Exception {
        var sourceFile = new FileStructure("source");
        var sourcePool = sourceFile.getConstantPool();
        var targetPool = new FileStructure("target").getConstantPool();
        var struct     = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Owner", null);
        var sig        = sourcePool.ensureSignatureConstant(
                "method", ConstantPool.NO_TYPES, ConstantPool.NO_TYPES);
        var source     = sourcePool.ensureMethodConstant(struct.getIdentityConstant(), sig);

        setField(source, "m_type", sourcePool.typeObject());
        setField(source, "m_sJitName", "SourceJitMethod");

        var adopted = adopt(source, targetPool);

        assertSame(targetPool, adopted.getConstantPool());
        assertEquals(source.getName(), adopted.getName());
        assertNull(fieldValue(adopted, "m_type"));
        assertNull(fieldValue(adopted, "m_sJitName"));
    }

    /**
     * PropertyConstant adoption must not carry cached property type, synthetic signature,
     * constraint, or JIT-name state to another pool. Those values are derived from the target owner
     * or a future JIT TypeSystem, while the logical property identity is only parent + name.
     */
    @Test
    public void adoptedPropertyConstantDropsMetadataAndJitCaches() throws Exception {
        var sourceFile = new FileStructure("source");
        var sourcePool = sourceFile.getConstantPool();
        var targetPool = new FileStructure("target").getConstantPool();
        var struct     = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Owner", null);
        var source     = sourcePool.ensurePropertyConstant(struct.getIdentityConstant(), "prop");
        var sig        = sourcePool.ensureSignatureConstant(
                "prop", ConstantPool.NO_TYPES, ConstantPool.NO_TYPES);

        setField(source, "m_type", sourcePool.typeString());
        setField(source, "m_constSig", sig);
        setField(source, "m_typeConstraint", sourcePool.typeString());
        setField(source, "m_sJitName", "SourceJitProperty");

        var adopted = adopt(source, targetPool);

        assertSame(targetPool, adopted.getConstantPool());
        assertEquals(source.getName(), adopted.getName());
        assertNull(fieldValue(adopted, "m_type"));
        assertNull(fieldValue(adopted, "m_constSig"));
        assertNull(fieldValue(adopted, "m_typeConstraint"));
        assertNull(fieldValue(adopted, "m_sJitName"));
    }

    /**
     * FormalTypeChildConstant inherits PropertyConstant cache fields, but its format is different
     * and must survive adoption. A base property copy hook would silently corrupt this identity.
     */
    @Test
    public void adoptedFormalTypeChildPreservesFormatAndDropsInheritedCaches() throws Exception {
        var sourceFile = new FileStructure("source");
        var sourcePool = sourceFile.getConstantPool();
        var targetPool = new FileStructure("target").getConstantPool();
        var struct     = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Owner", null);
        var sig        = sourcePool.ensureSignatureConstant(
                "method", ConstantPool.NO_TYPES, ConstantPool.NO_TYPES);
        var method     = sourcePool.ensureMethodConstant(struct.getIdentityConstant(), sig);
        var parent     = sourcePool.ensureRegisterConstant(method, 0, "T");
        var source     = sourcePool.ensureFormalTypeChildConstant(parent, "Element");

        setField(source, "m_typeConstraint", sourcePool.typeObject());
        setField(source, "m_sJitName", "SourceFormalChildJitProperty");

        var adopted = adopt(source, targetPool);

        assertSame(Constant.Format.FormalTypeChild, adopted.getFormat());
        assertSame(targetPool, adopted.getConstantPool());
        assertEquals(source.getName(), adopted.getName());
        assertNull(fieldValue(adopted, "m_typeConstraint"));
        assertNull(fieldValue(adopted, "m_sJitName"));
    }

    /**
     * DynamicFormalConstant stores enough logical register identity to assemble and compare after
     * register allocation. Adoption must copy that stable identity while dropping the transient
     * compiler Register object, which belongs to the source compiler/method owner. Registered
     * type constants may still use TypeConstant sharing rules; they just must be valid for the
     * target pool.
     */
    @Test
    public void adoptedDynamicFormalDropsCompileTimeRegister() throws Exception {
        var sourceFile = new FileStructure("source");
        var sourcePool = sourceFile.getConstantPool();
        var struct     = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Owner", null);
        var targetPool = new FileStructure(sourceFile.getModule(), false).getConstantPool();
        var sig        = sourcePool.ensureSignatureConstant(
                "method", ConstantPool.NO_TYPES, ConstantPool.NO_TYPES);
        var method     = sourcePool.ensureMethodConstant(struct.getIdentityConstant(), sig);
        var formal     = sourcePool.ensurePropertyConstant(struct.getIdentityConstant(), "Element");
        var reg        = new Register(struct.getIdentityConstant().getType(), "value", 3);
        var source     = sourcePool.ensureDynamicFormal(method, reg, formal, "value");

        var adopted = adopt(source, targetPool);
        var registered = targetPool.register(source);

        assertNull(adopted.getRegister());
        assertEquals(reg.getIndex(), adopted.getRegisterIndex());
        assertSame(targetPool, registered.getConstantPool());
        assertNull(registered.getRegister());
        assertEquals(reg.getIndex(), registered.getRegisterIndex());
        assertSame(targetPool, registered.getFormalConstant().getConstantPool());
        var registeredType = (TypeConstant) fieldValue(registered, "m_typeReg");
        assertTrue(registeredType.isShared(targetPool));
    }

    /**
     * A dynamic formal whose register type names an unrelated module-local class cannot be moved
     * into another pool by copying references. The old shallow clone allowed that invalid foreign
     * type to sit inside the target constant until later linkage or runtime code tripped over it.
     */
    @Test
    public void dynamicFormalRejectsForeignRegisterTypeDuringAdoption() {
        var sourceFile = new FileStructure("source");
        var sourcePool = sourceFile.getConstantPool();
        var targetPool = new FileStructure("target").getConstantPool();
        var struct     = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Owner", null);
        var sig        = sourcePool.ensureSignatureConstant(
                "method", ConstantPool.NO_TYPES, ConstantPool.NO_TYPES);
        var method     = sourcePool.ensureMethodConstant(struct.getIdentityConstant(), sig);
        var formal     = sourcePool.ensurePropertyConstant(struct.getIdentityConstant(), "Element");
        var reg        = new Register(struct.getIdentityConstant().getType(), "value", 3);
        var source     = sourcePool.ensureDynamicFormal(method, reg, formal, "value");

        var error = assertThrows(IllegalStateException.class, () -> adopt(source, targetPool));

        assertTrue(error.getMessage().contains("foreign register type"));
    }

    /**
     * RegisterConstant serializes the register index, not the compiler Register object. Adoption
     * must therefore preserve the runtime index while dropping the source compiler register and
     * its owner/type state.
     */
    @Test
    public void adoptedRegisterConstantDropsCompileTimeRegister() {
        var sourcePool = new FileStructure("source").getConstantPool();
        var targetPool = new FileStructure("target").getConstantPool();
        var reg        = new Register(sourcePool.typeObject(), "value", 3);
        var source     = new RegisterConstant(sourcePool, reg);

        var adopted = adopt(source, targetPool);

        assertSame(targetPool, adopted.getConstantPool());
        assertNull(adopted.getRegister());
        assertEquals(reg.getIndex(), adopted.getRegisterIndex());
        assertEquals(targetPool.typeObject(), adopted.getType());
    }

    /**
     * An unknown compiler Register can still be assigned later. The old shallow clone copied that
     * moving object to another pool; dropping it would freeze the wrong index, so adoption now
     * rejects this shape until allocation has completed.
     */
    @Test
    public void registerConstantRejectsUnknownRegisterAdoption() {
        var sourcePool = new FileStructure("source").getConstantPool();
        var targetPool = new FileStructure("target").getConstantPool();
        var reg        = new Register(sourcePool.typeObject(), "value", (MethodStructure) null);
        var source     = new RegisterConstant(sourcePool, reg);

        var error = assertThrows(IllegalStateException.class, () -> adopt(source, targetPool));

        assertTrue(error.getMessage().contains("before register allocation"));
    }

    /**
     * MethodBindingConstant is a serialized frame-dependent descriptor, not a live runtime handle.
     * Adoption should reconstruct the method-binding value and let target registration adopt the
     * method identity, closing the old FrameDependentConstant default-clone fallback.
     */
    @Test
    public void adoptedMethodBindingConstantPreservesMethodIdentity() {
        var sourceFile = new FileStructure("source");
        var sourcePool = sourceFile.getConstantPool();
        var targetPool = new FileStructure(sourceFile.getModule(), false).getConstantPool();
        var struct     = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Owner", null);
        var sig        = sourcePool.ensureSignatureConstant(
                "method", ConstantPool.NO_TYPES, ConstantPool.NO_TYPES);
        var method     = sourcePool.ensureMethodConstant(struct.getIdentityConstant(), sig);
        var source     = new MethodBindingConstant(sourcePool, method);

        var adopted = adopt(source, targetPool);
        var registered = targetPool.register(source);

        assertSame(targetPool, adopted.getConstantPool());
        assertEquals(method.getName(), adopted.getMethodConstant().getName());
        assertSame(targetPool, registered.getConstantPool());
        assertSame(targetPool, registered.getMethodConstant().getConstantPool());
        assertEquals(method.getName(), registered.getMethodConstant().getName());
    }

    /**
     * ConditionalConstant carries a transient brute-force simulation slot that is not part of the
     * serialized predicate. A shallow adoption clone copied that warmed scratch value into a new pool;
     * explicit leaf reconstruction preserves the condition name and starts with clean simulation
     * state.
     */
    @Test
    public void adoptedConditionLeafDropsSimulatedLinkerScratchState() throws Exception {
        var sourcePool = new FileStructure("source").getConstantPool();
        var targetPool = new FileStructure("target").getConstantPool();
        var source     = sourcePool.ensureNamedCondition("debug");

        setField(source, "iTest", 23);

        var adopted = adopt(source, targetPool);

        assertSame(targetPool, adopted.getConstantPool());
        assertEquals(source.getName(), adopted.getName());
        assertEquals(0, testIndex(adopted));
    }

    /**
     * Multi-condition adoption must also rebuild the child graph through the target pool. This proves
     * the recursive registration path preserves the same predicate text while no terminal condition
     * keeps the source pool or the source's simulated-linker scratch values.
     */
    @Test
    public void registeredConditionGraphAdoptsChildrenAndDropsSimulationScratch()
            throws Exception {
        var sourcePool = new FileStructure("source").getConstantPool();
        var targetPool = new FileStructure("target").getConstantPool();
        var left       = sourcePool.ensureNamedCondition("debug");
        var right      = sourcePool.ensureNamedCondition("trace");
        var source     = sourcePool.ensureAllCondition(left, sourcePool.ensureNotCondition(right));

        source.terminalInfluences();
        setField(left, "iTest", 31);
        setField(right, "iTest", -32);

        var registered = targetPool.register(source);

        assertSame(targetPool, registered.getConstantPool());
        assertEquals(source.getValueString(), registered.getValueString());
        assertTrue(registered.terminals().stream()
                .allMatch(cond -> cond.getConstantPool() == targetPool && testIndex(cond) == 0));
        assertTrue(registered.terminals().stream().noneMatch(cond -> cond == left || cond == right));
    }

    /**
     * Array-backed constants participate in hash/equality and pool lookup, so their byte storage is
     * logical immutable value. Keeping the caller's byte[] lets ordinary single-threaded code mutate
     * an already-registered constant after construction.
     */
    @Test
    public void arrayBackedValueConstructorsDefensivelyCopyInputBytes() {
        var pool         = new FileStructure("source").getConstantPool();
        var byteStringIn = new byte[] {1, 2, 3};
        var floatNIn     = new byte[] {4, 5};
        var float128In   = new byte[16];

        float128In[0] = 6;

        var byteString = new UInt8ArrayConstant(pool, byteStringIn);
        var floatN     = new FPNConstant(pool, Constant.Format.FloatN, floatNIn);
        var float128   = new Float128Constant(pool, float128In);

        byteStringIn[0] = 31;
        floatNIn[0]     = 32;
        float128In[0]   = 33;

        assertArrayEquals(new byte[] {1, 2, 3}, byteString.getValue());
        assertArrayEquals(new byte[] {4, 5}, floatN.getValue());
        assertEquals(6, float128.getValue()[0]);
    }

    /**
     * Shallow adoption cloned the constant object but shared the final byte[] backing store. The
     * target-pool copy must have the same bytes and independent storage so source mutation cannot
     * change target hash/equality value after adoption.
     */
    @Test
    public void adoptedArrayBackedValueConstantsDoNotShareByteStorage() {
        var sourcePool = new FileStructure("source").getConstantPool();
        var targetPool = new FileStructure("target").getConstantPool();
        var byteString = sourcePool.ensureByteStringConstant(new byte[] {1, 2, 3});
        var floatN     = sourcePool.ensureFloatNConstant(new byte[] {4, 5});
        var float128   = sourcePool.ensureFloat128Constant(new byte[16]);

        float128.getValue()[0] = 6;

        var adoptedByteString = adopt(byteString, targetPool);
        var adoptedFloatN     = adopt(floatN, targetPool);
        var adoptedFloat128   = adopt(float128, targetPool);

        byteString.getValue()[0] = 31;
        floatN.getValue()[0]     = 32;
        float128.getValue()[0]   = 33;

        assertArrayEquals(new byte[] {1, 2, 3}, adoptedByteString.getValue());
        assertArrayEquals(new byte[] {4, 5}, adoptedFloatN.getValue());
        assertEquals(6, adoptedFloat128.getValue()[0]);
    }

    /**
     * Immutable scalar values do not need owner-local caches, but they still must not depend on the
     * base shallow-clone adoption fallback. Explicit reconstruction preserves the old interning and
     * equality semantics while failing closed if a future scalar grows owner-derived helper state.
     */
    @Test
    public void adoptedImmutableScalarValueConstantsPreserveLogicalValueInTargetPool() {
        var sourcePool = new FileStructure("source").getConstantPool();
        var targetPool = new FileStructure("target").getConstantPool();
        var scalars    = List.<Constant>of(
                sourcePool.ensureByteConstant(Constant.Format.Bit, 1),
                sourcePool.ensureByteConstant(Constant.Format.Nibble, 12),
                sourcePool.ensureByteConstant(Constant.Format.Int8, -7),
                sourcePool.ensureByteConstant(Constant.Format.UInt8, 250),
                sourcePool.ensureCharConstant('X'),
                sourcePool.ensureIntConstant(123456789L),
                sourcePool.ensureStringConstant("owner-local"),
                sourcePool.ensureRegExConstant("a.*b", 3),
                sourcePool.ensureDecConstant(new Decimal64(123L)),
                sourcePool.ensureFloat8e4Constant(0.0f),
                sourcePool.ensureFloat8e5Constant(Float.POSITIVE_INFINITY),
                sourcePool.ensureBFloat16Constant(1.25f),
                sourcePool.ensureFloat16Constant(1.5f),
                sourcePool.ensureFloat32Constant(3.25f),
                sourcePool.ensureFloat64Constant(6.5d));

        scalars.forEach(source -> {
            var adopted = adopt(source, targetPool);

            assertNotSame(source, adopted);
            assertSame(targetPool, adopted.getConstantPool());
            assertEquals(source.getClass(), adopted.getClass());
            assertEquals(source.getFormat(), adopted.getFormat());
            assertEquals(source.getValueString(), adopted.getValueString());
            assertEquals(source, adopted);
        });
    }

    /**
     * Array and map constants use array elements as immutable hash/equality value. The public
     * constructors must copy caller arrays immediately; otherwise even single-threaded caller code
     * can rewrite a constant after construction. The map view must also keep its backing arrays
     * private so callers cannot cast the read-only wrapper and mutate through public fields.
     */
    @Test
    public void compositeValueConstructorsDefensivelyCopyInputArrays() throws Exception {
        var pool        = new FileStructure("source").getConstantPool();
        var original    = pool.ensureStringConstant("original");
        var replacement = pool.ensureStringConstant("replacement");
        var value       = pool.ensureIntConstant(1);
        var valueOther  = pool.ensureIntConstant(2);
        var arrayType   = pool.ensureArrayType(pool.typeString());
        var mapType     = pool.ensureMapType(pool.typeString(), pool.typeInt64());
        var arrayValues = new Constant[] {original};
        var mapKeys     = new Constant[] {original};
        var mapValues   = new Constant[] {value};

        var array = new ArrayConstant(pool, Constant.Format.Array, arrayType, arrayValues);
        var map   = new MapConstant(pool, Constant.Format.Map, mapType, mapKeys, mapValues);

        arrayValues[0] = replacement;
        mapKeys[0]     = replacement;
        mapValues[0]   = valueOther;

        assertSame(original, array.getValue()[0]);
        assertEquals(value, map.getValue().get(original));

        var keysField   = MapConstant.ROMap.class.getDeclaredField("ak");
        var valuesField = MapConstant.ROMap.class.getDeclaredField("av");

        assertTrue(Modifier.isPrivate(keysField.getModifiers()));
        assertTrue(Modifier.isFinal(keysField.getModifiers()));
        assertTrue(Modifier.isPrivate(valuesField.getModifiers()));
        assertTrue(Modifier.isFinal(valuesField.getModifiers()));
    }

    /**
     * Composite value adoption creates a target-owned shell and then lets target registration adopt
     * child constants through the normal recursive path. This preserves old interning behavior while
     * avoiding source-owner array/map containers that registration would mutate in place.
     */
    @Test
    public void registeredCompositeValueConstantsAdoptChildrenIntoTargetPool() {
        var sourcePool = new FileStructure("source").getConstantPool();
        var targetPool = new FileStructure("target").getConstantPool();
        var key        = sourcePool.ensureStringConstant("key");
        var value      = sourcePool.ensureIntConstant(7);
        var arrayType  = sourcePool.ensureArrayType(sourcePool.typeString());
        var mapType    = sourcePool.ensureMapType(sourcePool.typeString(), sourcePool.typeInt64());
        var array      = sourcePool.ensureArrayConstant(arrayType, new Constant[] {key});
        var map        = sourcePool.ensureMapConstant(mapType, Map.of(key, value));
        var range      = sourcePool.ensureRangeConstant(value, true, sourcePool.ensureIntConstant(9), false);

        var registeredArray = targetPool.register(array);
        var registeredMap   = targetPool.register(map);
        var registeredRange = targetPool.register(range);

        assertSame(targetPool, registeredArray.getConstantPool());
        // TypeConstant adoption is a separate family-wide clone-free wave; this test only proves
        // the array value container and child values are no longer source-owned after registration.
        assertEquals(array.getType().getValueString(), registeredArray.getType().getValueString());
        Arrays.stream(registeredArray.getValue())
                .forEach(constant -> assertSame(targetPool, constant.getConstantPool()));

        assertSame(targetPool, registeredMap.getConstantPool());
        assertEquals(map.getType().getValueString(), registeredMap.getType().getValueString());
        registeredMap.getValue().forEach((registeredKey, registeredValue) -> {
            assertSame(targetPool, registeredKey.getConstantPool());
            assertSame(targetPool, registeredValue.getConstantPool());
        });

        assertSame(targetPool, registeredRange.getConstantPool());
        assertSame(targetPool, registeredRange.getFirst().getConstantPool());
        assertSame(targetPool, registeredRange.getLast().getConstantPool());
        assertTrue(registeredRange.isFirstExcluded());
        assertFalse(registeredRange.isLastExcluded());
    }

    /**
     * LiteralConstant keeps parsed helper objects such as PackedInteger in m_oVal. Shallow adoption
     * copied that cache into another pool even though it is not serialized literal value. The
     * adopted literal keeps the same text/format and recomputes parsed state only if needed.
     */
    @Test
    public void adoptedLiteralConstantsDropParsedCacheAndRetainText() throws Exception {
        var sourcePool = new FileStructure("source").getConstantPool();
        var targetPool = new FileStructure("target").getConstantPool();
        var literal    = sourcePool.ensureLiteralConstant(Constant.Format.IntLiteral, "123");

        literal.getPackedInteger();

        var adopted   = adopt(literal, targetPool);
        var registered = targetPool.register(literal);

        assertNotNull(fieldValue(literal, "m_oVal"));
        assertSame(targetPool, adopted.getConstantPool());
        assertEquals(literal.getFormat(), adopted.getFormat());
        assertEquals(literal.getValue(), adopted.getValue());
        assertNull(fieldValue(adopted, "m_oVal"));
        assertSame(targetPool, registered.getStringConstant().getConstantPool());
    }

    /**
     * VersionConstant is a LiteralConstant subclass. A family-level literal copy would accidentally
     * return a plain LiteralConstant, so the version subclass must reconstruct itself explicitly.
     */
    @Test
    public void adoptedVersionConstantsPreserveConcreteTypeAndLiteralString() {
        var sourcePool = new FileStructure("source").getConstantPool();
        var targetPool = new FileStructure("target").getConstantPool();
        var version    = new Version("1.2.3");
        var source     = sourcePool.ensureVersionConstant(version);

        var adopted    = adopt(source, targetPool);
        var registered = targetPool.register(source);

        assertEquals(VersionConstant.class, adopted.getClass());
        assertSame(targetPool, adopted.getConstantPool());
        assertEquals(version.toString(), adopted.getVersion().toString());
        assertSame(targetPool, registered.getStringConstant().getConstantPool());
    }

    /**
     * DecimalAutoConstant delegates its logical value to a DecimalConstant child. Adoption must not
     * shallow-copy that child reference as final owner state; target registration has to adopt it
     * through the normal recursive constant path.
     */
    @Test
    public void registeredDecimalAutoConstantsAdoptDelegateIntoTargetPool() throws Exception {
        var sourcePool = new FileStructure("source").getConstantPool();
        var targetPool = new FileStructure("target").getConstantPool();
        var source     = sourcePool.ensureDecAConstant(new Decimal64(123L));

        var registered = targetPool.register(source);
        var delegate   = fieldValue(registered, "m_dec");

        assertSame(targetPool, registered.getConstantPool());
        assertEquals(source.getValueString(), registered.getValueString());
        assertSame(targetPool, ((DecimalConstant) delegate).getConstantPool());
    }

    /**
     * Match-any values are keyed by a TypeConstant locator. Adoption must rebuild the wildcard shell
     * without cloning helper state, then let target registration intern the shareable type key under
     * the destination pool exactly as the old registration cache expected.
     */
    @Test
    public void registeredMatchAnyConstantAdoptsShareableTypeKeyIntoTargetPool() {
        var sourceFile = new FileStructure("source");
        var sourcePool = sourceFile.getConstantPool();
        var struct     = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Owner", null);
        var targetPool = new FileStructure(sourceFile.getModule(), false).getConstantPool();
        var source     = sourcePool.ensureMatchAnyConstant(struct.getIdentityConstant().getType());

        var registered = targetPool.register(source);

        assertSame(targetPool, registered.getConstantPool());
        assertSame(targetPool, registered.getType().getConstantPool());
        assertEquals(source.getValueString(), registered.getValueString());
        assertSame(registered, targetPool.ensureMatchAnyConstant(registered.getType()));
    }

    /**
     * A match-any value whose type belongs to an unrelated module cannot be moved into another pool.
     * Otherwise the target pool would publish a value key that still names source-owner type state.
     */
    @Test
    public void matchAnyRejectsForeignTypeDuringAdoption() {
        var sourceFile = new FileStructure("source");
        var sourcePool = sourceFile.getConstantPool();
        var targetPool = new FileStructure("target").getConstantPool();
        var struct     = sourceFile.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Owner", null);
        var source     = sourcePool.ensureMatchAnyConstant(struct.getIdentityConstant().getType());

        var error = assertThrows(IllegalStateException.class, () -> targetPool.register(source));

        assertTrue(error.getMessage().contains("foreign type"));
    }

    /**
     * A live ObjectHandle is owner-specific runtime state, not logical constant data. Moving an
     * already-owned handle constant to another pool must fail instead of leaking the source owner.
     */
    @Test
    public void ownedHandleConstantCannotMoveToAnotherPool() {
        ConstantPool sourcePool = new FileStructure("source").getConstantPool();
        ConstantPool targetPool = new FileStructure("target").getConstantPool();

        HandleConstant unowned = new HandleConstant(ObjectHandle.DEFAULT);
        HandleConstant source = adopt(unowned, sourcePool);

        assertSame(sourcePool, source.getConstantPool());

        IllegalStateException error = assertThrows(
                IllegalStateException.class, () -> adopt(source, targetPool));
        assertTrue(error.getMessage().contains("live ObjectHandle"));
    }

    /**
     * FSNodeConstant caches source-pool path handles. Adoption must drop those handles so a target
     * pool cannot observe filesystem handles owned by the source pool.
     */
    @Test
    public void adoptedFSNodeConstantDropsSourcePoolPathCache() {
        ConstantPool sourcePool = new FileStructure("source").getConstantPool();
        ConstantPool targetPool = new FileStructure("target").getConstantPool();
        FileTime     created    = FileTime.fromMillis(1);
        FileTime     modified   = FileTime.fromMillis(2);

        byte[] contents = {1, 2, 3};
        FSNodeConstant source = sourcePool.ensureFileConstant("demo.txt", created, modified, contents);
        LiteralConstant sourcePath = source.getPathConstant();

        FSNodeConstant adopted = adopt(source, targetPool);
        LiteralConstant adoptedPath = adopted.getPathConstant();

        assertSame(sourcePool, sourcePath.getConstantPool());
        assertSame(targetPool, adopted.getConstantPool());
        assertSame(targetPool, adoptedPath.getConstantPool());
        assertNotSame(sourcePath, adoptedPath);
        assertSame(adoptedPath, adopted.getPathConstant());
    }

    /**
     * Base Constant adoption is no longer "clone unless somebody remembered to override it". A new
     * constant class must either provide an explicit adoption implementation or opt in to the
     * transitional default-clone policy with a local explanation.
     */
    @Test
    public void defaultAdoptionCloneRequiresExplicitPolicy() {
        var targetPool = new FileStructure("target").getConstantPool();
        var source     = new NoPolicyConstant(new FileStructure("source").getConstantPool());

        var error = assertThrows(IllegalStateException.class, () -> adopt(source, targetPool));

        assertTrue(error.getMessage().contains("default adoption-clone policy"));
    }

    /**
     * Adoption must go through one owner-transfer wrapper. The old design allowed each subclass to
     * override {@code adoptedBy(...)} directly, which made it easy to copy runtime/helper state and
     * forget the common owner/ref checks. The reviewed special cases now implement only the logical
     * copy hook.
     */
    @Test
    public void adoptionWrapperIsFinalAndSpecialCasesUseCopyHook()
            throws Exception {
        var adoptedBy = Constant.class.getDeclaredMethod("adoptedBy", ConstantPool.class);

        assertTrue(Modifier.isFinal(adoptedBy.getModifiers()));

        Set.of(Annotation.class,
               AllCondition.class,
               AnnotatedTypeConstant.class,
               AnyCondition.class,
               AccessTypeConstant.class,
               ArrayConstant.class,
               AnonymousClassTypeConstant.class,
               BFloat16Constant.class,
               ByteConstant.class,
               CastTypeConstant.class,
               CharConstant.class,
               DecimalAutoConstant.class,
               DecimalConstant.class,
               DifferenceTypeConstant.class,
               FSNodeConstant.class,
               DynamicFormalConstant.class,
               FPNConstant.class,
               FileStoreConstant.class,
               Float128Constant.class,
               Float16Constant.class,
               Float32Constant.class,
               Float64Constant.class,
               Float8e4Constant.class,
               Float8e5Constant.class,
               FormalTypeChildConstant.class,
               HandleConstant.class,
               IntConstant.class,
               InnerChildTypeConstant.class,
               IntersectionTypeConstant.class,
               ImmutableTypeConstant.class,
               LiteralConstant.class,
               MapConstant.class,
               MatchAnyConstant.class,
               MethodBindingConstant.class,
               MethodConstant.class,
               NamedCondition.class,
               NotCondition.class,
               ParameterizedTypeConstant.class,
               PendingTypeConstant.class,
               PresentCondition.class,
               PropertyConstant.class,
               PropertyClassTypeConstant.class,
               RangeConstant.class,
               RegExConstant.class,
               RegisterConstant.class,
               RecursiveTypeConstant.class,
               SignatureConstant.class,
               ServiceTypeConstant.class,
               SingletonConstant.class,
               StringConstant.class,
               TerminalTypeConstant.class,
               TypeParameterConstant.class,
               TypeSequenceTypeConstant.class,
               UInt8ArrayConstant.class,
               UnionTypeConstant.class,
               UnresolvedTypeConstant.class,
               VersionConstant.class,
               VersionMatchesCondition.class,
               VersionedCondition.class,
               VirtualChildTypeConstant.class)
                .forEach(clz -> {
                    assertDoesNotThrow(() -> clz.getDeclaredMethod(
                            "copyForAdoption", Constant.AdoptionContext.class));
                    assertThrows(NoSuchMethodException.class,
                            () -> clz.getDeclaredMethod("adoptedBy", ConstantPool.class));
                });
    }

    /**
     * The adoption validator must detect the exact bad default-clone shape: a copied helper
     * reference that still belongs to the source constant after ownership changes.
     */
    @Test
    public void adoptionValidatorReportsDefaultCloneCopiedHelperReference() {
        ConstantPool sourcePool = new FileStructure("source").getConstantPool();
        ConstantPool targetPool = new FileStructure("target").getConstantPool();

        DiagnosticConstant source  = new DiagnosticConstant(sourcePool);
        DiagnosticConstant adopted = adopt(source, targetPool);

        ConstantAdoptionValidator.Validation validation =
                ConstantAdoptionValidator.validate(source, adopted);

        assertFalse(validation.isValid());
        assertEquals(1, validation.sharedReferences().size());
        assertTrue(validation.message().contains("DiagnosticConstant.helper"));
        assertTrue(validation.message().contains("AtomicReference"));
    }

    /**
     * Runtime handles are owner-bearing state. A default-cloned constant that carries an arbitrary
     * ObjectHandle would move live runtime state by reference while pretending to only adopt
     * logical constant value into a new pool.
     */
    @Test
    public void adoptionValidatorRejectsDefaultCloneCopiedRuntimeHandle() {
        var sourcePool = new FileStructure("source").getConstantPool();
        var targetPool = new FileStructure("target").getConstantPool();

        var source  = new RuntimeHandleConstant(sourcePool, new TestHandle());
        var adopted = adopt(source, targetPool);

        var validation = ConstantAdoptionValidator.validate(source, adopted);

        assertFalse(validation.isValid());
        assertEquals(1, validation.sharedReferences().size());
        assertTrue(validation.message().contains("RuntimeHandleConstant.handle"));
        assertTrue(validation.message().contains("TestHandle"));
    }

    /**
     * HandleConstant is the one legacy exception: runtime annotation construction creates a fresh
     * unowned handle constant and immediately registers that handle in the current pool. The final
     * adoption wrapper still rejects second adoption of the now-owned constant.
     */
    @Test
    public void adoptionValidatorAllowsFreshHandleConstantFirstRegistration() {
        var targetPool = new FileStructure("target").getConstantPool();

        var source  = new HandleConstant(new TestHandle());
        var adopted = ((Constant) source).adoptedBy(targetPool);

        var validation = ConstantAdoptionValidator.validate(source, adopted);

        assertTrue(validation.isValid(), validation::message);
    }

    /**
     * Registration with adoption validation enabled must reject bad default clones before they are
     * published into the target pool's lookup structures.
     */
    @Test
    public void registerFailsOnBadDefaultCloneWhenAdoptionValidationIsEnabled() {
        ConstantPool sourcePool = new FileStructure("source").getConstantPool();
        ConstantPool targetPool = new FileStructure("target").getConstantPool();
        DiagnosticConstant source = new DiagnosticConstant(sourcePool);

        withAdoptionValidation(() -> {
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> targetPool.register(source));
            assertTrue(error.getMessage().contains("Invalid XVM constant adoption"));
            assertTrue(error.getMessage().contains("DiagnosticConstant.helper"));
        });
    }

    @SuppressWarnings("unchecked")
    private static <T extends Constant> T adopt(T constant, ConstantPool pool) {
        return (T) constant.adoptedBy(pool);
    }

    private static ClassConstant classId(ClassStructure struct) {
        return ClassConstant.class.cast(struct.getIdentityConstant());
    }

    private static TypedefConstant typedefId(TypedefStructure struct) {
        return TypedefConstant.class.cast(struct.getIdentityConstant());
    }

    private static int testIndex(ConditionalConstant condition) {
        try {
            return fieldValue(condition, "iTest");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void withAdoptionValidation(Runnable action) {
        String property = ConstantAdoptionValidator.VALIDATE_PROPERTY;
        String previous = System.getProperty(property);
        System.setProperty(property, "true");
        try {
            action.run();
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T fieldValue(Object target, String... names)
            throws ReflectiveOperationException {
        Field field = findField(target.getClass(), names);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private static void setField(Object target, String name, Object value)
            throws ReflectiveOperationException {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Field findField(Class<?> clz, String... names)
            throws NoSuchFieldException {
        Set<String> setNames = Set.of(names);
        for (Class<?> current = clz; current != null; current = current.getSuperclass()) {
            Field field = Arrays.stream(current.getDeclaredFields())
                    .filter(candidate -> setNames.contains(candidate.getName()))
                    .findFirst()
                    .orElse(null);
            if (field != null) {
                return field;
            }
        }
        throw new NoSuchFieldException(String.join("/", names));
    }

    private static final class DiagnosticConstant
            extends Constant {
        private final AtomicReference<Object> helper = new AtomicReference<>();

        private DiagnosticConstant(ConstantPool pool) {
            super(pool);
        }

        @Override
        public Format getFormat() {
            return Format.IntLiteral;
        }

        @Override
        public String getValueString() {
            return "diagnostic";
        }

        @Override
        public String getDescription() {
            return "diagnostic";
        }

        @Override
        protected int compareDetails(Constant that) {
            return 0;
        }

        @Override
        protected int computeHashCode() {
            return 1;
        }

        @Override
        protected boolean allowsDefaultAdoptionClone() {
            return true;
        }
    }

    private static final class RuntimeHandleConstant
            extends Constant {
        private final ObjectHandle handle;

        private RuntimeHandleConstant(ConstantPool pool, ObjectHandle handle) {
            super(pool);
            this.handle = handle;
        }

        @Override
        public Format getFormat() {
            return Format.IntLiteral;
        }

        @Override
        public String getValueString() {
            return "runtime-handle";
        }

        @Override
        public String getDescription() {
            return getValueString();
        }

        @Override
        protected int compareDetails(Constant that) {
            return 0;
        }

        @Override
        protected int computeHashCode() {
            return 1;
        }

        @Override
        protected boolean allowsDefaultAdoptionClone() {
            return true;
        }
    }

    private static final class NoPolicyConstant
            extends Constant {
        private NoPolicyConstant(ConstantPool pool) {
            super(pool);
        }

        @Override
        public Format getFormat() {
            return Format.IntLiteral;
        }

        @Override
        public String getValueString() {
            return "no-policy";
        }

        @Override
        public String getDescription() {
            return getValueString();
        }

        @Override
        protected int compareDetails(Constant that) {
            return 0;
        }

        @Override
        protected int computeHashCode() {
            return 1;
        }
    }

    private static final class TestHandle
            extends ObjectHandle {
        private TestHandle() {
            super(null);
        }
    }
}
