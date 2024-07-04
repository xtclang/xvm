// this test is a copy of not-yet submitted tck test "clazz/Medium.x" which failed to compile

module TestSimple {
    @Inject Console console;

    void run(String[] args=[""]) {
        incorp();
    }

    mixin MixIn<Element> into Base1<Element> | Base2<Element> {
        @Override
        String add(Element e) = $"MX[{e=} " + super(e) + " ]MX";
    }

    class Super1<Element> {
        String add(Element e) = $"S[{e=}]S";
    }
    class Base1<Element> extends Super1<Element> incorporates MixIn<Element> {
        @Override String add(Element e) = $"B[{e=} " + super(e) + " ]B";
    }
    class Derived1<Element> extends Base1<Element> {
        @Override String add(Element e) = $"D[{e=} " + super(e) + " ]D";
    }

    class Super2<Element> {
        String add(Element e) = $"s[{e=}]s";
    }
    class Base2<Element> extends Super2<Element> incorporates MixIn<Element> {
        @Override String add(Element e) = $"b[{e=} " + super(e) + " ]b";
    }
    class Derived2<Element> extends Base2<Element> {
        @Override String add(Element e) = $"d[{e=} " + super(e) + " ]d";
    }

    void incorp() {
        String baseint1 = new Base1   < Int  >().add( 123 );
        String basestr1 = new Base1   <String>().add("abc");
        String dervint1 = new Derived1< Int  >().add( 123 );
        String dervstr1 = new Derived1<String>().add("abc");
        assert baseint1 ==         "B[e=123 MX[e=123 S[e=123]S ]MX ]B"   ;
        assert basestr1 ==         "B[e=abc MX[e=abc S[e=abc]S ]MX ]B"   ;
        assert dervint1 == "D[e=123 B[e=123 MX[e=123 S[e=123]S ]MX ]B ]D";
        assert dervstr1 == "D[e=abc B[e=abc MX[e=abc S[e=abc]S ]MX ]B ]D";

        String baseint2 = new Base2   < Int  >().add( 123 );
        String basestr2 = new Base2   <String>().add("abc");
        String dervint2 = new Derived2< Int  >().add( 123 );
        String dervstr2 = new Derived2<String>().add("abc");
        assert baseint2 ==         "b[e=123 MX[e=123 s[e=123]s ]MX ]b"   ;
        assert basestr2 ==         "b[e=abc MX[e=abc s[e=abc]s ]MX ]b"   ;
        assert dervint2 == "d[e=123 b[e=123 MX[e=123 s[e=123]s ]MX ]b ]d";
        assert dervstr2 == "d[e=abc b[e=abc MX[e=abc s[e=abc]s ]MX ]b ]d";
    }
}
