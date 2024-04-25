/**
 * Very basic elvis tests.
 */
class Basic {

    void run() {
        basic0();
        basic1();
        basic2();
        basic3();
        basic4();
        basic5();
        basic6();
    }

    // -----------------------------
    void basic0() {
        Int a = 2;
        Int b = 3;
        Int? c = a;
        assert ( c?:b) == 2;
    }

    void basic1() {
        Int? c = 2;
        static Int? hideNull() = Null;
        c = hideNull();
        assert ( c?:3) == 3;
    }

    void basic2() {
        IntLiteral? a = Null;
        Int b = 7;
        assert (a?.toInt64():b) == 7;

        if (b==7) { a = 4; }
        assert (a?.toInt64():b) == 4;
    }

    private conditional String checkPositive(Int i) = i < 0 ? False : (True, "pos");

    void basic3() {
        if (String s := checkPositive(17)) {
            assert s=="pos";
        }
        // BAST appears incorrect, drops the "!"
        //assert !(String s := checkPositive(-17));
        String s = "neg";
        s := checkPositive(-99);
        assert s=="neg";
        s := checkPositive(99);
        assert s=="pos";
        String? s2 = s;
        if( s2==Null ) { assert; } // Must be in or fails to compile
        static String? foolCompiler(String s) = s;
        s2 = foolCompiler(s2); // reintroduce possibility that s2 is Null
        assert s2=="pos";
        assert s2?.size>=0, True;
    }

    void basic4() {
        Int? n = Null;
        n ?:= 4;
        assert n==4;

        private Int? pretendNullable(Int n) = n;
        n = pretendNullable(n);
        // No assignment, since n is not-null
        n ?:= 7;
        assert n==4;
    }

    void basic5() {
        String? s2 = "abc";
        assert String s3 ?= s2;
        if( s2==Null ) { assert; } // Must be in or fails to compile
        static String? foolCompiler(String s) = s;
        s2 = foolCompiler(s2); // reintroduce possibility that s2 is Null
        assert s2=="abc";
    }

    void basic6() {
        Char[] chars = "1aA!\n$£€".toCharArray();
        // this also tests the conditional UInt8 to Int conversion
        assert Int n := chars[0].asciiDigit(), n == 1;
        assert !chars[1].asciiDigit();
        assert n == 1;
    }

    // -----------------------------

    private conditional String? trinary(Int x) {
        return (x==0) ? False : ((x==1) ? (True,Null) : (True,"abc"));
    }

    //void multi() {
    //    if (Orderer? thisOrder := this.ordered(),
    //        Orderer? thatOrder := values.ordered(),
    //        thisOrder? == thatOrder?) {
    //
    //}

}
