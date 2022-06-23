/**
 * An implementation of the lightweight xorshift* (xor / shift / multiply) pseudo-random number
 * generator.
 */
service PseudoRandom(UInt seed = 0)
        implements Random
    {
    /**
     * Construct the PseudoRandom generator with the specified (optional) seed.
     *
     * @param seed  the optional seed to prime the generator with, or 0 to generate a seed
     */
    construct(UInt seed = 0)
        {
        if (seed == 0)
            {
            @Inject Clock clock;
            DateTime now = clock.now;

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
    private @Unchecked UInt n;


    // ----- Random interface ----------------------------------------------------------------------

    @Override
    Bit bit()
        {
        return (uint() & 1 == 1).toBit();
        }

    @Override
    Byte byte()
        {
        return uint().toByteArray()[7];
        }

    @Override
    Int int()
        {
        return uint().toByteArray().toInt64();
        }

    @Override
    UInt uint()
        {
        @Unchecked UInt rnd = n;

        rnd ^= (rnd >> 12);
        rnd ^= (rnd << 25);
        rnd ^= (rnd >> 27);

        n = rnd;
        return rnd * 0x2545F4914F6CDD1D;
        }
    }
