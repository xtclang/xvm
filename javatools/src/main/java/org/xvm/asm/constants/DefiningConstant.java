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
}
