package constuctTests {

    void run() {
        testReplicable();
        testDuplicable();
        testOptimizedVirtualConstructor();
    }

    void testReplicable() {
        Base replicate(Base original) = original.new();

        Base replica = replicate(new Derived());
        assert replica.is(Derived);
    }

    class Base
            implements Replicable {}

    class Derived
            extends Base {}

    void testDuplicable() {
        Copy original = new Copy(17);
        Copy duplicate = original.duplicate().as(Copy);

        assert duplicate != original;
        assert duplicate.hasValue(17);
    }

    class Copy(Int value)
            implements Duplicable {

        @Override
        construct(Copy that) {
            value = that.value;
        }

        Boolean hasValue(Int expected) = value == expected;
    }

    void testOptimizedVirtualConstructor() {
        Valued create(Valued original, Int value) = original.new(value);

        Valued replica = create(new Value(0), 42);

        assert replica.is(Value);
        assert replica.as(Value).hasValue(42);
    }

    interface Valued {
        construct(Int value);
    }

    class Value
            implements Valued {

        @Override
        construct(Int value) {
            this.value = value;
        }

        Int value;

        Boolean hasValue(Int expected) = value == expected;
    }
}
