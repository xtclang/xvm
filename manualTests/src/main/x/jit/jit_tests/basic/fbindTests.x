package fbindTests {
        @Inject Console console;

        void run() {
            console.print(">>>> Running FBind Tests >>>>");

            testBindPrimitive();
            testBindNullablePrimitive();
            testBindNullablePrimitiveNullArg();
            testBindNullablePrimitiveNullReturn();
            testBindNullablePrimitiveNullArgAndReturn();
            testBindMultiplePrimitivesMultipleUnboundArgs();

            testBindMultiplePrimitives();
            testBindMultiplePrimitivesMultipleUnboundArgs();
            testBindPrimitivesNotOptimizedAfter();
            testBindPrimitivesNotOptimizedBefore();

            testBindXvmPrimitive();
            testBindNullableXvmPrimitive();
            testBindNullableXvmPrimitiveNullArg();
            testBindNullableXvmPrimitiveNullReturn();
            testBindNullableXvmPrimitiveNullArgAndReturn();

            testBindObject();
            testBindNullableObject();
            testBindNullableObjectNullArg();
            testBindNullableObjectNullReturn();
            testBindNullableObjectNullArgAndReturn();

            testBindChar();
            testBindBoolean();

            console.print("<<<< Finished FBind Tests >>>>");
        }

    void testBindPrimitive() {
        Test<Int> t = new Test(1);
        var result = t.testOne(2, (a, b) -> {
            assert a == 2;
            assert b == 1;
            return 3;
        });
        assert result == 3;
    }

    void testBindNullablePrimitive() {
        Test<Int?> t = new Test(1);
        var result = t.testOne(2, (a, b) -> {
            assert a == 2;
            assert b == 1;
            return 3;
        });
        assert result == 3;
    }

    void testBindNullablePrimitiveNullArg() {
        Test<Int?> t = new Test(1);
        var result = t.testOne(Null, (a, b) -> {
            assert a == Null;
            assert b == 1;
            return 3;
        });
        assert result == 3;
    }

    void testBindNullablePrimitiveNullReturn() {
        Test<Int?> t = new Test(1);
        var result = t.testOne(2, (a, b) -> {
            assert a == 2;
            assert b == 1;
            return Null;
        });
        assert result == Null;
    }

    void testBindNullablePrimitiveNullArgAndReturn() {
        Test<Int?> t = new Test(1);
        var result = t.testOne(Null, (a, b) -> {
            assert a == Null;
            assert b == 1;
            return Null;
        });
        assert result == Null;
    }

    void testBindMultiplePrimitives() {
        Test<Int> t = new Test(1);
        var result = t.testTwo(2, 4, (a, b, c) -> {
            assert a == 2;
            assert b == 4;
            assert c == 1;
            return 3;
        });
        assert result == 3;
    }

    void testBindMultiplePrimitivesMultipleUnboundArgs() {
        Test<Int> t = new Test(1);
        var result = t.testThree(2, 4, (a, b, c) -> {
            assert a == 2;
            assert b == 4;
            assert c == 1;
            return 3;
        });
        assert result == 3;
    }

    void testBindPrimitivesNotOptimizedAfter() {
        Test<Int> t = new Test(1);
        var result = t.testFour(2, (a, b) -> {
            assert a == 2;
            assert b == "1";
            return "foo";
        });
        assert result == "foo";
    }

    void testBindPrimitivesNotOptimizedBefore() {
        Test<Int> t = new Test(1);
        var result = t.testSix(2, (a, b) -> {
            assert a.is(Int) && a == 2;
            assert b.is(Int) && b == 3;
            return 99;
        });
        assert result == 99;
    }

    void testBindXvmPrimitive() {
        Test<Int128> t = new Test(18446744073709551616);
        var result = t.testOne(18446744073709551617, (a, b) -> {
            assert a == 18446744073709551617;
            assert b == 18446744073709551616;
            return 18446744073709551699;
        });
        assert result == 18446744073709551699;
    }

    void testBindNullableXvmPrimitive() {
        Test<Int128?> t = new Test(18446744073709551616);
        var result = t.testOne(18446744073709551617, (a, b) -> {
            assert a == 18446744073709551617;
            assert b == 18446744073709551616;
            return 18446744073709551699;
        });
        assert result == 18446744073709551699;
    }

    void testBindNullableXvmPrimitiveNullArg() {
        Test<Int128?> t = new Test(18446744073709551616);
        var result = t.testOne(Null, (a, b) -> {
            assert a == Null;
            assert b == 18446744073709551616;
            return 18446744073709551699;
        });
        assert result == 18446744073709551699;
    }

    void testBindNullableXvmPrimitiveNullReturn() {
        Test<Int128?> t = new Test(18446744073709551616);
        var result = t.testOne(18446744073709551617, (a, b) -> {
            assert a == 18446744073709551617;
            assert b == 18446744073709551616;
            return Null;
        });
        assert result == Null;
    }

    void testBindNullableXvmPrimitiveNullArgAndReturn() {
        Test<Int128?> t = new Test(18446744073709551616);
        var result = t.testOne(Null, (a, b) -> {
            assert a == Null;
            assert b == 18446744073709551616;
            return Null;
        });
        assert result == Null;
    }

    void testBindObject() {
        Test<String> t = new Test("One");
        var result = t.testOne("Two", (a, b) -> {
            assert a == "Two";
            assert b == "One";
            return "Three";
        });
        assert result == "Three";
    }

    void testBindNullableObject() {
        Test<String> t = new Test("One");
        var result = t.testOne("Two", (a, b) -> {
            assert a == "Two";
            assert b == "One";
            return "Three";
        });
        assert result == "Three";
    }

    void testBindNullableObjectNullArg() {
        Test<String?> t = new Test("One");
        var result = t.testOne(Null, (a, b) -> {
            assert a == Null;
            assert b == "One";
            return "Three";
        });
        assert result == "Three";
    }

    void testBindNullableObjectNullReturn() {
        Test<String?> t = new Test("One");
        var result = t.testOne("Two", (a, b) -> {
            assert a == "Two";
            assert b == "One";
            return Null;
        });
        assert result == Null;
    }

    void testBindNullableObjectNullArgAndReturn() {
        Test<String?> t = new Test("One");
        var result = t.testOne(Null, (a, b) -> {
            assert a == Null;
            assert b == "One";
            return Null;
        });
        assert result == Null;
    }

    void testBindChar() {
        Test<Char> t = new Test('b');
        var result = t.testOne('a', (a, b) -> {
            assert a == 'a';
            assert b == 'b';
            return 'c';
        });
        assert result == 'c';
    }

    void testBindBoolean() {
        Test<Boolean> t = new Test(False);
        var result = t.testOne(True, (a, b) -> {
            assert a == True;
            assert b == False;
            return False;
        });
        assert result == False;
    }

    class Test<Element>(Element e) {

        typedef function Element (Element, Element) as Fn1;

        Element testOne(Element value, Fn1 fn) {
            function Element(Element) f = fn(value, _);
            return f(e);
        }

        typedef function Element (Element, Element, Element) as Fn2;

        Element testTwo(Element value1, Element value2, Fn2 fn) {
            function Element(Element) f = fn(value1, value2, _);
            return f(e);
        }

        Element testThree(Element value1, Element value2, Fn2 fn) {
            function Element(Element,Element) f = fn(value1, _, _);
            return f(value2, e);
        }

        typedef function String (Element, String) as Fn3;

        String testFour(Element value, Fn3 fn) {
            function String(String) f = fn(value, _);
            return f(e.toString());
        }

        String testFive(Element value, Fn3 fn) {
            function String(Element) f = fn(_, "foo");
            return f(e);
        }

        typedef function Element (Number, Number) as Fn4;

        Element testSix(Int i, Fn4 fn) {
            function Element(Number) f = fn(i, _);
            Int i2 = i + 1;
            return f(i2);
        }
    }
}
