module TestContributionArguments {
    // A class's contribution arguments, as in "extends Base(seed)", reach the compiled class only
    // through the synthetic constructor that a shorthand declaration implies. Where explicit
    // constructors stand in its place, the arguments are never compiled, so the compiler reports
    // them as unused, unless they match the ones that the explicit constructor passes. These
    // errors are reported while names are resolved; the cases that compile, the matching ones
    // among them, are in javatools' ConstructorArgumentsTest.
    class Base {
        construct(Int n) {}
    }

    // the arguments name nothing: no constructor passes them, so nothing compiles them
    class Unresolvable extends Base(noSuchName, alsoMissing) {  // expect-error: COMPILER-188
        construct(Int n) {
            construct Base(n);
        }
    }

    // the shape of jsondb's JsonLogStoreBase: the name looks like the constructor's parameter, but
    // no constructor takes the synthetic one's place, so it names no parameter
    class Store extends Base(n) {  // expect-error: COMPILER-188
        construct(Int n) {
            construct Base(n);
        }
    }

    // the explicit constructor passes another parameter
    class OtherParameter(Int x, Int y) extends Base(x) {  // expect-error: COMPILER-188
        construct(Int x, Int y) {
            construct Base(y);
            this.x = x;
            this.y = y;
        }
    }

    // the explicit constructor passes another literal
    class OtherLiteral extends Base(1) {  // expect-error: COMPILER-188
        construct() {
            construct Base(2);
        }
    }

    // the name is a parameter only of the explicit constructor
    class ConstructorParameter extends Base(n) {  // expect-error: COMPILER-188
        construct(Int n = 0) {
            construct Base(n);
        }
    }

    // an expression never matches, not even the same one
    class SameExpression(Int x) extends Base(x + 1) {  // expect-error: COMPILER-188
        construct(Int x) {
            construct Base(x + 1);
            this.x = x;
        }
    }

    // an enum value has no compositions, which the check failed on with an internal error; the
    // error that remains is that an enum value needs a constructor without parameters
    enum Color {
        Red {
            construct(Int n = 0) {}
        },
        Green {  // expect-error: COMPILER-62
            construct(Int n) {}
        }
    }
}
