module TestMisc2 {
    @Inject Console console;

    void run() {
        // Mar/12/2024 - Tests not currently working with the javatools_backend
        testChars();
        testCast(); // Requires an intersection type of `Int` and `String`
        testRecursiveType();
        testChild();
        testSideEffects();
    }

    // Requires unicode character handling
    void testChars() {
        console.print("\n** testChars()");

        Char[] chars = "1aA!\n$£€".toCharArray();
        for (Char ch : chars) {
            String dec = "";
            if (Int value := ch.decimalValue()) {
                dec = $"\'{value}\'";
            }

            console.print($|char {ch.toSourceString()}, unicode={ch.unicode}, cat={ch.category},\
                             | lower={ch.lowercase.toSourceString()}, upper={ch.uppercase.toSourceString()},\
                             | title={ch.titlecase.toSourceString()}, dec={dec}, num={ch.numericValue}
                             );
        }

        // this also tests the conditional UInt8 to Int conversion
        assert Int n := chars[0].asciiDigit(), n == 1;
        assert !chars[1].asciiDigit();
    }

    // Requires an intersection type of `Int` and `String`
    void testCast() {
        console.print("\n** testCast()");

        Int    i = 42;
        Object o = i;
        console.print("o=" + o);
        Int    n = o.as(Int);
        console.print("n=" + n);

        Object o2 = Int:4;
        console.print("o2=" + o2);

        try {
            // Requires an intersection type of `Int` and `String` here
            console.print("i.as(String) should throw " + i.as(String));
        } catch (Exception e) {
            console.print("i.as(String) failed correctly: \"" + e.text + '"');
        }
    }

    void testRecursiveType() {
        console.print("\n** testRecursiveType()");

        typedef (Nullable | Int | List<Manifold>) as Manifold;

        Manifold m1 = 9;
        Manifold m2 = [m1];
        Manifold m3 = [m2];

        console.print(m1);
        console.print(m2);
        console.print(m3);

        console.print(report(m3));

        static String report(Manifold m) {
            if (m == Null) {
                return "Null";
            }
            if (m.is(Int)) {
                return "Integer";
            }
            return $"array of {report(m[0])}";
        }
    }

    void testChild() {
        console.print("\n** testChild()");

        Order order = new Order("Order-17");
        console.print("order=" + order);

        Order.OrderLine line = order.addLine("item-5");
        console.print("line=" + line);

        order = new EnhancedOrder("Order-18");
        line = order.addLine("item-6");
        console.print("line=" + line);
    }

    class Order(String id) {
        Int lineCount;

        @Override
        String toString() {
            return id;
        }

        OrderLine addLine(String descr) {
            return new OrderLine(++lineCount, descr);
        }

        class OrderLine(Int lineNumber, String descr) {
            @Override
            String toString() {
                return this.Order.toString() + ": " + descr;
            }
        }
    }

    class EnhancedOrder(String id)
            extends Order(id) {
        @Override
        class OrderLine(Int lineNumber, String descr) {
            @Override
            String toString() {
                return this.EnhancedOrder.toString() +
                    ": " + lineNumber + ") " + descr;
            }
        }
    }

    void testSideEffects() {
        console.print("** testSideEffects()");

        // tuple
        {
            Int x = 3;
            function Int() fn = () -> (x, ++x)[0];
            assert fn() == 3 as "tuple side-effect";
        }

        // invoke
        {
            Int x = 3;
            function Int() fn = () -> x.minOf(++x);
            assert fn() == 3 as "invoke side-effect";
        }

        // new
        {
            static const Point(Int x, Int y);

            Int x = 3;
            function Int() fn = () -> (new Point(x, ++x)).x;
            assert fn() == 3 as "new side-effect";
        }

        // cmp
        {
            Int x = 3;
            function Boolean() fn = () -> (x < ++x);
            assert fn() as "cmp side-effect";
        }

        // cmp2
        {
            Int x = 3;
            function Boolean() fn = () -> (++x < ++x);
            assert fn() as "cmp2 side-effect";
        }

        // cmp chain
        {
            Int x = 3;
            function Boolean() fn = () -> x <= 3 < ++x;
            assert fn() as "cmpChain side-effect";
        }

        // relOp
        {
            Int x = 3;
            function Int() fn = () -> x + ++x;
            assert fn() == 7 as "relOp side-effect";
        }

        // list
        {
            Int x = 3;
            function Int() fn = () -> [x, ++x, ++x][0];
            assert fn() == 3 as "list side-effect";
        }

        // map
        {
            Int x = 3;
            function Int?() fn = () -> Map<String, Int>:["a"=x, "b"=++x, "c"=++x].getOrNull("a");
            assert fn() == 3 as "map side-effect";
        }

        // unpack
        {
            Int x = 3;
            function (Int, Int)() fn = () -> (x, ++x);
            assert fn() == 3 as "unpacked side-effect";
        }

        // return
        {
            static (Int, Int) fn() {
                function void (Int) log = (Int v) -> {};

                @Watch(log) Int x = 3;
                return x, x++;
            }
            (Int x, Int y) = fn();
            assert x == 3 as "return side-effect";
        }
    }

}