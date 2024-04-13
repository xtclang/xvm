/**
 * Very basic union tests.
 */
class Basic {

    void run() {
        basic();
    }

    // -----------------------------
    void basic() {
        typedef (String | Int) as StrInt;
        StrInt id( StrInt x ) { return x; }
        StrInt x = id(7);
        assert x==7;
        assert x!=id("abc");
        x = "def";
        assert x=="def";
        //assert x!= id(9);
    }
}
