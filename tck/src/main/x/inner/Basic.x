/**
 * Basic inner/outer virtual class tests.
 */
class Basic {

    void run() {
        testBaseConstruct();
        testDerivedConstruct();
        testCallChain();
    }

    static class Base {
        @Override
        String toString() = "B";

        class Child {
            String f1(String s) = f2(s + "->BC.f1");
            String f2(String s) =    s + "->BC.f2";

            @Override
            String toString() = $"{outer}.C";
        }
    }

    static class Derived extends Base {
        @Override
        String toString() = $"(D->{super()})";

        @Override
        class Child {
            @Override
            String f2(String s) = super(s + "->DC.f2");
        }
    }

    void testBaseConstruct() {
        Base b = new Base();
        Base.Child c = b.new Child();
        assert c.toString() == "B.C";
    }

    void testDerivedConstruct() {
        Base b = new Derived();
        Base.Child c = b.new Child();
        assert c.toString() == "(D->B).C";
    }

    void testCallChain() {
        Base b = new Derived();
        Base.Child c = b.new Child();
        assert c.f1("") == "->BC.f1->DC.f2->BC.f2";
    }
}