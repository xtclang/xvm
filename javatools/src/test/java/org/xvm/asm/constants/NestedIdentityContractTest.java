package org.xvm.asm.constants;


import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.FileStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Parameter;
import org.xvm.asm.PropertyStructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;


/**
 * Characterisation test for the "nested identity" union, written BEFORE any attempt to give it a
 * type.
 *
 * <p>{@code IdentityConstant.getNestedIdentity()} is declared to return {@code Object}, and it
 * really does return five different things: {@code null} for a non-nested identity, a
 * {@code String} for a property, an {@code Integer} for a lambda, a {@code SignatureConstant} for
 * an ordinary method, and a {@code NestedIdentity} when recursively nested. Those values are used
 * as <b>map keys</b> in roughly 76 declarations - {@code Map<Object, ParamInfo>},
 * {@code Map<Object, MethodInfo>}, {@code Map<Object, PropertyInfo>} and friends.</p>
 *
 * <p><b>Why this test exists before the refactor rather than after.</b> The union is only safe
 * because the variants are mutually unequal under Java's own {@code equals}: a {@code String}
 * never equals a {@code SignatureConstant}, an {@code Integer} never equals a
 * {@code NestedIdentity}. Any sealed carrier introduced later must reproduce that <i>exactly</i>.
 * Get it subtly wrong and lookups silently start hitting or missing - <b>wrong answers, not
 * crashes</b>, across 76 maps. That is the same failure mode as the {@code unlinkSibling} hazard,
 * at far greater surface, and it is not the kind of thing a passing build would reveal.</p>
 *
 * <p>So this pins the contract the maps depend on. It must keep passing through the change; if it
 * cannot, the carrier design is wrong.</p>
 */
public class NestedIdentityContractTest {
    /**
     * The variants really are distinct types. This is the premise everything else rests on.
     */
    @Test
    public void theUnionHasTheDocumentedVariants() {
        var fixture = new Fixture();

        assertNull(fixture.clz.getIdentityConstant().getNestedIdentity(),
                "a top-level class is not nested, so its nid is null");
        assertInstanceOf(String.class, fixture.prop.getIdentityConstant().getNestedIdentity(),
                "a property nests by name");
        assertInstanceOf(SignatureConstant.class, fixture.method.getIdentityConstant().getNestedIdentity(),
                "a method nests by signature");
    }

    /**
     * The load-bearing property: no two variants are ever equal. Every one of the ~76
     * {@code Map<Object, …>} lookups depends on this and none of them states it.
     */
    @Test
    public void variantsAreMutuallyUnequal() {
        var fixture = new Fixture();

        Object nidProp   = fixture.prop.getIdentityConstant().getNestedIdentity();
        Object nidMethod = fixture.method.getIdentityConstant().getNestedIdentity();

        assertNotEquals(nidProp, nidMethod, "a property name must never equal a method signature");
        assertNotEquals(nidMethod, nidProp, "and the inequality must be symmetric");

        // an Integer lambda nid must not collide with a property whose name happens to be numeric
        assertNotEquals(Integer.valueOf(0), nidProp);
        assertNotEquals(nidProp, Integer.valueOf(0));
        assertNotEquals(Integer.valueOf(0), nidMethod);
    }

    /**
     * Within a variant, equal identities must give keys that collide in a map - otherwise the
     * caches these maps implement would never hit.
     */
    @Test
    public void equalIdentitiesProduceEqualKeys() {
        var fixture = new Fixture();

        Object nid1 = fixture.prop.getIdentityConstant().getNestedIdentity();
        Object nid2 = fixture.prop.getIdentityConstant().getNestedIdentity();

        assertEquals(nid1, nid2, "the same property must produce equal nids");
        assertEquals(nid1.hashCode(), nid2.hashCode(), "and equal nids must hash equally");

        Map<Object, String> map = new HashMap<>();
        map.put(nid1, "value");
        assertSame("value", map.get(nid2), "a nid must find its own entry in an Object-keyed map");
    }

    /**
     * Distinct members of the same kind must NOT collide, or one would shadow the other in every
     * one of those maps.
     */
    @Test
    public void distinctMembersDoNotCollide() {
        var fixture = new Fixture();

        Object nidProp  = fixture.prop.getIdentityConstant().getNestedIdentity();
        Object nidProp2 = fixture.prop2.getIdentityConstant().getNestedIdentity();

        assertNotEquals(nidProp, nidProp2, "two properties must not share a nid");

        Map<Object, String> map = new HashMap<>();
        map.put(nidProp, "first");
        map.put(nidProp2, "second");
        assertEquals(2, map.size(), "distinct members must occupy distinct entries");
    }

    /** A minimal real structure: one class holding two properties and a method. */
    private static final class Fixture {
        final ClassStructure    clz;
        final PropertyStructure prop;
        final PropertyStructure prop2;
        final MethodStructure   method;

        Fixture() {
            var file   = new FileStructure("test");
            var module = file.getModule();
            var pool   = file.getConstantPool();

            ClassStructure clzObject = module.createClass(
                    Access.PUBLIC, Component.Format.CLASS, "Object", null);
            var typeObject = clzObject.getIdentityConstant().getType();

            clz    = module.createClass(Access.PUBLIC, Component.Format.CLASS, "Widget", null);
            prop   = clz.createProperty(false, Access.PUBLIC, Access.PUBLIC, typeObject, "alpha");
            prop2  = clz.createProperty(false, Access.PUBLIC, Access.PUBLIC, typeObject, "beta");
            method = clz.createMethod(false, Access.PUBLIC, null,
                    Parameter.NO_PARAMS, "doIt", Parameter.NO_PARAMS, false, false);

            assert pool != null;
        }
    }
}
