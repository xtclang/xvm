/**
 * A [HashCollector] is a service that collects hashing information as a sequence of additions,
 * culminating in a hash value when nothing is left to add.
 *
 * The methods that *must* be implemented in a subclass are: [add(Byte)], [add(Byte[])],
 * [compute()], and [reset()].
 *
 * It is expected that most implementations will support seeding the `HashCollector` in order to
 * better protect against "hash attacks".
 *
 * A seeded [HashCollector] service named "`hash`" is expected to be available via injection; for
 * example:
 *
 *     class Point(Dec x, Dec y) {
 *         // ...
 *         @Override
 *         static <CompileType extends Point> Int hashCode(CompileType value) {
 *             @Inject HashCollector hash;
 *             return hash.add(value.x).add(value.y).compute();
 *         }
 *     }
 */
@Abstract service HashCollector {
    /**
     * Add a [Boolean] value to the hash.
     *
     * @param value  the [Boolean] value
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Boolean value) = add(value.toBit());

    /**
     * Add a [Boolean] value to the hash.
     *
     * @param array  the array of [Boolean] values
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Boolean[] array) = add(array.hashCode());

    /**
     * Add a [Bit] value to the hash.
     *
     * @param bit  the [Bit] value
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Bit bit) = add(bit.toByte());

    /**
     * Add an array of [Bit] value to the hash.
     *
     * @param array  the array of [Bit] value
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Bit[] array) = add(array.hashCode());

    /**
     * Add an [Int8] value to the hash.
     *
     * @param value  the [Int8] value
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Int8 value) = add(value.toByte());

    /**
     * Add an array of [Int8] values to the hash.
     *
     * @param array  the array of [Int8] values
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Int8[] array) = add(array.hashCode());

    /**
     * Add a [UInt8] value to the hash.
     *
     * @param value  the [UInt8] value
     *
     * @return this [HashCollector]
     */
    @Abstract @Op("+") HashCollector add(UInt8 value);

    /**
     * Add an array of [UInt8] values to the hash.
     *
     * @param array  the array of [UInt8] values
     *
     * @return this [HashCollector]
     */
    @Abstract @Op("+") HashCollector add(UInt8[] array);

    /**
     * Add an [Int16] value to the hash.
     *
     * @param value  the [Int16] value
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Int16 value) = add(value.toByteArray());

    /**
     * Add an array of [Int16] values to the hash.
     *
     * @param array  the array of [Int16] values
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Int16[] array) = add(array.hashCode());

    /**
     * Add a [UInt16] value to the hash.
     *
     * @param value  the [UInt16] value
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(UInt16 value) = add(value.toByteArray());

    /**
     * Add an array of [UInt16] values to the hash.
     *
     * @param array  the array of [UInt16] values
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(UInt16[] array) = add(array.hashCode());

    /**
     * Add an [Int32] value to the hash.
     *
     * @param value  the [Int32] value
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Int32 value) = add(value.toByteArray());

    /**
     * Add an array of [Int32] values to the hash.
     *
     * @param array  the array of [Int32] values
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Int32[] array) = add(array.hashCode());

    /**
     * Add a [UInt32] value to the hash.
     *
     * @param value  the [UInt32] value
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(UInt32 value) = add(value.toByteArray());

    /**
     * Add an array of [UInt32] values to the hash.
     *
     * @param array  the array of [UInt32] values
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(UInt32[] array) = add(array.hashCode());

    /**
     * Add an [Int64] value to the hash.
     *
     * @param value  the [Int64] value
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Int64 value) = add(value.toByteArray());

    /**
     * Add an array of [Int64] values to the hash.
     *
     * @param array  the array of [Int64] values
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Int64[] array) = add(array.hashCode());

    /**
     * Add a [UInt64] value to the hash.
     *
     * @param value  the [UInt64] value
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(UInt64 value) = add(value.toByteArray());

    /**
     * Add an array of [UInt64] values to the hash.
     *
     * @param array  the array of [UInt64] values
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(UInt64[] array) = add(array.hashCode());

    /**
     * Add an [Int128] value to the hash.
     *
     * @param value  the [Int128] value
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Int128 value) = add(value.toByteArray());

    /**
     * Add an array of [Int128] values to the hash.
     *
     * @param array  the array of [Int128] values
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Int128[] array) = add(array.hashCode());

    /**
     * Add a [UInt128] value to the hash.
     *
     * @param value  the [UInt128] value
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(UInt128 value) = add(value.toByteArray());

    /**
     * Add an array of [UInt128] values to the hash.
     *
     * @param array  the array of [UInt128] values
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(UInt128[] array) = add(array.hashCode());

    /**
     * Add an [IntN] value to the hash.
     *
     * @param value  the [IntN] value
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(IntN value) = add(value.toByteArray());

    /**
     * Add an array of [IntN] values to the hash.
     *
     * @param array  the array of [IntN] values
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(IntN[] array) = add(array.hashCode());

    /**
     * Add a [UIntN] value to the hash.
     *
     * @param value  the [UIntN] value
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(UIntN value) = add(value.toByteArray());

    /**
     * Add an array of [UIntN] values to the hash.
     *
     * @param array  the array of [UIntN] values
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(UIntN[] array) = add(array.hashCode());

    /**
     * Add a [Float8e4] value to the hash.
     *
     * @param value  the [Float8e4] value
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Float8e4 value) = add(value.toByteArray());

    /**
     * Add an array of [Float8e4] values to the hash.
     *
     * @param array  the array of [Float8e4] values
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Float8e4[] array) = add(array.hashCode());

    /**
     * Add a [Float8e5] value to the hash.
     *
     * @param value  the [Float8e5] value
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Float8e5 value) = add(value.toByteArray());

    /**
     * Add an array of [Float8e5] values to the hash.
     *
     * @param array  the array of [Float8e5] values
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Float8e5[] array) = add(array.hashCode());

    /**
     * Add a [BFloat16] value to the hash.
     *
     * @param value  the [BFloat16] value
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(BFloat16 value) = add(value.toByteArray());

    /**
     * Add an array of [BFloat16] values to the hash.
     *
     * @param array  the array of [BFloat16] values
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(BFloat16[] array) = add(array.hashCode());

    /**
     * Add a [Float16] value to the hash.
     *
     * @param value  the [Float16] value
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Float16 value) = add(value.toByteArray());

    /**
     * Add an array of [Float16] values to the hash.
     *
     * @param array  the array of [Float16] values
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Float16[] array) = add(array.hashCode());

    /**
     * Add a [Float32] value to the hash.
     *
     * @param value  the [Float32] value
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Float32 value) = add(value.toByteArray());

    /**
     * Add an array of [Float32] values to the hash.
     *
     * @param array  the array of [Float32] values
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Float32[] array) = add(array.hashCode());

    /**
     * Add a [Float64] value to the hash.
     *
     * @param value  the [Float64] value
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Float64 value) = add(value.toByteArray());

    /**
     * Add an array of [Float64] values to the hash.
     *
     * @param array  the array of [Float64] values
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Float64[] array) = add(array.hashCode());

    /**
     * Add a [Float128] value to the hash.
     *
     * @param value  the [Float128] value
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Float128 value) = add(value.toByteArray());

    /**
     * Add an array of [Float128] values to the hash.
     *
     * @param array  the array of [Float128] values
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Float128[] array) = add(array.hashCode());

    /**
     * Add a [FloatN] value to the hash.
     *
     * @param value  the [FloatN] value
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(FloatN value) = add(value.toByteArray());

    /**
     * Add an array of [FloatN] values to the hash.
     *
     * @param array  the array of [FloatN] values
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(FloatN[] array) = add(array.hashCode());

    /**
     * Add a [Dec32] value to the hash.
     *
     * @param value  the [Dec32] value
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Dec32 value) = add(value.toByteArray());

    /**
     * Add an array of [Dec32] values to the hash.
     *
     * @param array  the array of [Dec32] values
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Dec32[] array) = add(array.hashCode());

    /**
     * Add a [Dec64] value to the hash.
     *
     * @param value  the [Dec64] value
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Dec64 value) = add(value.toByteArray());

    /**
     * Add an array of [Dec64] values to the hash.
     *
     * @param array  the array of [Dec64] values
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Dec64[] array) = add(array.hashCode());

    /**
     * Add a [Dec128] value to the hash.
     *
     * @param value  the [Dec128] value
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Dec128 value) = add(value.toByteArray());

    /**
     * Add an array of [Dec128] values to the hash.
     *
     * @param array  the array of [Dec128] values
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Dec128[] array) = add(array.hashCode());

    /**
     * Add a [DecN] value to the hash.
     *
     * @param value  the [DecN] value
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(DecN value) = add(value.toByteArray());

    /**
     * Add an array of [DecN] values to the hash.
     *
     * @param array  the array of [DecN] values
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(DecN[] array) = add(array.hashCode());

    /**
     * Add a [Char] value to the hash.
     *
     * @param value  the [Char] value
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Char value) = add(value.codepoint);

    /**
     * Add an array of [Char] values to the hash.
     *
     * @param array  the array of [Char] values
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(Char[] array) = add(array.hashCode());

    /**
     * Add a [String] value to the hash.
     *
     * @param value  the [String] value
     *
     * @return this [HashCollector]
     */
    @Op("+") HashCollector add(String value) = add(value.hashCode());

    /**
     * Calculate the result from collecting all of the hashing information. After the result is
     * produced, the HashCollector should not used without first being [reset].
     *
     * @return the hash code result
     */
    @Abstract Int compute();

    /**
     * Return the HashCollector to its initial state. After computing a hash result using the
     * [compute()] method, this method must be invoked before re-using the [HashCollector] instance.
     *
     * @return this [HashCollector]
     */
    @Abstract HashCollector reset();
}