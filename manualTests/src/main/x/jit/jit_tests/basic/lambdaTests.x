package lambdaTests {
    @Inject Console console;

    void run() {
        console.print(">>>> Running LambdaTests >>>>");

        test1();
        test2();
        test3();
        test4();
    }

    void test1() {
        // lambda
        /* This should generate the following:
            a) a synthetic lambda method:
                static void lambda¤1(Ctx ctx) {
                    console.print("Hello");
                }

            b) the code that creates the standard MethodHandle:
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                MethodHandle stdHandle = lookup.findStatic(
                    getClass(), "lambda¤1",
                    MethodType.methodType(Ctx.class));

            c) instantiation of the function object:
                f1 = new nFunction(ctx, stdHandle, null, true);
         */
        function void() f1 = () -> {
            @Inject Console console;
            console.print("test1_lambda1");
        };

        /* This should generate the following:
            f1.stdMethod.invoke();
        */
        f1();

        /* This should generate the following:
            a) no op
            b) the code that creates the standard MethodHandle:
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                MethodHandle stdHandle = lookup.findStatic(
                    getClass(), "test1a",
                    MethodType.methodType(Ctx.class));

            c) instantiation of the function object:
                f2 = new nFunction(ctx, stdHandle, null, true);
         */

        // static
        function void() f2 = test1_static_inner;
        f2();

        f2 = test1_static_outer;
        f2();

        static void test1_static_inner() {
            @Inject Console console;
            console.print("test1_static_inner");
        }

        // instance
        function void() f3 = test1_instance_outer;
        f3();

        f3 = test1_instance_inner;
        f3();

        void test1_instance_inner() {
            @Inject Console console;
            console.print("test1_instance_inner");
        }
    }

    static void test1_static_outer() {
        @Inject Console console;
        console.print("test1_static_outer");
    }

    void test1_instance_outer() {
        console.print("test1_instance_outer");
    }

    void test2() {
        /* This should generate the following:
            a) two methods:
                static Int64 lambda¤2(Ctx ctx) {
                    return Int64.box(lambda#1$p());
                }
                static long lambda¤2$p(Ctx ctx) {
                    return 42;
                }

            b) the code that creates two MethodHandle objects:
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                MethodHandle stdHandle = lookup.findStatic(
                    getClass(), "lambda¤2$p",
                    MethodType.methodType(Ctx.class, long.class)
                    );
                MethodHandle optHandle = lookup.findStatic(
                    getClass(), "lambda¤2",
                    MethodType.methodType(Ctx.class, Int64.class)
                    );

            c) instantiation of the function object:
                f1 = new nFunction(ctx, stdHandle, optHandle, true);
         */

        function Int() f1 = () -> 42;

        Int n = f1();
        assert n == 42;

        function Int() f2 = test2_static_outer;
        assert f2() == 43;

        f2 = test2_static_inner;
        assert f2() == 44;

        static Int test2_static_inner() = 44;

        function Int() f3 = test2_instance_outer;
        assert f3() == 45;

        f3 = test2_instance_inner;
        assert f3() == 46;

        Int test2_instance_inner() {
            console.print("test2_instance_inner");
            return 46;
        }
    }

    static Int test2_static_outer() = 43;

    Int test2_instance_outer() {
        console.print("test2_instance_outer");
        return 45;
    }

    void test3() {
        Base d = new Derived();
        d.testSuper();
    }

    class Base() {
        void testSuper() = console.print("Base.test");
    }

    class Derived() extends Base {
        @Override void testSuper() {
            console.print("Derived.test");
            function void () su = super;
            call(su);
        }

        void call(function void () f) = f();
    }

    void test4() {
        function Int(Int) f = mul(1, _);
        assert f(2) == 5;

        f = mul(_, 1);
        assert f(2) == 4;

        function Int(String) log = log(2, _);
        assert log("twice") == -1;

        static Int mul(Int x, Int y) = x + 2*y;

        static Int log(Int count, String message) {
            while (count-- > 0) {
                console.print(message);
            }
            return count;
        }
    }
}