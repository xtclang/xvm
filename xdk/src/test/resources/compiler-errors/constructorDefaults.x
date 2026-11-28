module TestConstructorDefaults {
    // The default values of a class's shorthand constructor parameters reach the compiled class
    // only through the synthetic constructor that the shorthand declaration implies. Where an
    // explicit constructor takes its place, the compiler reports a default value that differs from
    // that constructor's as unused. These errors are reported while code is generated, where the
    // explicit constructor's default values are compiled; the cases that compile, the matching
    // ones among them, are in javatools' ConstructorArgumentsTest.

    // the explicit constructor has another default value
    class Appender(Int width, Boolean flush = True) {  // expect-error: COMPILER-210
        construct(Int width, Boolean flush = False) {
            this.width = width;
            this.flush = flush;
        }
        Int width;
        Boolean flush;
    }

    // the explicit constructor has no default value
    class Required(Int width = 4) {  // expect-error: COMPILER-210
        construct(Int width) {
            this.width = width;
        }
        Int width;
    }

    // with neither a shorthand declaration nor any constructor, the synthetic default constructor
    // passes the contribution's arguments, so they are compiled, not unused: a name that names
    // nothing is unresolvable, and the implicit super constructor call that it is in fails
    class Seeded(Int seed) {}
    class Implicit extends Seeded(noSuchName) {}  // expect-error: COMPILER-38, COMPILER-103
}
