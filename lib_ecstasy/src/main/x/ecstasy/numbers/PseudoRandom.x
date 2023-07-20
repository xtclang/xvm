/**
 * An implementation of the lightweight xorshift* (xor / shift / multiply) pseudo-random number
 * generator.
 */
service PseudoRandom(UInt64 seed = 0)
        implements Random {
    /**
     * Construct the PseudoRandom generator with the specified (optional) seed.
     *
     * @param seed  the optional seed to prime the generator with, or 0 to generate a seed
     */
    construct(UInt64 seed = 0) {
        if (seed == 0) {
            @Inject Clock clock;
            Time now = clock.now;

            seed = now.date.epochDay.magnitude ^ now.timeOfDay.picos;
            if (seed == 0) {
                seed = 42; // RIP DNA
            }
        }
        this.seed = seed;
    }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The most recent rolling random seed.
     */
    private UInt64 seed;


    // ----- Random interface ----------------------------------------------------------------------

    @Override
    Bit bit() = (uint64() & 1 == 1).toBit();

    @Override
    immutable Bit[] bits(Int size) = uints(size + 0b111111 >> 6).toBitArray()[0..<size].freeze(inPlace=True);

    @Override
    immutable Byte[] bytes(Int size) = uints(size + 0b111 >> 3).toByteArray()[0..<size].freeze(inPlace=True);

    /**
     * Create an array of the specified number of random UInt64 values.
     *
     * @param size  the number of UInt64 values to generate
     *
     * @return an array of `size` UInt64 values
     */
    protected UInt64[] uints(Int size) = new UInt64[size](_ -> uint64());

    @Override
    Int8 int8()     = int64().toInt8(truncate=True);

    @Override
    Int16 int16()   = int64().toInt16(truncate=True);

    @Override
    Int32 int32()   = int64().toInt32(truncate=True);

    @Override
    Int64 int64()   = uint64().toBitArray().toInt64();

    @Override
    UInt8 uint8()   = uint64().toUInt8(truncate=True);

    @Override
    UInt16 uint16() = uint64().toUInt16(truncate=True);

    @Override
    UInt32 uint32() = uint64().toUInt32(truncate=True);

    @Override
    UInt64 uint64() {
        UInt64 seed = seed;
        seed ^= (seed >> 12);
        seed ^= (seed << 25);
        seed ^= (seed >> 27);
        this.seed = seed;
        return seed * 0x2545F4914F6CDD1D;
    }
}