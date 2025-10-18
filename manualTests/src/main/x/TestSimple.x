module TestSimple {

    @Inject Console console;

    void run() {
        ClassA a = new ClassA();
        console.print(a.b.i);
        a = a.makeImmutable(); // this used to fail to freeze "a.b"
        a.b.i[0]++;            // and this used to be incorrectly allowed
        console.print(a.b.i);
    }

    class ClassA {
        @Lazy
        ClassB b.calc() = new ClassB();
    }

    class ClassB {
        Int[] i = new Int[1] (i -> i);
    }
}