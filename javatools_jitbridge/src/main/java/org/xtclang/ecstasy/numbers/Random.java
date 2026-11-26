package org.xtclang.ecstasy.numbers;

import org.xtclang.ecstasy.Object;
import org.xtclang.ecstasy.RangeᐸInt64ᐳ;

import org.xtclang.ecstasy.collections.ArrayᐸBitᐳ;
import org.xtclang.ecstasy.collections.ArrayᐸUInt8ᐳ;

import org.xvm.javajit.Ctx;

import org.xvm.javajit.annotations.JitGenerated;
import org.xvm.javajit.annotations.JitName;

/**
 * Native representation of `ecstasy.numbers.Random`.
 */
@JitGenerated
public interface Random extends Object {

    @JitName
    @JitGenerated
    boolean boolean$p(Ctx ctx);

    @JitName
    @JitGenerated
    int bit$p(Ctx ctx);

    @JitName
    @JitGenerated
    int dec32$p(Ctx ctx);

    @JitName
    @JitGenerated
    long dec64$p(Ctx ctx);

    @JitName
    @JitGenerated
    long dec$p(Ctx ctx);

    @JitName
    @JitGenerated
    long dec128$p(Ctx ctx);

    @JitName
    @JitGenerated
    int int8$p(Ctx ctx);

    @JitName
    @JitGenerated
    int int16$p(Ctx ctx);

    @JitName
    @JitGenerated
    int int32$p(Ctx ctx);

    @JitName
    @JitGenerated
    long int64$p(Ctx ctx);

    @JitName
    @JitGenerated
    long int$p(Ctx ctx);

    @JitName
    @JitGenerated
    long int$p(Ctx ctx, long max);

    @JitName
    @JitGenerated
    long int$p(Ctx ctx, RangeᐸInt64ᐳ range);

    @JitName
    @JitGenerated
    long int128$p(Ctx ctx);

    @JitName
    @JitGenerated
    int uint8$p(Ctx ctx);

    @JitName
    @JitGenerated
    int uint16$p(Ctx ctx);

    @JitName
    @JitGenerated
    int uint32$p(Ctx ctx);

    @JitName
    @JitGenerated
    long uint$p(Ctx ctx);

    @JitName
    @JitGenerated
    long uint64$p(Ctx ctx);

    @JitName
    @JitGenerated
    long uint128$p(Ctx ctx);

    @JitName
    @JitGenerated
    int float8e4$p(Ctx ctx);

    @JitName
    @JitGenerated
    int float8e5$p(Ctx ctx);

    @JitName
    @JitGenerated
    float bfloat16$p(Ctx ctx);

    @JitName
    @JitGenerated
    float float16$p(Ctx ctx);

    @JitName
    @JitGenerated
    float float32$p(Ctx ctx);

    @JitName
    @JitGenerated
    double float64$p(Ctx ctx);

    @JitName
    @JitGenerated
    ArrayᐸBitᐳ bits$p(Ctx ctx, long size);

    @JitName
    @JitGenerated
    ArrayᐸUInt8ᐳ bytes$p(Ctx ctx, long size);
}
