module test8.examples.org {
    void run() {
        test1();
    }

    void test1() {
        // lambda
        /* This should generate the following:
            a) a method:
                static void lambda¤1(Ctx ctx) {
                    console.print("Hello");
                }

            b) the code that instantiates a function class:
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                MethodHandle stdHandle = lookup.findStatic(
                    getClass(), "lambda¤1",
                    MethodType.methodType(Ctx.class));

            c) instantiate the function object:
                f1 = new nFunction$ꖛ0(ctx, stdHandle, null, true);
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
            b) the code that instantiates a function class:
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                MethodHandle stdHandle = lookup.findStatic(
                    getClass(), "test1a",
                    MethodType.methodType(Ctx.class));

            c) instantiate the function object:
                new nFunction$ꖛ0(ctx, stdHandle, null, true);
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
        @Inject Console console;
        console.print("test1_instance_outer");
    }

//    void test2() {
//        /* This should generate the following:
//            a) two methods:
//                static Int64 lambda¤2(Ctx ctx) {
//                    return Int64.box(lambda#1$p());
//                }
//                static long lambda¤2$p(Ctx ctx) {
//                    return 42;
//                }
//
//            b) the code that instantiates a function class:
//                MethodHandles.Lookup lookup = MethodHandles.lookup();
//                MethodHandle stdHandle = lookup.findStatic(
//                    getClass(), "lambda¤2$p",
//                    MethodType.methodType(Ctx.class, long.class)
//                    );
//                MethodHandle optHandle = lookup.findStatic(
//                    getClass(), "lambda¤2",
//                    MethodType.methodType(Ctx.class, Int64.class)
//                    );
//
//            c) instantiate the function object:
//                new ¤fnꖛ2(ctx, stdHandle, optHandle, true);
//         */
//        @Inject Console console;
//        function Int() f = () -> 42;
//
//        Int n = f();
//        console.print(n);
//    }
}