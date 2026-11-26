package org.xtclang.ecstasy.numbers;

import org.xtclang.ecstasy.Object;
import org.xtclang.ecstasy.RangeᐸInt64ᐳ;

import org.xtclang.ecstasy.collections.ArrayᐸBitᐳ;
import org.xtclang.ecstasy.collections.ArrayᐸUInt8ᐳ;

import org.xvm.javajit.Ctx;

import org.xvm.javajit.annotations.JitFromNatural;
import org.xvm.javajit.annotations.JitGenerated;
import org.xvm.javajit.annotations.JitName;

/**
 * Native representation of `ecstasy.numbers.Random`.
 */
@JitGenerated
public interface Random extends Object {

    @JitName
    @JitFromNatural
    boolean boolean$p(Ctx ctx);

    @JitName
    @JitFromNatural
    int bit$p(Ctx ctx);

    @JitName
    @JitFromNatural
    int dec32$p(Ctx ctx);

    @JitName
    @JitFromNatural
    long dec64$p(Ctx ctx);

    @JitName
    @JitFromNatural
    long dec$p(Ctx ctx);

    @JitName
    @JitFromNatural
    long dec128$p(Ctx ctx);

    @JitName
    @JitFromNatural
    int int8$p(Ctx ctx);

    @JitName
    @JitFromNatural
    int int16$p(Ctx ctx);

    @JitName
    @JitFromNatural
    int int32$p(Ctx ctx);

    @JitName
    @JitFromNatural
    long int64$p(Ctx ctx);

    @JitName
    @JitFromNatural
    long int$p(Ctx ctx);

    @JitName
    @JitFromNatural
    long int$p(Ctx ctx, long max);

    @JitName
    @JitFromNatural
    long int$p(Ctx ctx, RangeᐸInt64ᐳ range);

    @JitName
    @JitFromNatural
    long int128$p(Ctx ctx);

    @JitName
    @JitFromNatural
    int uint8$p(Ctx ctx);

    @JitName
    @JitFromNatural
    int uint16$p(Ctx ctx);

    @JitName
    @JitFromNatural
    int uint32$p(Ctx ctx);

    @JitName
    @JitFromNatural
    long uint$p(Ctx ctx);

    @JitName
    @JitFromNatural
    long uint64$p(Ctx ctx);

    @JitName
    @JitFromNatural
    long uint128$p(Ctx ctx);

    @JitName
    @JitFromNatural
    int float8e4$p(Ctx ctx);

    @JitName
    @JitFromNatural
    int float8e5$p(Ctx ctx);

    @JitName
    @JitFromNatural
    float bfloat16$p(Ctx ctx);

    @JitName
    @JitFromNatural
    float float16$p(Ctx ctx);

    @JitName
    @JitFromNatural
    float float32$p(Ctx ctx);

    @JitName
    @JitFromNatural
    double float64$p(Ctx ctx);

    @JitName
    @JitFromNatural
    ArrayᐸBitᐳ bits$p(Ctx ctx, long size);

    @JitName
    @JitFromNatural
    ArrayᐸUInt8ᐳ bytes$p(Ctx ctx, long size);
}
