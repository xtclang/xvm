package mappings {
    Mapping[] DEFAULT_MAPPINGS =
        [
        new LiteralMapping<Nullable>(),
        new LiteralMapping<Boolean>(),
        new LiteralMapping<IntLiteral>(),
        new LiteralMapping<FPLiteral>(),
        new LiteralMapping<String>(),

        new BitMapping(),
        new CharMapping(),

        new IntNumberMapping<ecstasy.numbers.Int8    >(),
        new IntNumberMapping<ecstasy.numbers.Int16   >(),
        new IntNumberMapping<ecstasy.numbers.Int32   >(),
        new IntNumberMapping<ecstasy.numbers.Int64   >(),
        new IntNumberMapping<ecstasy.numbers.Int128  >(),
        new IntNumberMapping<ecstasy.numbers.UInt8   >(),
        new IntNumberMapping<ecstasy.numbers.UInt16  >(),
        new IntNumberMapping<ecstasy.numbers.UInt32  >(),
        new IntNumberMapping<ecstasy.numbers.UInt64  >(),
        new IntNumberMapping<ecstasy.numbers.UInt128 >(),
        new IntNumberMapping<ecstasy.numbers.IntN    >(),
        new IntNumberMapping<ecstasy.numbers.UIntN   >(),

        new  FPNumberMapping<ecstasy.numbers.Dec32   >(),
        new  FPNumberMapping<ecstasy.numbers.Dec64   >(),
        new  FPNumberMapping<ecstasy.numbers.Dec128  >(),
        new  FPNumberMapping<ecstasy.numbers.DecN    >(),
        new  FPNumberMapping<ecstasy.numbers.BFloat16>(),
        new  FPNumberMapping<ecstasy.numbers.Float16 >(),
        new  FPNumberMapping<ecstasy.numbers.Float32 >(),
        new  FPNumberMapping<ecstasy.numbers.Float64 >(),
        new  FPNumberMapping<ecstasy.numbers.Float128>(),
        new  FPNumberMapping<ecstasy.numbers.FloatN  >(),

        new DateMapping(),
        new TimeMapping(),
        new TimeMapping(),
        new TimeZoneMapping(),
        new DurationMapping(),

        new PathMapping(),
        new VersionMapping(),

        new JsonPatchMapping(),
        new JsonPatchOperationMapping(),

        // generic container types
        new @Narrowable RangeMapping(new GenericMapping<Orderable>()),
        // TODO other container types...
        ];
}