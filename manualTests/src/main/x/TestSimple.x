module TestSimple {

    @Inject Console console;
    @Inject Clock clock;

    void run() {
        @Future ClassA a;

        clock.schedule(Duration:1s, () -> {
            console.print("assigning a");
            a = new ClassA();
        });
        clock.schedule(Duration:2s, () -> {
            console.print("assigning a.b");
            a.b = new ClassB();
        });
        clock.schedule(Duration:3s, () -> {
            console.print("assigning a.b.i");
            a.b.i = 42;
        });

        console.print("a=" + a.b.i); // this used to assert the compiler
    }

    class ClassA {
        @Future ClassB b;
    }

    class ClassB {
        @Future Int i;
    }
}