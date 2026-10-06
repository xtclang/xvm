package org.xvm.xdk;

import java.io.IOException;
import java.io.InputStream;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Composition;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.Component.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.FileStructure;

import org.xvm.asm.constants.TerminalTypeConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.util.Auto;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for layer-one canonicalization of {@link TypeConstant}s.
 */
public class TypeConstantL1SpecializationTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("nonParameterizedTypes")
    public void nonParameterizedTypeIsCanonical(TypeConstant type) {
        assertInstanceOf(TerminalTypeConstant.class, type);
        assertFalse(type.isAnnotated());
        ClassStructure clz = (ClassStructure) type.getSingleUnderlyingClass(true).getComponent();
        assertFalse(clz.isParameterizedDeep());

        assertTrue(type.isCanonicalType());
        assertSame(type, type.getCanonicalType());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unparameterizedTypesOfParameterizedClasses")
    public void unparameterizedTypeOfParameterizedClassIsCanonical(
            TypeConstant type, TypeConstant expected) {
        assertInstanceOf(TerminalTypeConstant.class, type);
        assertFalse(type.isParamsSpecified());
        assertFalse(type.isAnnotated());
        ClassStructure clz = (ClassStructure) type.getSingleUnderlyingClass(true).getComponent();
        assertTrue(clz.isParameterized());

        assertTrue(type.isCanonicalType());
        assertSame(expected, type.getCanonicalType());
    }

    @ParameterizedTest(name = "{0}[]")
    @MethodSource("arrayElementTypes")
    public void arrayCanonicalization(TypeConstant typeElement, TypeConstant typeCanonicalElement,
            boolean fCanonical) {
        ConstantPool pool         = typeElement.getConstantPool();
        TypeConstant type         = pool.ensureArrayType(typeElement);
        TypeConstant typeExpected = pool.ensureArrayType(typeCanonicalElement);

        try (Auto ignored = ConstantPool.withPool(pool)) {
            assertAll(
                    () -> assertEquals(conditionalIncorporations(typeExpected),
                            conditionalIncorporations(type), "conditional incorporations"),
                    () -> assertEquals(fCanonical, type.isCanonicalType(), "isCanonicalType()"),
                    () -> assertCanonical(type, typeExpected));
        }
    }

    @Test
    public void arrayCasesCoverEveryConditionalIncorporationCombination()
            throws IOException {
        Set<Set<String>> actual = new HashSet<>();
        try (Stream<Arguments> cases = arrayElementTypes()) {
            for (Arguments args : cases.toList()) {
                TypeConstant typeElement = (TypeConstant) args.get()[0];
                ConstantPool pool        = typeElement.getConstantPool();
                try (Auto ignored = ConstantPool.withPool(pool)) {
                    actual.add(conditionalIncorporations(pool.ensureArrayType(typeElement)));
                }
            }
        }

        List<String> independent = List.of("HashableArray", "OrderableArray", "FreezableArray");
        Set<Set<String>> expected = new HashSet<>();
        for (int mask = 0; mask < (1 << independent.size()); ++mask) {
            Set<String> combination = new HashSet<>();
            for (int i = 0; i < independent.size(); ++i) {
                if ((mask & (1 << i)) != 0) {
                    combination.add(independent.get(i));
                }
            }
            expected.add(combination);
        }

        // these class constraints imply all three independent conditions; Bit is not a Number
        // Byte and Nibble extend IntNumber, while IntNumber and FPNumber are separate Number branches
        for (Set<String> specialized : List.of(
                Set.of("BitArray"),
                Set.of("NumberArray"),
                Set.of("NumberArray", "IntNumberArray"),
                Set.of("NumberArray", "IntNumberArray", "ByteArray"),
                Set.of("NumberArray", "IntNumberArray", "NibbleArray"),
                Set.of("NumberArray", "FPNumberArray"))) {
            Set<String> combination = new HashSet<>(independent);
            combination.addAll(specialized);
            expected.add(combination);
        }

        assertEquals(expected, actual);
    }

    @Test
    public void classesWithoutConditionalIncorporationsKeepBareTypes()
            throws IOException {
        ConstantPool pool = loadConstantPool();
        try (Auto ignored = ConstantPool.withPool(pool)) {
            for (TypeConstant bare : List.of(pool.typeList(), pool.typeTuple(),
                    pool.ensureEcstasyTypeConstant("maps.KeyBasedMap"))) {
                assertSame(bare, bare.getCanonicalType());
                TypeConstant parameterized = bare.adoptParameters(pool,
                        new TypeConstant[] {pool.typeString()});
                assertSame(bare, parameterized.getCanonicalType());
                assertFalse(parameterized.isCanonicalType());
            }
        }
    }

    @Test
    public void listMapIncludesVirtualChildConditions()
            throws IOException {
        ConstantPool pool = loadConstantPool();
        TypeConstant map = pool.ensureEcstasyTypeConstant("maps.ListMap");
        TypeConstant object = pool.typeObject();
        TypeConstant immutable = object.freeze();
        TypeConstant hashable = pool.typeHashable().freeze();
        TypeConstant shareable = pool.ensureTypedefConstant(
                pool.clzService(), "Shareable").getReferredToType();
        try (Auto ignored = ConstantPool.withPool(pool)) {
            assertCanonical(map.adoptParameters(pool, new TypeConstant[] {object, object}),
                    map.adoptParameters(pool, new TypeConstant[] {object, object}));
            assertCanonical(map.adoptParameters(pool, new TypeConstant[] {object, pool.typeString()}),
                    map.adoptParameters(pool, new TypeConstant[] {object, shareable}));
            assertCanonical(map.adoptParameters(pool, new TypeConstant[] {immutable, object}),
                    map.adoptParameters(pool, new TypeConstant[] {shareable, object}));
            assertCanonical(map.adoptParameters(pool, new TypeConstant[] {immutable, pool.typeString()}),
                    map.adoptParameters(pool, new TypeConstant[] {immutable, shareable}));
            assertCanonical(map.adoptParameters(pool, new TypeConstant[] {pool.typeString(), object}),
                    map.adoptParameters(pool, new TypeConstant[] {hashable, object}));
            assertCanonical(map.adoptParameters(pool,
                    new TypeConstant[] {pool.typeString(), pool.typeInt64()}),
                    map.adoptParameters(pool, new TypeConstant[] {hashable, shareable}));
        }
    }

    @Test
    public void multipleParameterConditionsAreConjunctions()
            throws IOException {
        ConstantPool pool = testPool();
        ClassStructure base = parameterizedClass(pool, "Base", Format.CLASS, "Key");
        base.addTypeParam("Value", pool.typeObject());
        ClassStructure mixin = parameterizedClass(pool, "Mixin", Format.MIXIN, "K");
        mixin.addTypeParam("V", pool.typeObject());
        Map<String, TypeConstant> conditions = new LinkedHashMap<>();
        conditions.put("K", pool.typeObject().freeze());
        conditions.put("V", pool.typeFreezable());
        base.addIncorporates(mixin.getIdentityConstant().getType().adoptParameters(pool,
                base.getFormalType().getParamTypesArray()), conditions);

        TypeConstant bare = base.getIdentityConstant().getType();
        TypeConstant unspecialized = base.getNormalizedType();
        try (Auto ignored = ConstantPool.withPool(pool)) {
            assertCanonical(bare.adoptParameters(pool,
                    new TypeConstant[] {pool.typeString(), pool.typeObject()}), unspecialized);
            assertCanonical(bare.adoptParameters(pool,
                    new TypeConstant[] {pool.typeObject(), pool.typeString()}), unspecialized);
            assertCanonical(bare.adoptParameters(pool,
                    new TypeConstant[] {pool.typeString(), pool.typeString()}),
                    bare.adoptParameters(pool,
                            new TypeConstant[] {pool.typeObject().freeze(), pool.typeFreezable()}));
        }
    }

    @Test
    public void conditionalMixinsAreFollowedOnlyWhenApplicable()
            throws IOException {
        ConstantPool pool = testPool();
        ClassStructure base = parameterizedClass(pool, "Base", Format.CLASS, "T");
        ClassStructure outer = parameterizedClass(pool, "Outer", Format.MIXIN, "U");
        ClassStructure inner = parameterizedClass(pool, "Inner", Format.MIXIN, "V");
        outer.addIncorporates(inner.getIdentityConstant().getType().adoptParameters(pool,
                outer.getFormalType().getParamTypesArray()), Map.of("V", pool.typeOrderable()));
        base.addIncorporates(outer.getIdentityConstant().getType().adoptParameters(pool,
                base.getFormalType().getParamTypesArray()), Map.of("U", pool.typeHashable()));

        try (Auto ignored = ConstantPool.withPool(pool)) {
            TypeConstant bare = base.getIdentityConstant().getType();
            assertCanonical(bare.adoptParameters(pool, new TypeConstant[] {pool.typeOrderable()}),
                    bare.adoptParameters(pool, new TypeConstant[] {pool.typeObject()}));
            assertCanonical(bare.adoptParameters(pool, new TypeConstant[] {pool.typeString()}),
                    bare.adoptParameters(pool, new TypeConstant[] {
                            pool.ensureIntersectionTypeConstant(pool.typeHashable(), pool.typeOrderable())}));
        }
    }

    @Test
    public void inheritedConditionsFollowRenamedParameters()
            throws IOException {
        ConstantPool pool = testPool();
        ClassStructure base = parameterizedClass(pool, "Base", Format.CLASS, "T");
        addCondition(pool, base, base.getFormalType().getParamType(0), pool.typeNumber());
        ClassStructure derived = parameterizedClass(pool, "Derived", Format.CLASS, "U");
        derived.addContribution(Composition.Extends, base.getIdentityConstant().getType()
                .adoptParameters(pool, derived.getFormalType().getParamTypesArray()));
        TypeConstant bare = derived.getIdentityConstant().getType();
        try (Auto ignored = ConstantPool.withPool(pool)) {
            assertCanonical(bare.adoptParameters(pool, new TypeConstant[] {pool.typeInt64()}),
                    bare.adoptParameters(pool, new TypeConstant[] {pool.typeNumber()}));
        }
    }

    @Test
    public void unconditionalMixinsAreFollowedRecursively()
            throws IOException {
        ConstantPool pool = testPool();
        ClassStructure base = parameterizedClass(pool, "Base", Format.CLASS, "T");
        ClassStructure outer = parameterizedClass(pool, "Outer", Format.MIXIN, "U");
        ClassStructure inner = parameterizedClass(pool, "Inner", Format.MIXIN, "V");
        addCondition(pool, inner, inner.getFormalType().getParamType(0), pool.typeNumber());
        outer.addIncorporates(inner.getIdentityConstant().getType().adoptParameters(pool,
                outer.getFormalType().getParamTypesArray()), null);
        base.addIncorporates(outer.getIdentityConstant().getType().adoptParameters(pool,
                base.getFormalType().getParamTypesArray()), null);

        TypeConstant bare = base.getIdentityConstant().getType();
        try (Auto ignored = ConstantPool.withPool(pool)) {
            assertCanonical(bare.adoptParameters(pool, new TypeConstant[] {pool.typeInt64()}),
                    bare.adoptParameters(pool, new TypeConstant[] {pool.typeNumber()}));
        }
    }

    @Test
    public void declaredParameterConstraintsArePreserved()
            throws IOException {
        ConstantPool pool = testPool();
        ClassStructure base = parameterizedClass(pool, "Base", Format.CLASS, "T");
        TypeConstant formal = base.getFormalType().getParamType(0);
        base.addTypeParam("U", formal);
        addCondition(pool, base, formal, pool.typeNumber());
        TypeConstant bare = base.getIdentityConstant().getType();
        try (Auto ignored = ConstantPool.withPool(pool)) {
            assertCanonical(bare.adoptParameters(pool,
                    new TypeConstant[] {pool.typeInt64(), pool.typeInt64()}),
                    bare.adoptParameters(pool,
                            new TypeConstant[] {pool.typeNumber(), pool.typeNumber()}));
        }
    }

    @Test
    public void virtualGrandchildConditionsConstrainParentParameters()
            throws IOException {
        ConstantPool pool = testPool();
        ClassStructure base = parameterizedClass(pool, "Base", Format.CLASS, "T");
        ClassStructure child = base.createClass(Access.PUBLIC, Format.CLASS, "Child", null);
        ClassStructure grandchild = child.createClass(Access.PUBLIC, Format.CLASS, "Grandchild", null);
        grandchild.addTypeParam("U", base.getFormalType().getParamType(0));
        addCondition(pool, grandchild, grandchild.getFormalType().getParamType(0), pool.typeNumber());

        TypeConstant bare = base.getIdentityConstant().getType();
        try (Auto ignored = ConstantPool.withPool(pool)) {
            assertCanonical(bare.adoptParameters(pool, new TypeConstant[] {pool.typeInt64()}),
                    bare.adoptParameters(pool, new TypeConstant[] {pool.typeNumber()}));
            assertCanonical(bare.adoptParameters(pool, new TypeConstant[] {pool.typeString()}),
                    bare.adoptParameters(pool, new TypeConstant[] {pool.typeObject()}));
        }
    }

    @Test
    public void virtualChildCanChooseAParameterToCompleteAParentCondition()
            throws IOException {
        ConstantPool pool = testPool();
        ClassStructure parent = parameterizedClass(pool, "Parent", Format.CLASS, "T");
        ClassStructure child = parent.createClass(Access.PUBLIC, Format.CLASS, "Child", null);
        child.addTypeParam("U", pool.typeObject());
        ClassStructure mixin = parameterizedClass(pool, "Conditional", Format.MIXIN, "T");
        mixin.addTypeParam("U", pool.typeObject());

        Map<String, TypeConstant> constraints = new LinkedHashMap<>();
        constraints.put("T", pool.typeNumber());
        constraints.put("U", pool.typeHashable());
        child.addIncorporates(mixin.getIdentityConstant().getType().adoptParameters(pool,
                new TypeConstant[] {parent.getFormalType().getParamType(0),
                        child.getFormalType().getParamType(0)}), constraints);

        TypeConstant bare = parent.getIdentityConstant().getType();
        try (Auto ignored = ConstantPool.withPool(pool)) {
            assertCanonical(bare.adoptParameters(pool, new TypeConstant[] {pool.typeInt64()}),
                    bare.adoptParameters(pool, new TypeConstant[] {pool.typeNumber()}));
            assertCanonical(bare.adoptParameters(pool, new TypeConstant[] {pool.typeNumber()}),
                    bare.adoptParameters(pool, new TypeConstant[] {pool.typeNumber()}));
            assertCanonical(bare.adoptParameters(pool, new TypeConstant[] {pool.typeString()}),
                    bare.adoptParameters(pool, new TypeConstant[] {pool.typeObject()}));
            assertCanonical(bare.adoptParameters(pool, new TypeConstant[] {pool.typeObject()}),
                    bare.adoptParameters(pool, new TypeConstant[] {pool.typeObject()}));
        }
    }

    @Test
    public void impossibleChildParameterPreventsTheWholeCondition()
            throws IOException {
        ConstantPool pool = testPool();
        ClassStructure parent = parameterizedClass(pool, "Parent", Format.CLASS, "T");
        ClassStructure child = parent.createClass(Access.PUBLIC, Format.CLASS, "Child", null);
        child.addTypeParam("U", pool.typeString());
        ClassStructure mixin = parameterizedClass(pool, "Conditional", Format.MIXIN, "T");
        mixin.addTypeParam("U", pool.typeObject());

        Map<String, TypeConstant> constraints = new LinkedHashMap<>();
        constraints.put("T", pool.typeNumber());
        constraints.put("U", pool.typeNumber());
        child.addIncorporates(mixin.getIdentityConstant().getType().adoptParameters(pool,
                new TypeConstant[] {parent.getFormalType().getParamType(0),
                        child.getFormalType().getParamType(0)}), constraints);

        TypeConstant bare = parent.getIdentityConstant().getType();
        try (Auto ignored = ConstantPool.withPool(pool)) {
            assertCanonical(bare.adoptParameters(pool, new TypeConstant[] {pool.typeInt64()}),
                    bare.adoptParameters(pool, new TypeConstant[] {pool.typeObject()}));
        }
    }

    @Test
    public void childParameterChoiceIsCarriedIntoNestedMixins()
            throws IOException {
        ConstantPool pool = testPool();
        ClassStructure parent = parameterizedClass(pool, "Parent", Format.CLASS, "T");
        ClassStructure child = parent.createClass(Access.PUBLIC, Format.CLASS, "Child", null);
        child.addTypeParam("U", pool.typeObject());
        ClassStructure outer = parameterizedClass(pool, "Outer", Format.MIXIN, "T");
        outer.addTypeParam("U", pool.typeObject());
        ClassStructure inner = parameterizedClass(pool, "Inner", Format.MIXIN, "T");
        inner.addTypeParam("U", pool.typeObject());

        Map<String, TypeConstant> outerCondition = new LinkedHashMap<>();
        outerCondition.put("T", null);
        outerCondition.put("U", pool.typeHashable());
        child.addIncorporates(outer.getIdentityConstant().getType().adoptParameters(pool,
                new TypeConstant[] {parent.getFormalType().getParamType(0),
                        child.getFormalType().getParamType(0)}), outerCondition);

        Map<String, TypeConstant> innerCondition = new LinkedHashMap<>();
        innerCondition.put("T", pool.typeNumber());
        innerCondition.put("U", pool.typeOrderable());
        outer.addIncorporates(inner.getIdentityConstant().getType().adoptParameters(pool,
                outer.getFormalType().getParamTypesArray()), innerCondition);

        TypeConstant bare = parent.getIdentityConstant().getType();
        try (Auto ignored = ConstantPool.withPool(pool)) {
            assertCanonical(bare.adoptParameters(pool, new TypeConstant[] {pool.typeInt64()}),
                    bare.adoptParameters(pool, new TypeConstant[] {pool.typeNumber()}));
            assertCanonical(bare.adoptParameters(pool, new TypeConstant[] {pool.typeString()}),
                    bare.adoptParameters(pool, new TypeConstant[] {pool.typeObject()}));
        }
    }

    @Test
    public void incompatibleNestedChildChoiceDoesNotConstrainParent()
            throws IOException {
        ConstantPool pool = testPool();
        ClassStructure parent = parameterizedClass(pool, "Parent", Format.CLASS, "T");
        ClassStructure child = parent.createClass(Access.PUBLIC, Format.CLASS, "Child", null);
        child.addTypeParam("U", pool.typeObject());
        ClassStructure outer = parameterizedClass(pool, "Outer", Format.MIXIN, "T");
        outer.addTypeParam("U", pool.typeObject());
        ClassStructure inner = parameterizedClass(pool, "Inner", Format.MIXIN, "T");
        inner.addTypeParam("U", pool.typeObject());

        Map<String, TypeConstant> outerCondition = new LinkedHashMap<>();
        outerCondition.put("T", null);
        outerCondition.put("U", pool.typeString());
        child.addIncorporates(outer.getIdentityConstant().getType().adoptParameters(pool,
                new TypeConstant[] {parent.getFormalType().getParamType(0),
                        child.getFormalType().getParamType(0)}), outerCondition);

        Map<String, TypeConstant> innerCondition = new LinkedHashMap<>();
        innerCondition.put("T", pool.typeNumber());
        innerCondition.put("U", pool.typeNumber());
        outer.addIncorporates(inner.getIdentityConstant().getType().adoptParameters(pool,
                outer.getFormalType().getParamTypesArray()), innerCondition);

        TypeConstant bare = parent.getIdentityConstant().getType();
        try (Auto ignored = ConstantPool.withPool(pool)) {
            assertCanonical(bare.adoptParameters(pool, new TypeConstant[] {pool.typeInt64()}),
                    bare.adoptParameters(pool, new TypeConstant[] {pool.typeObject()}));
        }
    }

    @Test
    public void staticChildrenDoNotSpecializeTheirParents()
            throws IOException {
        ConstantPool pool = testPool();
        ClassStructure base = parameterizedClass(pool, "Base", Format.CLASS, "T");
        ClassStructure child = base.createClass(Access.PUBLIC, Format.CLASS, "Child", null);
        child.setStatic(true);
        child.addTypeParam("T", pool.typeObject());
        addCondition(pool, child, child.getFormalType().getParamType(0), pool.typeNumber());
        TypeConstant bare = base.getIdentityConstant().getType();
        try (Auto ignored = ConstantPool.withPool(pool)) {
            assertSame(bare, bare.adoptParameters(pool, new TypeConstant[] {pool.typeInt64()})
                    .getCanonicalType());
        }
    }

    @Test
    public void parameterizedVirtualChildCanonicalizesItsParent()
            throws IOException {
        ConstantPool pool = testPool();
        ClassStructure base = parameterizedClass(pool, "Base", Format.CLASS, "T");
        addCondition(pool, base, base.getFormalType().getParamType(0), pool.typeNumber());
        ClassStructure child = base.createClass(Access.PUBLIC, Format.CLASS, "Child", null);
        child.addTypeParam("U", pool.typeObject());
        addCondition(pool, child, child.getFormalType().getParamType(0), pool.typeOrderable());
        TypeConstant bare = base.getIdentityConstant().getType();
        try (Auto ignored = ConstantPool.withPool(pool)) {
            TypeConstant original = pool.ensureVirtualChildTypeConstant(bare.adoptParameters(pool,
                    new TypeConstant[] {pool.typeInt64()}), "Child").adoptParameters(pool,
                    new TypeConstant[] {pool.typeString()});
            TypeConstant expected = pool.ensureVirtualChildTypeConstant(bare.adoptParameters(pool,
                    new TypeConstant[] {pool.typeNumber()}), "Child").adoptParameters(pool,
                    new TypeConstant[] {pool.typeOrderable()});
            assertCanonical(original, expected);
            assertEquals(expected.getParentType(), original.getCanonicalType().getParentType());
            assertFalse(original.isCanonicalType());
        }
    }

    @Test
    public void childParameterDoesNotCaptureParentParameterOfSameName()
            throws IOException {
        ConstantPool pool = testPool();
        ClassStructure base = parameterizedClass(pool, "Base", Format.CLASS, "T");
        ClassStructure child = base.createClass(Access.PUBLIC, Format.CLASS, "Child", null);
        child.addTypeParam("T", pool.typeObject());
        addCondition(pool, child, child.getFormalType().getParamType(0), pool.typeNumber());
        TypeConstant bare = base.getIdentityConstant().getType();
        try (Auto ignored = ConstantPool.withPool(pool)) {
            assertCanonical(bare.adoptParameters(pool, new TypeConstant[] {pool.typeInt64()}),
                    bare.adoptParameters(pool, new TypeConstant[] {pool.typeObject()}));
        }
    }

    @Test
    public void annotationConditionsAreRetained()
            throws IOException {
        ConstantPool pool = testPool();
        ClassStructure base = parameterizedClass(pool, "Base", Format.CLASS, "T");
        ClassStructure annotation = parameterizedClass(pool, "Annotated", Format.MIXIN, "U");
        annotation.addContribution(Composition.Into, base.getIdentityConstant().getType()
                .adoptParameters(pool, annotation.getFormalType().getParamTypesArray()));
        addCondition(pool, annotation, annotation.getFormalType().getParamType(0), pool.typeNumber());
        TypeConstant bare = base.getIdentityConstant().getType();
        try (Auto ignored = ConstantPool.withPool(pool)) {
            TypeConstant original = pool.ensureAnnotatedTypeConstant(annotation.getIdentityConstant(),
                    null, bare.adoptParameters(pool, new TypeConstant[] {pool.typeInt64()}));
            TypeConstant expected = pool.ensureAnnotatedTypeConstant(annotation.getIdentityConstant(),
                    null, bare.adoptParameters(pool, new TypeConstant[] {pool.typeNumber()}));
            assertCanonical(original, expected);
            assertTrue(original.getCanonicalType().isAnnotated());

            ClassStructure marker = pool.getFileStructure().getModule().createClass(
                    Access.PUBLIC, Format.MIXIN, "Marker", null);
            TypeConstant wrapped = pool.ensureAnnotatedTypeConstant(marker.getIdentityConstant(),
                    null, original.ensureAccess(Access.PRIVATE));
            TypeConstant wrappedExpected = pool.ensureAnnotatedTypeConstant(marker.getIdentityConstant(),
                    null, expected.ensureAccess(Access.PRIVATE));
            assertCanonical(wrapped, wrappedExpected);
            assertEquals(wrappedExpected, wrapped.getCanonicalType());

            base.addAnnotation(annotation.getIdentityConstant());
            assertCanonical(bare.adoptParameters(pool, new TypeConstant[] {pool.typeInt64()}),
                    bare.adoptParameters(pool, new TypeConstant[] {pool.typeNumber()}));
        }
    }

    @Test
    public void classMetadataAnnotationsDoNotSpecializeInstances()
            throws IOException {
        ConstantPool pool = testPool();
        ClassStructure base = parameterizedClass(pool, "Base", Format.CLASS, "T");
        ClassStructure annotation = parameterizedClass(pool, "Metadata", Format.ANNOTATION, "T");
        annotation.addContribution(Composition.Into, pool.ensureParameterizedTypeConstant(
                pool.typeClass(), base.getIdentityConstant().getType().adoptParameters(pool,
                        annotation.getFormalType().getParamTypesArray())));
        addCondition(pool, annotation, annotation.getFormalType().getParamType(0), pool.typeNumber());
        base.addAnnotation(annotation.getIdentityConstant());
        TypeConstant bare = base.getIdentityConstant().getType();
        try (Auto ignored = ConstantPool.withPool(pool)) {
            assertSame(bare, bare.adoptParameters(pool, new TypeConstant[] {pool.typeInt64()})
                    .getCanonicalType());
        }
    }

    private static void assertCanonical(TypeConstant type, TypeConstant expected) {
        TypeConstant actual = type.getCanonicalType();
        assertEquals(expected.getSingleUnderlyingClass(true), actual.getSingleUnderlyingClass(true));
        assertEquals(expected.getParamsCount(), actual.getParamsCount());
        for (int i = 0; i < expected.getParamsCount(); ++i) {
            TypeConstant actualParam = actual.getParamType(i);
            TypeConstant expectedParam = expected.getParamType(i);
            // equivalent intersections can have different operand orderings
            assertTrue(actualParam.isA(expectedParam) && expectedParam.isA(actualParam),
                    () -> "expected " + expected.getValueString() + ", but was " + actual.getValueString());
        }
        assertEquals(actual, actual.getCanonicalType(), "canonicalization is idempotent");
        assertTrue(actual.isCanonicalType());
    }

    private static ClassStructure parameterizedClass(ConstantPool pool, String name, Format format,
            String parameter) {
        ClassStructure clz = pool.getFileStructure().getModule().createClass(
                Access.PUBLIC, format, name, null);
        clz.addTypeParam(parameter, pool.typeObject());
        return clz;
    }

    private static void addCondition(ConstantPool pool, ClassStructure target, TypeConstant argument,
            TypeConstant constraint) {
        ClassStructure mixin = parameterizedClass(pool, target.getName() + "Mixin", Format.MIXIN, "V");
        target.addIncorporates(mixin.getIdentityConstant().getType().adoptParameters(pool,
                new TypeConstant[] {argument}), Map.of("V", constraint));
    }

    private static ConstantPool testPool()
            throws IOException {
        FileStructure file = new FileStructure("test");
        file.merge(loadConstantPool().getFileStructure().getModule(), false, false);
        return file.getConstantPool();
    }

    private static Stream<TypeConstant> nonParameterizedTypes()
            throws IOException {
        ConstantPool pool = loadConstantPool();
        return Stream.of(pool.typeObject(), pool.typeString(), pool.typeTime());
    }

    private static Stream<Arguments> unparameterizedTypesOfParameterizedClasses()
            throws IOException {
        ConstantPool pool       = loadConstantPool();
        TypeConstant typeList   = pool.typeList();
        TypeConstant typeMap    = pool.ensureEcstasyTypeConstant("maps.HasherMap");
        TypeConstant typeArray  = pool.typeArray();
        TypeConstant typeObject = pool.typeObject();
        return Stream.of(
                Arguments.of(typeList, typeList),
                Arguments.of(typeMap, pool.ensureParameterizedTypeConstant(
                        typeMap, typeObject, typeObject)),
                Arguments.of(typeArray, pool.ensureArrayType(typeObject)));
    }

    private static Stream<Arguments> arrayElementTypes()
            throws IOException {
        FileStructure file = new FileStructure("test");
        file.merge(loadConstantPool().getFileStructure().getModule(), false, false);

        ConstantPool pool          = file.getConstantPool();
        TypeConstant typeObject    = pool.typeObject();
        TypeConstant typeHashable  = pool.typeHashable();
        TypeConstant typeOrderable = pool.typeOrderable();
        TypeConstant typeShareable = pool.ensureTypedefConstant(
                pool.clzService(), "Shareable").getReferredToType();

        // String and Time trigger all three non-numeric conditional incorporations
        TypeConstant typeCombined = pool.ensureIntersectionTypeConstant(
                pool.ensureIntersectionTypeConstant(typeHashable, typeOrderable), typeShareable);

        TypeConstant typeHashOrder  = pool.ensureIntersectionTypeConstant(typeHashable, typeOrderable);
        TypeConstant typeHashShare  = pool.ensureIntersectionTypeConstant(typeHashable, typeShareable);
        TypeConstant typeOrderShare = pool.ensureIntersectionTypeConstant(typeOrderable, typeShareable);

        return Stream.of(
                Arguments.of(typeObject, typeObject, true),
                Arguments.of(pool.typeInt64(), pool.typeIntNumber(), false),
                Arguments.of(pool.typeBit(), pool.typeBit(), true),
                Arguments.of(pool.typeByte(), pool.typeByte(), true),
                Arguments.of(pool.typeNibble(), pool.typeNibble(), true),
                Arguments.of(pool.typeNumber(), pool.typeNumber(), true),
                Arguments.of(pool.typeIntNumber(), pool.typeIntNumber(), true),
                Arguments.of(pool.typeFPNumber(), pool.typeFPNumber(), true),
                Arguments.of(pool.typeString(), typeCombined, false),
                Arguments.of(pool.typeTime(), typeCombined, false),
                Arguments.of(pool.ensureEcstasyTypeConstant("collections.SetHasher"),
                        typeHashable, false),
                Arguments.of(pool.ensureServiceTypeConstant(typeObject), typeShareable, false),
                Arguments.of(typeOrderable, typeOrderable, true),
                Arguments.of(createElementType(pool, "HashableOrderable", typeHashable, typeOrderable),
                        typeHashOrder, false),
                Arguments.of(createElementType(pool, "HashableShareable", typeHashable,
                        pool.typeFreezable()), typeHashShare, false),
                Arguments.of(createElementType(pool, "OrderableShareable", typeOrderable,
                        pool.typeFreezable()), typeOrderShare, false));
    }

    private static TypeConstant createElementType(ConstantPool pool, String name,
            TypeConstant... interfaces) {
        ClassStructure clz = pool.getFileStructure().getModule().createClass(
                Access.PUBLIC, Format.CLASS, name, null);
        clz.setAbstract(true);
        for (TypeConstant type : interfaces) {
            clz.addContribution(Composition.Implements, type);
        }
        return clz.getIdentityConstant().getType();
    }

    private static Set<String> conditionalIncorporations(TypeConstant type) {
        ClassStructure clz = (ClassStructure) type.getSingleUnderlyingClass(true).getComponent();
        List<Contribution> contributions = clz.collectConditionalIncorporates(type);
        Set<String> names = new HashSet<>();
        if (contributions != null) {
            for (Contribution contribution : contributions) {
                names.add(contribution.getTypeConstant().getSingleUnderlyingClass(true).getName());
            }
        }
        return names;
    }

    private static ConstantPool loadConstantPool()
            throws IOException {
        try (InputStream in = TypeConstantL1SpecializationTest.class.getResourceAsStream("/ecstasy.xtc")) {
            return new FileStructure(in).getConstantPool();
        }
    }
}
