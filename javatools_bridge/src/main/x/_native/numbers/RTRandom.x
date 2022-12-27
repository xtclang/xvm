/**
 * Random number generator. Injected with an optional (Int | IntLiteral) seed value to produce
 * a "repetitive" pseudo-random values.
 */
service RTRandom
        implements Random
    {
    @Override Bit bit()                 {TODO("native");}
    @Override Bit[] fill(Bit[] bits)    {TODO("native");}
    @Override Byte[] fill(Byte[] bytes) {TODO("native");}
    @Override Xnt xnt(Xnt max)          {TODO("native");}
    @Override UInt uint(UInt max)       {TODO("native");}
    @Override Int8 int8()               {TODO("native");}
    @Override Int16 int16()             {TODO("native");}
    @Override Int32 int32()             {TODO("native");}
    @Override Int64 int64()             {TODO("native");}
    @Override UInt8 uint8()             {TODO("native");}
    @Override UInt16 uint16()           {TODO("native");}
    @Override UInt32 uint32()           {TODO("native");}
    @Override UInt64 uint64()           {TODO("native");}
    @Override Dec64 dec64()             {TODO("native");}
    @Override Float32 float32()         {TODO("native");}
    @Override Float64 float64()         {TODO("native");}

    @Override
    String toString()
        {
        return "Random";
        }
    }