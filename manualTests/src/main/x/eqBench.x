/**
 * A synthetic equality-heavy loop, for measuring the `IsEq`/`IsNotEq` composition cache.
 *
 * Deliberately mixes the three shapes the measurement in
 * `docs/reentrancy/iseq-canonicalization-measurement.md` found in the wild, because a loop over
 * only one of them would flatter whichever design happens to suit it:
 *
 *  - same-type comparisons, which hit the cache monomorphically;
 *  - comparisons through a SUPERTYPE (`Object` holding `Int` or `String`), which is the population
 *    that no pool canonicalization can help and only a cache can;
 *  - comparisons through an ENUM type, whose operands are always distinct enum values.
 */
module eqBench {
    void run(String[] args = []) {
        Int reps = args.size > 0 ? new IntLiteral(args[0]).toInt64() : 200_000;

        @Inject Console console;
        console.print($"eqBench reps={reps}");

        console.print($"sameType   = {sameType(reps)}");
        console.print($"viaObject  = {viaObject(reps)}");
        console.print($"viaEnum    = {viaEnum(reps)}");
        console.print($"viaJump    = {viaJump(reps)}");
    }

    /** Monomorphic: both operands are always String. */
    Int sameType(Int reps) {
        String[] words = ["alpha", "beta", "gamma", "delta"];
        Int      hits  = 0;
        for (Int i : 0 ..< reps) {
            for (String w : words) {
                if (w == "beta") {
                    ++hits;
                }
                if (w != "alpha") {
                    ++hits;
                }
            }
        }
        return hits;
    }

    /** Through a supertype: the comparison type is Object, the operands are Int and String. */
    Int viaObject(Int reps) {
        Int hits = 0;
        for (Int i : 0 ..< reps) {
            Object a = i % 3 == 0 ? "x" : i;
            Object b = i % 2 == 0 ? "x" : i + 1;
            if (a == b) {
                ++hits;
            }
        }
        return hits;
    }

    /**
     * Equality in a JUMP position rather than an assignment, which compiles to `JumpEq`/`JumpNotEq`
     * instead of `IsEq`/`IsNotEq`. Those extend a different base and so were not covered when the
     * cache first landed on `OpTest`; this exercises the path that shares it now.
     */
    Int viaJump(Int reps) {
        String[] words = ["alpha", "beta", "gamma", "delta"];
        Int      hits  = 0;
        for (Int i : 0 ..< reps) {
            for (String w : words) {
                if (w == "gamma") {
                    ++hits;
                } else if (w != "delta") {
                    hits += 2;
                }
            }
        }
        return hits;
    }

    /** Through an enum type: operands are distinct values of the same enum. */
    Int viaEnum(Int reps) {
        Ordered[] all  = [Lesser, Equal, Greater];
        Int       hits = 0;
        for (Int i : 0 ..< reps) {
            Ordered x = all[i % 3];
            Ordered y = all[(i + 1) % 3];
            if (x == y) {
                ++hits;
            }
            if (x != Equal) {
                ++hits;
            }
        }
        return hits;
    }
}
