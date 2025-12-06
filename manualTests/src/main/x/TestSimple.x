module TestSimple {
    @Inject Console console;

    static ClassA a = new ClassA();

    void run() {
        console.print(a);
    }

    @AutoFreezable
    class ClassA { // this used to compile and fail at run-time
    }
}