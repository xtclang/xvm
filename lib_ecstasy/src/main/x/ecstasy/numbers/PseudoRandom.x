/**
 * An implementation of the lightweight xorshift* (xor / shift / multiply) pseudo-random number
 * generator.
 */
service PseudoRandom(UInt64 seed = 0)
        implements Random
    {
    /**
     * Construct the PseudoRandom generator with the specified (optional) seed.
     *
     * @param seed  the optional seed to prime the generator with, or 0 to generate a seed
     */
    construct(UInt64 seed = 0)
        {
        if (seed == 0)
            {
            @Inject Clock clock;
            Time now = clock.now;

            seed = (now.date.epochDay ^ now.timeOfDay.picos).magnitude;
            if (seed == 0)
                {
                seed = 42; // RIP DNA
                }
            }

        n = seed.toUnchecked();
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The previous random value.
     */
    private @Unchecked UInt64 n;


    // ----- Random interface ----------------------------------------------------------------------

    @Override
    Bit bit()
        {
        return (uint64() & 1 == 1).toBit();
        }

    @Override
    Bit[] fill(Bit[] bits)
        {
        assert bits.mutability >= Fixed;

        Int offset = 0;
        Int remain = bits.size;
        while (remain >= 64)
            {
            bits.replaceAll(offset, uint64().toBitArray());
            offset += 64;
            remain -= 64;
            }
        if (remain > 0)
            {
            bits.replaceAll(offset, uint64().toBitArray()[0..<remain]);
            }
        return bits;
        }

    @Override
    Byte[] fill(Byte[] bytes)
        {
        assert bytes.mutability >= Fixed;

        Int offset = 0;
        Int remain = bytes.size;
        while (remain >= 8)
            {
            bytes.replaceAll(offset, uint64().toByteArray());
            offset += 8;
            remain -= 8;
            }
        if (remain > 0)
            {
            bytes.replaceAll(offset, uint64().toByteArray()[0..<remain]);
            }
        return bytes;
        }

    @Override
    Int8 int8()
        {
        return uint64().toInt8(truncate=True);
        }

    @Override
    Int16 int16()
        {
        return uint64().toInt16(truncate=True);
        }

    @Override
    Int32 int32()
        {
        return uint64().toInt32(truncate=True);
        }

    @Override
    Int64 int64()
        {
        return uint64().toInt64(truncate=True);
        }

    @Override
    UInt8 uint8()
        {
        return uint64().toUInt8(truncate=True);
        }

    @Override
    UInt16 uint16()
        {
        return uint64().toUInt16(truncate=True);
        }

    @Override
    UInt32 uint32()
        {
        return uint64().toUInt32(truncate=True);
        }

    @Override
    UInt64 uint64()
        {
        @Unchecked UInt64 rnd = n;

        rnd ^= (rnd >> 12);
        rnd ^= (rnd << 25);
        rnd ^= (rnd >> 27);

        n = rnd;
        return rnd * 0x2545F4914F6CDD1D;
        }
    }
