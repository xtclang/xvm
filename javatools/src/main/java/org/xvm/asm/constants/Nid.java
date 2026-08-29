package org.xvm.asm.constants;


/**
 * A <i>nested identity</i>: the key by which a member is identified relative to the container it
 * nests within.
 *
 * <p>This type exists to replace a bare {@code Object}. {@code IdentityConstant.getNestedIdentity()}
 * was declared to return {@code Object} and really did return five unrelated things - a
 * {@code String} for a property or field name, an {@code Integer} for a lambda index, a
 * {@code SignatureConstant} for a method, an {@code IdentityConstant} for a private property, and a
 * {@code NestedIdentity} when recursively nested. Those values are used as <b>map keys</b> in the
 * {@code Map<Nid, ParamInfo>} / {@code MethodInfo} / {@code PropertyInfo} / {@code FieldInfo}
 * caches that type resolution runs on.</p>
 *
 * <h2>Why the shape is what it is</h2>
 *
 * <p>Only the variants that are <b>not ours</b> are wrapped. {@link SignatureConstant},
 * {@link IdentityConstant} and {@code NestedIdentity} are classes in this package, so they carry
 * the marker directly: no wrapper object, no change to their {@code equals}, and no conversion at
 * any site that already holds one. {@code String} and {@code int} cannot have an interface
 * retrofitted onto them, so those two - and only those two - get a record.</p>
 *
 * <p>Using records for the wrapped variants makes the property the whole union rests on
 * <i>structural</i> rather than incidental: no two record types are ever equal, so
 * {@code ByName("x")} can never collide with {@code ByLambda(0)}. That property was previously
 * supplied by the accident that {@code String} and {@code Integer} happen not to equal each other,
 * and is pinned by {@code NestedIdentityContractTest}.</p>
 *
 * <h2>What typing the key does and does NOT catch</h2>
 *
 * <p>Once a map is declared {@code Map<Nid, V>}, javac rejects every <b>write</b> with a wrong key
 * type. It rejects no <b>reads</b> at all, because {@link java.util.Map#get},
 * {@code containsKey} and {@code remove} take {@code Object} rather than {@code K}. A
 * {@code map.get("Referent")} against a {@code Map<Nid, V>} compiles and silently returns null.
 * That gap is the reason this migration is verified by building the XDK and not by the compiler
 * alone - see {@code docs/reentrancy/plans/static-typing-campaign.md}.</p>
 */
public sealed interface Nid
        permits Nid.ByName, Nid.ByLambda, SignatureConstant, IdentityConstant,
                IdentityConstant.NestedIdentity {
    /**
     * A member identified by name: an implicit field, a type parameter, or a property or field
     * whose container is not itself nested.
     *
     * @param name  the member name
     */
    record ByName(String name)
            implements Nid {
        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * A lambda identified by its index within its parent method.
     *
     * @param index  the lambda's index
     */
    record ByLambda(int index)
            implements Nid {
        @Override
        public String toString() {
            return "^" + index;
        }
    }

    /**
     * @param name  the member name
     *
     * @return the nested identity of a member identified by name
     */
    static Nid of(String name) {
        return new ByName(name);
    }

    /**
     * @param index  the lambda's index
     *
     * @return the nested identity of a lambda
     */
    static Nid of(int index) {
        return new ByLambda(index);
    }
}
