// NOTE: THE COMPILATION OF THIS MODULE SHOULD PRODUCE TWO COMPILER ERRORS
module TestSimple {
    void run() {
        static const Point(@Volatile Int x, Int y); // @Volatile annotation used to NPE the compiler
    }

    class C {
        @Volatile Int x; // this used to compile without an error
    }
}