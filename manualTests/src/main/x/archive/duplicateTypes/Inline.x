// Negative control: duplicate declarations in one source file must also report COMPILER-148.
module Inline {
    class Taken {}
    class Taken {}

    Taken make() = new Taken();
}
