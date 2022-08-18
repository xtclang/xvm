/**
 * Random number generator. Injected with an optional (Int | IntLiteral) seed value to produce
 * a "repetitive" pseudo-random values.
 */
service RTRandom
        implements Random
    {
    @Override Bit bit()               {TODO("native");}
    @Override void fill(Bit[] bits)   {TODO("native");}
    @Override Byte byte()             {TODO("native");}
    @Override void fill(Byte[] bytes) {TODO("native");}
    @Override Int int()               {TODO("native");}
    @Override Int int(Int max)        {TODO("native");}
    @Override UInt uint()             {TODO("native");}
    @Override Dec dec()               {TODO("native");}
    @Override Float64 float()         {TODO("native");}

    @Override
    String toString()
        {
        return "Random";
        }
    }