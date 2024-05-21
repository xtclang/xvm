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
        basic7();

        multi0();
        multi1();
        multi2();
        multi3();
        multi4();
        multi5();
    }

    Int?        hideI(Int?        x) = x;
    IntLiteral? hideL(IntLiteral? x) = x;
    String?     hideS(String?     x) = x;

    // -----------------------------
    void basic0() {
        Int a = 2;
        Int b = 3;
        Int? c = hideI(a);
        assert ( c?:b) == 2;
    }

    void basic1() {
        Int? c = 2;
        static Int? hideNull() = Null;
        c = hideNull();
        assert ( c?:3) == 3;
    }

    void basic2() {
        IntLiteral? a = hideL(Null);
        Int b = 7;
        assert (a?.toInt64()+1:b) == 7;

        if (b==7) { a = 4; }
        assert (a?.toInt64()+1:b) == 5;
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
        String? s2 = hideS(s);
        if( s2==Null ) { assert; } // Must be in or fails to compile
        static String? foolCompiler(String s) = s;
        s2 = foolCompiler(s2); // reintroduce possibility that s2 is Null
        assert s2=="pos";
        assert s2?.size>=0, True;
    }

    void basic4() {
        Int? n = hideI(Null);
        n ?:= 4;
        assert n==4;

        private Int? pretendNullable(Int n) = n;
        n = pretendNullable(n);
        // No assignment, since n is not-null
        n ?:= 7;
        assert n==4;
    }

    void basic5() {
        String? s2 = hideS("abc");
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

    private static class X {
        Int cnt;
        X  inc () { cnt++;  return this; }
        X? incQ() { cnt++;  return this; }
        void empty() { }
        static X? make() { return new X(); }
    }
    void basic7() {
        X? x = X.make();
        x?.inc().empty();
        assert x?.cnt == 1;
    }

    // -----------------------------

    private conditional String? trinary(Int x) {
        return (x==0) ? False : ((x==1) ? (True,Null) : (True,"abc"));
    }

    void multi0() {
        assert !(String? s0 := trinary(0));
        assert String? s1 := trinary(1), s1==Null;
        assert String? s2 := trinary(2), s2=="abc";
    }

    void multi1() {
        if (String? s0 := trinary(0), // Always false, so IF is false
            String? s1 := trinary(1),
            s0? == s1?) {
            assert False;
        } else {
            assert True;
        }
    }

    void multi2() {
        if (String? s0 := trinary(1), // Always null
            String? s1 := trinary(2),
            s0? == s1?) {       // So this fails
            assert False;
        } else {
            assert True;
        }
    }

    void multi3() {
        if (String? s0 := trinary(2), // Always abc
            String? s1 := trinary(2),
            s0? == s1?) {       // So this passes
            assert True;
        } else {
            assert False;
        }
    }

    void multi4() {
        String? x = trinary(1)  // Returns (True,Null)
            ?: hideS("def")     // Tests condition (true), so that's yer answer, no running hideS
            ?: "xyz";
        assert x==Null;
    }

    void multi5() {
        X? x = Null;
        x = X.make()?.incQ()?.inc();
        assert x.cnt==2;
    }

}
