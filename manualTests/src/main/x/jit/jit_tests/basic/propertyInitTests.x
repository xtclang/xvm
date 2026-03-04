
package propertyInitTests {

    typedef String|Int as StringOrInt;

    @Inject Console console;

    void run() {
        console.print(">>>> Running PropertyInitTests >>>>");

        Test t = new Test();

        assert t.i    == 100;
        assert t.ni1  == Null;
        assert t.ni2  == 200;
        assert t.s    == "hello";
        assert t.ns1  == "world";
        assert t.ns2  == Null;
        assert t.si1  == "Foo";
        assert t.si2  == 300;
        assert t.nsi1 == "Bar";
        assert t.nsi2 == 301;
        assert t.nsi3 == Null;
        assert t.x    == 400;
        assert t.nx1  == 500;
        assert t.nx2  == Null;

        console.print("<<<< Finished PropertyInitTests <<<<<");
    }

    class Test() {
        Int          i    = 100;
        Int?         ni1  = Null;
        Int?         ni2  = 200;
        String       s    = "hello";
        String?      ns1  = "world";
        String?      ns2  = Null;
        StringOrInt  si1  = "Foo";
        StringOrInt  si2  = 300;
        StringOrInt? nsi1 = "Bar";
        StringOrInt? nsi2 = 301;
        StringOrInt? nsi3 = Null;
        Int128       x    = 400;
        Int128?      nx1  = 500;
        Int128?      nx2  = Null;
    }
}
