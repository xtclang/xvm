import ecstasy.collections.HashCollector;

/**
 * Tests for HashCollector injection.
 */
package hashCollectorTests {

    void run() {
        shouldReset();

        shouldAddBoolean();
        shouldAddBooleanArray();

        shouldAddBit();
        shouldAddBitArray();

        shouldAddInt8();
        shouldAddInt8Array();
        shouldAddUInt8();
        shouldAddUInt8Array();
        shouldAddInt16();
        shouldAddInt16Array();
        shouldAddUInt16();
        shouldAddUInt16Array();
        shouldAddInt32();
        shouldAddInt32Array();
        shouldAddUInt32();
        shouldAddUInt32Array();
        shouldAddInt64();
        shouldAddInt64Array();
        shouldAddUInt64();
        shouldAddUInt64Array();
        shouldAddInt128();
        shouldAddInt128Array();
        shouldAddUInt128();
        shouldAddUInt128Array();
        shouldAddIntN();
        shouldAddIntNArray();
        shouldAddUIntN();
        shouldAddUIntNArray();
    }

    void shouldReset() {
        @Inject HashCollector hash;
        // initial hash from seed
        Int value1 = hash.compute();
        // add something
        hash.add("Foo");
        // hash should now be different
        Int value2 = hash.compute();
        assert value2 != value1;
        // reset back to initial seed
        hash.reset();
        assert hash.compute() == value1;
    }

    void shouldAddBoolean() {
        @Inject("hash") HashCollector hash1;
        @Inject("hash") HashCollector hash2;
        Int value1 = hash1.add(True).compute();
        Int value2 = hash2.add(False).compute();
        assert value1 != value2;
        assert hash2.reset().add(True).compute() == value1;
        assert hash1.reset().add(False).compute() == value2;
    }

    void shouldAddBooleanArray() {
// TODO array hashCode() throws unsupported
//        @Inject("hash") HashCollector hash1;
//        @Inject("hash") HashCollector hash2;
//        Int value1 = hash1.add([True, False]).compute();
//        Int value2 = hash2.add([False, True]).compute();
//        assert value1 != value2;
//        assert hash2.reset().add([True, False]).compute() == value1;
//        assert hash1.reset().add([False, True]).compute() == value2;
    }

    void shouldAddBit() {
        @Inject("hash") HashCollector hash1;
        @Inject("hash") HashCollector hash2;
        Bit zero = 0;
        Bit one  = 1;
        Int value1 = hash1.add(zero).compute();
        Int value2 = hash2.add(one).compute();
        assert value1 != value2;
        assert hash2.reset().add(zero).compute() == value1;
        assert hash1.reset().add(one).compute() == value2;
    }

    void shouldAddBitArray() {
// TODO array hashCode() throws unsupported
//        @Inject("hash") HashCollector hash1;
//        @Inject("hash") HashCollector hash2;
//        Bit zero   = 0;
//        Bit one    = 1;
//        Int value1 = hash1.add([zero, one]).compute();
//        Int value2 = hash2.add([one, zero]).compute();
//        assert value1 != value2;
//        assert hash2.reset().add([zero, one]).compute() == value1;
//        assert hash1.reset().add([one, zero]).compute() == value2;
    }

    void shouldAddInt8() {
        @Inject("hash") HashCollector hash1;
        @Inject("hash") HashCollector hash2;
        Int8 n1    = 3;
        Int8 n2    = 5;
        Int value1 = hash1.add(n1).compute();
        Int value2 = hash2.add(n2).compute();
        assert value1 != value2;
        assert hash2.reset().add(n1).compute() == value1;
        assert hash1.reset().add(n2).compute() == value2;
    }

    void shouldAddInt8Array() {
// TODO array hashCode() throws unsupported
//        @Inject("hash") HashCollector hash1;
//        @Inject("hash") HashCollector hash2;
//        Int8 n1    = 3;
//        Int8 n2    = 5;
//        Int value1 = hash1.add([n1, n2]).compute();
//        Int value2 = hash2.add([n2, n1]).compute();
//        assert value1 != value2;
//        assert hash2.reset().add([n1, n2]).compute() == value1;
//        assert hash1.reset().add([n2, n1]).compute() == value2;
    }

    void shouldAddUInt8() {
        @Inject("hash") HashCollector hash1;
        @Inject("hash") HashCollector hash2;
        UInt8 n1   = 3;
        UInt8 n2   = 5;
        Int value1 = hash1.add(n1).compute();
        Int value2 = hash2.add(n2).compute();
        assert value1 != value2;
        assert hash2.reset().add(n1).compute() == value1;
        assert hash1.reset().add(n2).compute() == value2;
    }

    void shouldAddUInt8Array() {
        @Inject("hash") HashCollector hash1;
        @Inject("hash") HashCollector hash2;
        UInt8 n1   = 3;
        UInt8 n2   = 5;
        Int value1 = hash1.add([n1, n2]).compute();
        Int value2 = hash2.add([n2, n1]).compute();
        assert value1 != value2;
        assert hash2.reset().add([n1, n2]).compute() == value1;
        assert hash1.reset().add([n2, n1]).compute() == value2;
    }

    void shouldAddInt16() {
        @Inject("hash") HashCollector hash1;
        @Inject("hash") HashCollector hash2;
        Int16 n1   = 234;
        Int16 n2   = 567;
        Int value1 = hash1.add(n1).compute();
        Int value2 = hash2.add(n2).compute();
        assert value1 != value2;
        assert hash2.reset().add(n1).compute() == value1;
        assert hash1.reset().add(n2).compute() == value2;
    }

    void shouldAddInt16Array() {
// TODO array hashCode() throws unsupported
//        @Inject("hash") HashCollector hash1;
//        @Inject("hash") HashCollector hash2;
//        Int16 n1   = 234;
//        Int16 n2   = 567;
//        Int value1 = hash1.add([n1, n2]).compute();
//        Int value2 = hash2.add([n2, n1]).compute();
//        assert value1 != value2;
//        assert hash2.reset().add([n1, n2]).compute() == value1;
//        assert hash1.reset().add([n2, n1]).compute() == value2;
    }

    void shouldAddUInt16() {
        @Inject("hash") HashCollector hash1;
        @Inject("hash") HashCollector hash2;
        UInt16 n1  = 234;
        UInt16 n2  = 567;
        Int value1 = hash1.add(n1).compute();
        Int value2 = hash2.add(n2).compute();
        assert value1 != value2;
        assert hash2.reset().add(n1).compute() == value1;
        assert hash1.reset().add(n2).compute() == value2;
    }

    void shouldAddUInt16Array() {
// TODO array hashCode() throws unsupported
//        @Inject("hash") HashCollector hash1;
//        @Inject("hash") HashCollector hash2;
//        UInt16 n1  = 234;
//        UInt16 n2  = 567;
//        Int value1 = hash1.add([n1, n2]).compute();
//        Int value2 = hash2.add([n2, n1]).compute();
//        assert value1 != value2;
//        assert hash2.reset().add([n1, n2]).compute() == value1;
//        assert hash1.reset().add([n2, n1]).compute() == value2;
    }

    void shouldAddInt32() {
        @Inject("hash") HashCollector hash1;
        @Inject("hash") HashCollector hash2;
        Int32 n1   = 234;
        Int32 n2   = 567;
        Int value1 = hash1.add(n1).compute();
        Int value2 = hash2.add(n2).compute();
        assert value1 != value2;
        assert hash2.reset().add(n1).compute() == value1;
        assert hash1.reset().add(n2).compute() == value2;
    }

    void shouldAddInt32Array() {
// TODO array hashCode() throws unsupported
//        @Inject("hash") HashCollector hash1;
//        @Inject("hash") HashCollector hash2;
//        Int32 n1   = 234;
//        Int32 n2   = 567;
//        Int value1 = hash1.add([n1, n2]).compute();
//        Int value2 = hash2.add([n2, n1]).compute();
//        assert value1 != value2;
//        assert hash2.reset().add([n1, n2]).compute() == value1;
//        assert hash1.reset().add([n2, n1]).compute() == value2;
    }

    void shouldAddUInt32() {
        @Inject("hash") HashCollector hash1;
        @Inject("hash") HashCollector hash2;
        UInt32 n1  = 234;
        UInt32 n2  = 567;
        Int value1 = hash1.add(n1).compute();
        Int value2 = hash2.add(n2).compute();
        assert value1 != value2;
        assert hash2.reset().add(n1).compute() == value1;
        assert hash1.reset().add(n2).compute() == value2;
    }

    void shouldAddUInt32Array() {
// TODO array hashCode() throws unsupported
//        @Inject("hash") HashCollector hash1;
//        @Inject("hash") HashCollector hash2;
//        UInt32 n1  = 234;
//        UInt32 n2  = 567;
//        Int value1 = hash1.add([n1, n2]).compute();
//        Int value2 = hash2.add([n2, n1]).compute();
//        assert value1 != value2;
//        assert hash2.reset().add([n1, n2]).compute() == value1;
//        assert hash1.reset().add([n2, n1]).compute() == value2;
    }

    void shouldAddInt64() {
        @Inject("hash") HashCollector hash1;
        @Inject("hash") HashCollector hash2;
        Int64 n1   = 234;
        Int64 n2   = 567;
        Int value1 = hash1.add(n1).compute();
        Int value2 = hash2.add(n2).compute();
        assert value1 != value2;
        assert hash2.reset().add(n1).compute() == value1;
        assert hash1.reset().add(n2).compute() == value2;
    }

    void shouldAddInt64Array() {
// TODO array hashCode() throws unsupported
//        @Inject("hash") HashCollector hash1;
//        @Inject("hash") HashCollector hash2;
//        Int64 n1   = 234;
//        Int64 n2   = 567;
//        Int value1 = hash1.add([n1, n2]).compute();
//        Int value2 = hash2.add([n2, n1]).compute();
//        assert value1 != value2;
//        assert hash2.reset().add([n1, n2]).compute() == value1;
//        assert hash1.reset().add([n2, n1]).compute() == value2;
    }

    void shouldAddUInt64() {
        @Inject("hash") HashCollector hash1;
        @Inject("hash") HashCollector hash2;
        UInt64 n1  = 234;
        UInt64 n2  = 567;
        Int value1 = hash1.add(n1).compute();
        Int value2 = hash2.add(n2).compute();
        assert value1 != value2;
        assert hash2.reset().add(n1).compute() == value1;
        assert hash1.reset().add(n2).compute() == value2;
    }

    void shouldAddUInt64Array() {
// TODO array hashCode() throws unsupported
//        @Inject("hash") HashCollector hash1;
//        @Inject("hash") HashCollector hash2;
//        UInt64 n1  = 234;
//        UInt64 n2  = 567;
//        Int value1 = hash1.add([n1, n2]).compute();
//        Int value2 = hash2.add([n2, n1]).compute();
//        assert value1 != value2;
//        assert hash2.reset().add([n1, n2]).compute() == value1;
//        assert hash1.reset().add([n2, n1]).compute() == value2;
    }

    void shouldAddInt128() {
        @Inject("hash") HashCollector hash1;
        @Inject("hash") HashCollector hash2;
        Int128 n1  = 234;
        Int128 n2  = 567;
        Int value1 = hash1.add(n1).compute();
        Int value2 = hash2.add(n2).compute();
        assert value1 != value2;
        assert hash2.reset().add(n1).compute() == value1;
        assert hash1.reset().add(n2).compute() == value2;
    }

    void shouldAddInt128Array() {
// TODO array hashCode() throws unsupported
//        @Inject("hash") HashCollector hash1;
//        @Inject("hash") HashCollector hash2;
//        Int128 n1  = 234;
//        Int128 n2  = 567;
//        Int value1 = hash1.add([n1, n2]).compute();
//        Int value2 = hash2.add([n2, n1]).compute();
//        assert value1 != value2;
//        assert hash2.reset().add([n1, n2]).compute() == value1;
//        assert hash1.reset().add([n2, n1]).compute() == value2;
    }

    void shouldAddUInt128() {
        @Inject("hash") HashCollector hash1;
        @Inject("hash") HashCollector hash2;
        UInt128 n1 = 234;
        UInt128 n2 = 567;
        Int value1 = hash1.add(n1).compute();
        Int value2 = hash2.add(n2).compute();
        assert value1 != value2;
        assert hash2.reset().add(n1).compute() == value1;
        assert hash1.reset().add(n2).compute() == value2;
    }

    void shouldAddUInt128Array() {
// TODO array hashCode() throws unsupported
//        @Inject("hash") HashCollector hash1;
//        @Inject("hash") HashCollector hash2;
//        UInt128 n1 = 234;
//        UInt128 n2 = 567;
//        Int value1 = hash1.add([n1, n2]).compute();
//        Int value2 = hash2.add([n2, n1]).compute();
//        assert value1 != value2;
//        assert hash2.reset().add([n1, n2]).compute() == value1;
//        assert hash1.reset().add([n2, n1]).compute() == value2;
    }

    void shouldAddIntN() {
// TODO array hashCode() throws unsupported as
//        @Inject("hash") HashCollector hash1;
//        @Inject("hash") HashCollector hash2;
//        IntN n1    = 234;
//        IntN n2    = 567;
//        Int value1 = hash1.add(n1).compute();
//        Int value2 = hash2.add(n2).compute();
//        assert value1 != value2;
//        assert hash2.reset().add(n1).compute() == value1;
//        assert hash1.reset().add(n2).compute() == value2;
    }

    void shouldAddIntNArray() {
// TODO array hashCode() throws unsupported
//        @Inject("hash") HashCollector hash1;
//        @Inject("hash") HashCollector hash2;
//        IntN n1    = 234;
//        IntN n2    = 567;
//        Int value1 = hash1.add([n1, n2]).compute();
//        Int value2 = hash2.add([n2, n1]).compute();
//        assert value1 != value2;
//        assert hash2.reset().add([n1, n2]).compute() == value1;
//        assert hash1.reset().add([n2, n1]).compute() == value2;
    }

    void shouldAddUIntN() {
// TODO array hashCode() throws unsupported
//        @Inject("hash") HashCollector hash1;
//        @Inject("hash") HashCollector hash2;
//        UIntN n1   = 234;
//        UIntN n2   = 567;
//        Int value1 = hash1.add(n1).compute();
//        Int value2 = hash2.add(n2).compute();
//        assert value1 != value2;
//        assert hash2.reset().add(n1).compute() == value1;
//        assert hash1.reset().add(n2).compute() == value2;
    }

    void shouldAddUIntNArray() {
// TODO array hashCode() throws unsupported
//        @Inject("hash") HashCollector hash1;
//        @Inject("hash") HashCollector hash2;
//        UIntN n1   = 234;
//        UIntN n2   = 567;
//        Int value1 = hash1.add([n1, n2]).compute();
//        Int value2 = hash2.add([n2, n1]).compute();
//        assert value1 != value2;
//        assert hash2.reset().add([n1, n2]).compute() == value1;
//        assert hash1.reset().add([n2, n1]).compute() == value2;
    }
}
