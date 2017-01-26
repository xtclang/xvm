/**
 * An IntNumber is a Number that represents an integer value.
 */
interface IntNumber
        extends Number
    {
    /**
     * Integer increment.
     */
    @op IntNumber inc();
    /**
     * Integer decrement.
     */
    @op IntNumber dec();

    /**
     * Shift bits left. This is both a logical left shift and arithmetic left shift, for
     * both signed and unsigned integer values.
     * <p/>
     * Note: For an integer of size n bits, only the least significant log2(n) bits of the
     *       count value are used.
     */
    @op IntNumber shl(Int count);
    /**
     * Shift bits right. For signed integer values, this is an arithmetic right shift. For
     * unsigned integer values, this is both a logical right shift and arithmetic right
     * shift.
     * <p/>
     * Note: For an integer of size n bits, only the least significant log2(n) bits of the
     *       count value are used.
     */
    @op IntNumber shr(Int count);
    /**
     * "Unsigned" Shift bits right. For signed integer values, this is an logical right
     * shift. For unsigned integer values, this is both a logical right shift and arithmetic
     * right shift.
     * <p/>
     * Note: For an integer of size n bits, only the least significant log2(n) bits of the
     *       count value are used.
     */
    @op IntNumber ushr(Int count);
    
    /**
     * Rotate bits left.
     * <p/>
     * Note: For an integer of size n bits, only the least significant log2(n) bits of the
     *       count value are used.
     */ 
    IntNumber rol(Int count);
    /**
     * Rotate bits right.
     * <p/>
     * Note: For an integer of size n bits, only the least significant log2(n) bits of the
     *       count value are used.
     */ 
    IntNumber ror(Int count);
    }
