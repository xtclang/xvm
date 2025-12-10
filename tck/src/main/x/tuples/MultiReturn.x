/**
 * Basic multi-return tests
 */
class MultiReturn {

    void run() {
        testBasic();
        testIgnore0();
        testIgnore2();
        //testFunFun();
    }

    (Int, String, Float64) funISF(Int i, String s, Float64 f) {
        return (i*i, s+"abc", f+i);
    }

    void testBasic() {
        (Int i, String s, Float64 f ) = funISF(2,"def",3.14);
        assert i==4;
        assert s=="defabc";
        assert f==5.140000000000001;
    }

    void testIgnore0() {
        (Int i, _, _ ) = funISF(2,"def",3.14);
        assert i==4;
    }

    void testIgnore2() {
        (_, _, Float64 f ) = funISF(2,"def",3.14);
        assert f==5.140000000000001;
    }

    //// Return a function returning a tuple
    //static const E<Key,Val>(Key key, Val val) {
    //    function (Key, Val) (E<Key, Val>) entryAssociator() {
    //        return e -> (e.key, e.val);
    //    }
    //}
    //
    //void testFunFun() {
    //    E e = new E<>("ghi",16);
    //    var fun = e.entryAssociator();
    //    (var x, var y ) = fun(e);
    //    assert x=="ghi";
    //    assert y==16;
    //}

}
