package org.xvm.asm.constants;


import org.xvm.asm.Constant;


/**
 * The sealed union of constants that can define a terminal type: identity constants (classes,
 * properties, formal types, and so on) and pseudo constants (this/parent/child class,
 * keywords, unresolved names). {@code TerminalTypeConstant.getDefiningConstant()} always
 * produces one of these two trees; before this union existed, every consumer re-derived that
 * fact with a 10-20 case {@code Format} switch full of per-arm casts and a default arm that
 * silently answered for every format the author forgot. A pattern switch over this union is
 * compiler-checked exhaustive instead: adding a defining-constant kind refuses to compile at
 * every switch, and the casts disappear into pattern bindings.
 */
public sealed interface DefiningConstant
        permits IdentityConstant, PseudoConstant {
    /**
     * @return the format of this constant (both branches of the union are constants)
     */
    Constant.Format getFormat();

    /**
     * @return the value of this constant, in some sort of human-readable form
     */
    String getValueString();

    /**
     * @return the type that this constant defines
     */
    TypeConstant getType();

    /**
     * Narrow a constant that is known to define a type.
     *
     * <p>Two producers of a defining constant - {@code TerminalTypeConstant} and
     * {@code UnresolvedTypeConstant} - read their constant out of a field declared {@code Constant},
     * so neither can prove the narrowing statically. The invariant that makes it safe is
     * {@code TerminalTypeConstant}'s own constructor guard, {@code Format.isTypeable()}: every
     * format it admits - Module, Package, Class, Typedef, Property, the formal types, the pseudo
     * classes, the keywords and UnresolvedName - is implemented by a class in one of this union's
     * two branches. {@code TypeableFormatsAreDefiningConstantsTest} pins that.</p>
     *
     * <p>Doing the check here rather than at each consumer is the point: it converts the unchecked
     * casts those consumers used to write into a single checked conversion that names what went
     * wrong if the invariant is ever broken.</p>
     *
     * @param constant  a constant that must define a type
     *
     * @return the same constant, typed
     */
    /**
     * @return this defining constant as a {@link Constant}
     */
    default Constant asConstant() {
        // exhaustive over the union's two branches, both of which extend Constant, so this
        // conversion needs no cast and no default: adding a branch that is not a Constant would
        // fail to compile here rather than at some consumer
        return switch (this) {
            case IdentityConstant constId -> constId;
            case PseudoConstant   constId -> constId;
        };
    }

    static DefiningConstant of(Constant constant) {
        if (constant instanceof DefiningConstant defining) {
            return defining;
        }
        throw new IllegalStateException("not a defining constant: "
                + (constant == null ? "null" : constant.getFormat() + " " + constant.getClass()));
    }
}
