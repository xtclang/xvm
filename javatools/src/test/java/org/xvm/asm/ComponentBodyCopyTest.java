package org.xvm.asm;


import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Component.Composition;
import org.xvm.asm.Component.Contribution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the body-copy constructors that replaced the Object.clone()-based cloneBody() (the
 * structure-family Cloneable retirement). Two defects the clone mechanism itself caused, plus a
 * ratchet against the one hazard hand-written copy constructors introduce:
 *
 * 1. Contribution is a non-static inner class; Object.clone() duplicated its hidden outer
 *    reference, so a cloned component's contributions kept answering getComponent() with the
 *    SOURCE component (red on master).
 * 2. Object.clone() copied every field silently; copy constructors can forget one. The field
 *    ratchet pins each structure's exact instance-field list, so adding a field without
 *    updating the copy constructor fails here with instructions, instead of shipping a
 *    partially copied structure.
 */
public class ComponentBodyCopyTest {
    /**
     * Contributions of a body copy must belong to the copy. Red on master: the shallow
     * Contribution.clone() kept the hidden outer reference, so getComponent() on the copy's
     * contributions answered the original component.
     */
    @Test
    public void contributionsAreReOwnedByBodyCopies() {
        var file = new FileStructure("test");
        var pool = file.getConstantPool();
        var clz  = file.getModule().createClass(
                Constants.Access.PUBLIC, Component.Format.CLASS, "Test", null);
        clz.addContribution(Composition.Implements, pool.typeOrderable());

        var copy = clz.cloneBody();

        List<Contribution> contribsOrig = clz.getContributionsAsList();
        List<Contribution> contribsCopy = copy.getContributionsAsList();
        assertEquals(1, contribsCopy.size());
        assertNotSame(contribsOrig.get(0), contribsCopy.get(0),
                "a body copy must deep-copy the contribution list");
        assertSame(clz, contribsOrig.get(0).getComponent());
        assertSame(copy, contribsCopy.get(0).getComponent(),
                "the copied contribution must answer getComponent() with the COPY;"
                        + " Object.clone() kept the hidden outer reference to the source");
        assertSame(contribsOrig.get(0).getTypeConstant(),
                contribsCopy.get(0).getTypeConstant());
    }

    /**
     * The class-structure type parameters must be deep-copied (the map is mutable), and the
     * copy must carry the body state.
     */
    @Test
    public void classStructureBodyCopyIsIndependent() {
        var file = new FileStructure("test");
        var pool = file.getConstantPool();
        var clz  = file.getModule().createClass(
                Constants.Access.PUBLIC, Component.Format.CLASS, "Test", null);
        clz.addTypeParam("Element", pool.typeObject());

        var copy = clz.cloneBody();

        assertEquals(clz.getTypeParams().keySet(), copy.getTypeParams().keySet());
        assertNotSame(clz.getTypeParams(), copy.getTypeParams(),
                "the mutable type-parameter map must be deep-copied");
        assertSame(clz.getIdentityConstant(), copy.getIdentityConstant());
        assertSame(clz.getParent(), copy.getParent());
        assertTrue(copy.children().isEmpty(), "a body copy carries no children");
    }

    /**
     * The one hazard hand-written copy constructors introduce is a forgotten field. This
     * ratchet pins every structure's exact instance-field list (authoritative via reflection,
     * identical to what Object.clone() used to copy): adding or removing a field fails here
     * until the class's body-copy constructor is updated together with this list.
     */
    @Test
    public void bodyCopyFieldRatchet() {
        assertFields(XvmStructure.class, "m_xsParent");
        assertFields(Component.class,
                "m_sibling", "m_constId", "m_cond", "m_nFlags", "m_listContribs", "m_sDoc",
                "m_abChildren", "m_childByName", "m_fModified", "m_FVisited");
        assertFields(ClassStructure.class,
                "m_mapParams", "m_constPath", "m_typeFormal", "m_typeCanonical", "m_safety",
                "m_aAnnoClass", "m_aAnnoMixin");
        assertFields(ModuleStructure.class,
                "m_constDir", "m_constTimestamp", "m_moduletype", "m_constVersion",
                "m_vtreeImportAllowVers", "m_listImportPreferVers", "m_moduleActual",
                "m_pkgImport", "m_abDigest", "m_mapCondNames", "m_mapDependencies", "m_vtree");
        assertFields(PackageStructure.class);
        assertFields(MethodStructure.class,
                "m_aAnnotations", "m_idFinally", "m_aReturns", "m_cTypeParams",
                "m_cDefaultParams", "m_aParams", "m_idSuper", "m_aconstSuper", "m_abOps",
                "m_abAst", "m_aconstLocal", "m_registry", "m_code", "m_ast", "m_aAstParams",
                "m_cVars", "m_cScopes", "m_fNative", "m_fTransient", "m_FHasCode",
                "m_FUsesSuper", "m_safety", "m_fInitialized", "m_structFinally",
                "m_nNextUnassignedIndex", "m_source");
        assertFields(MultiMethodStructure.class, "m_methodByConstant");
        assertFields(PropertyStructure.class,
                "m_accessVar", "m_type", "m_constVal", "m_fNative", "m_aPropAnno", "m_aRefAnno");
        assertFields(TypedefStructure.class, "m_type");
        assertFields(CompositeComponent.class, "f_siblings");
    }

    /**
     * The Cloneable mechanism must not return to the structure family.
     */
    @Test
    public void structureFamilyIsNotCloneable() {
        for (var clz : List.of(Component.class, MethodStructure.class, Contribution.class)) {
            assertTrue(!Cloneable.class.isAssignableFrom(clz),
                    clz.getSimpleName() + " must not be Cloneable; body copies go through the"
                            + " copy constructors");
        }
    }

    // ----- helpers -------------------------------------------------------------------------------

    private static void assertFields(Class<?> clz, String... expected) {
        Set<String> actual = Arrays.stream(clz.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .filter(field -> !field.isSynthetic())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of(expected), actual,
                clz.getSimpleName() + " instance fields changed; update its body-copy"
                        + " constructor (and cloneBody if the field needs re-owning), then"
                        + " update this ratchet");
    }
}
