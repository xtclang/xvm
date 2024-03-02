module TestSimple {
    @Inject Console console;

    void run() {
        Type t = Test;
        console.print("Multimethods:");
        console.print($"{t.multimethods.keys.toString(sep="\n")}");
        console.print("Methods:");
        console.print($"{t.methods.toString(sep="\n")}"); // the order used to be random
    }

    class Test {
        void f1(Int i);
        void f1(String s);
        void f2(String s);
        void f2(Boolean f);
        void f3(Boolean f);
        void f4(Byte b);
    }
}
