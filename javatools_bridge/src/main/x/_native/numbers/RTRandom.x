/**
 * Native random number generator available through `@Inject Random random` or `@Inject Random rnd`.
 *
 * Omitting the injection option selects Java's `ThreadLocalRandom`.
 *
 * Using `@Inject(opts=N)` where N is a nonzero `Int` or `IntLiteral` seeds a reproducible
 * pseudo-random sequence.
 *
 * Using `@Inject(opts="secure")` selects Java's cryptographically secure `SecureRandom`.
 */
service RTRandom
        implements Random {
    @Override Bit bit()                         {TODO("native");}
    @Override immutable Bit[] bits(Int size)    {TODO("native");}
    @Override immutable Byte[] bytes(Int size)  {TODO("native");}
    @Override Int int(Int max)                  {TODO("native");}
    @Override Int8 int8()                       {TODO("native");}
    @Override Int16 int16()                     {TODO("native");}
    @Override Int32 int32()                     {TODO("native");}
    @Override Int64 int64()                     {TODO("native");}
    @Override UInt8 uint8()                     {TODO("native");}
    @Override UInt16 uint16()                   {TODO("native");}
    @Override UInt32 uint32()                   {TODO("native");}
    @Override UInt64 uint64()                   {TODO("native");}
    @Override Dec64 dec64()                     {TODO("native");}
    @Override Float32 float32()                 {TODO("native");}
    @Override Float64 float64()                 {TODO("native");}

    @Override
    String toString() {
        return "Random";
    }
}
