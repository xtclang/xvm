/**
 * Very basic class nesting
 */
class Basic {

    void run() {
        basic0();
        basic1();
        //basic2();
    }

    // -----------------------------
    void basic0() {
        // Class is static, or not.
        // Has 2 local type parameters.
        // Inherits a 3rd from Appender
        // Constructor takes a To/Int64.GOLD, From/String.Gold, Element/Float.GOLD, Int64/7
        static
        class StaticClass<To, From>(Int arg) implements Appender<Float> { @Override Appender<Float> add(Float v) = this; }
        class NestedClass<To, From>(Int arg) implements Appender<Float> { @Override Appender<Float> add(Float v) = this; }
        StaticClass sc = new StaticClass<Int64,String>(7);
        NestedClass nc = new NestedClass<Int64,String>(7);
        assert sc.Element == nc.Element; // Read Appender type parameters
    }

    void basic1() {
        // Class is static, or not.
        // Has 2 local type parameters.
        // 3rd type parm from Appender is same as From, not extra type
        // Constructor takes a To/Int64.GOLD, From/String.Gold, Element/Float.GOLD, Int64/7
        static
        class StaticClass<To, From>(Int arg) implements Appender<From> { @Override Appender<From> add(From v) = this; }
        class NestedClass<To, From>(Int arg) implements Appender<From> { @Override Appender<From> add(From v) = this; }
        StaticClass sc = new StaticClass<Int64,String>(7);
        NestedClass nc = new NestedClass<Int64,String>(7);
        assert sc.Element == nc.Element; // Read Appender type parameters
    }

    //void basic2() {
    //    import ecstasy.collections.Hasher;
    //    class MyMap<A,B,C extends Hashable> extends HashMap<C,A> { // Swapping type args
    //        construct(A a, B b, C c) { construct MyMap(0); }
    //        @Override construct(MyMap<A,B,C> mm) { super(mm); } // Duplicable
    //        @Override construct(Int cap=0) { super(cap); } // Replicable
    //        @Override construct(Hasher<Key> hasher, Int cap = 0) { super(hasher, cap);  }
    //    }
    //    MyMap bar = new MyMap<Int64,Float64,String>(17,3.14,"abc");
    //}

}
