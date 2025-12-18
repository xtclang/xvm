package org.xvm.asm;


import java.util.regex.Pattern;

import org.xvm.type.Decimal;

import org.xvm.util.PackedInteger;


/**
 * A Locator is a unique identifier for a Constant within a ConstantPool, used for fast lookups.
 * Each Constant type that supports locators returns a specific Locator subtype from its
 * {@link Constant#getLocator()} method.
 * <p>
 * The sealed hierarchy ensures type safety for the map keys in {@code m_mapLocators}.
 */
public sealed interface Locator
        permits Locator.OfString,
                Locator.OfPackedInteger,
                Locator.OfCharacter,
                Locator.OfInteger,
                Locator.OfFloat,
                Locator.OfDouble,
                Locator.OfBytes,
                Locator.OfDecimal,
                Locator.OfFormat,
                Locator.OfPattern,
                Locator.OfConstant {

    // ----- wrapper records for each locator type -------------------------------------------------

    /**
     * Locator for String values (used by StringConstant, LiteralConstant, NamedCondition).
     */
    record OfString(String value) implements Locator {}

    /**
     * Locator for PackedInteger values (used by IntConstant).
     */
    record OfPackedInteger(PackedInteger value) implements Locator {}

    /**
     * Locator for Character values (used by CharConstant).
     */
    record OfCharacter(Character value) implements Locator {}

    /**
     * Locator for Integer values (used by ByteConstant).
     */
    record OfInteger(Integer value) implements Locator {}

    /**
     * Locator for Float values (used by Float32Constant, Float8e4Constant, etc.).
     */
    record OfFloat(Float value) implements Locator {}

    /**
     * Locator for Double values (used by Float64Constant).
     */
    record OfDouble(Double value) implements Locator {}

    /**
     * Locator for byte array values (used by Float128Constant).
     */
    record OfBytes(byte[] value) implements Locator {
        @Override
        public boolean equals(Object obj) {
            return obj instanceof OfBytes that && java.util.Arrays.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return java.util.Arrays.hashCode(value);
        }
    }

    /**
     * Locator for Decimal values (used by DecimalConstant).
     */
    record OfDecimal(Decimal value) implements Locator {}

    /**
     * Locator for Format enum values (used by KeywordConstant).
     */
    record OfFormat(Constant.Format value) implements Locator {}

    /**
     * Locator for Pattern values (used by RegExConstant).
     */
    record OfPattern(Pattern value) implements Locator {}

    /**
     * Locator for Constant values (used when the locator is itself another Constant,
     * e.g., SingletonConstant uses ClassConstant, TypeConstants use TypeConstant, etc.).
     */
    record OfConstant(Constant value) implements Locator {}
}