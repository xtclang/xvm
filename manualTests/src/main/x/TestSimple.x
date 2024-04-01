module TestSimple {
    @Inject Console console;

    void run() {
        console.print(test(Int:17));
    }

    Int test(String|Int si) {
        if (si.is(Int)) {
            si = f^(si); // this used to throw in the compiler
            return si;
        }
        TODO
    }

    Int f(Int i) = i;
}