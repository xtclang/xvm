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
        StrInt x = 7;
        assert x==7;
        assert x!="abc";
        x = "def";
        assert x=="def";
        //assert x!= 9;
    }
}
