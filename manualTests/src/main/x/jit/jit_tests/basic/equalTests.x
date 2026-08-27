package equalTests {

    void run() {
        testEqualityRouting();
    }

    void testEqualityRouting() {
        Derived o1 = new Derived("hello");
        Derived o2 = o1;

        assert compareBase(o1, o2);
        assert compareSimple(o1, o2);
        assert compareDerived(o1, o2);

        o2 = new Derived("hello");
        assert !compareBase(o1, o2);
        assert compareSimple(o1, o2);
        assert compareDerived(o1, o2);

        Boolean compareBase(Base o1, Base o2) = o1 == o2;
        Boolean compareSimple(Simple o1, Simple o2) = o1 == o2;
        Boolean compareDerived(Derived o1, Derived o2) = o1 == o2;

        class Base(String text);
        class Simple(String text) extends Base(text) {
            @Override
            static <CompileType extends Simple> Boolean equals(CompileType o1, CompileType o2) =
                o1.text == o2.text;
        }
        class Derived(String text) extends Simple(text);
    }

}
