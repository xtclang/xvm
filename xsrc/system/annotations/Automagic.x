/**
 * The Automagic mixin is used as an annotation for two purposes:
 *
 * TODO this is no longer true (need a different annotation for it?)
 * * When the compiler is faced with an incompatible assignment from type A to type B, if type A
 *   contains a no-parameter method annotated with Automagic that returns type B, then the compiler
 *   will automatically add an invocation of that method to achieve the necessary type conversion.
 *
 * * When the compiler is aware of a mixin M annotated with Automagic that applies to a certain type
 *   C, the compiler will automatically mix in M to every occurrence of type C.
 *
 * There are several rules for using the Automagic mixin:
 *
 * 1. Do not use the Automagic mixin. This means you.
 * 2. The Automagic mixin is incredibly dangerous, like running with scissors, blindfolded, on an
 *    escalator, with your shoes untied, in hell.
 * 3. Honestly, if your name is not Blaise Pascal, Grace Hopper, Charles Babbage, Anders Hejlsberg,
 *    Guy Steele, Donald Knuth, Ada Lovelace, or John Carmack, do not use it. Especially if your
 *    name is Bjarne Stroustrup -- in which case we will buy you the scissors and an escalator.
 * 4. Only if you understand and agree to the above rules should you even consider using the
 *    Automagic mixin.
 *
 * Specifically, and in all seriousness, one must *always* consider the reader of the code first and
 * foremost, and behavior that is "automatic" (in the sense of being automatically located in the
 * ether and automatically incorporated without any explicit indication) is almost always an attack
 * on the reader's senses. Readers can only absorb a small number of such attacks, and only if they
 * deem those specific uses of "automatic" to be ultimately both obvious and significantly
 * beneficial in nature.
 *
 * In other words, "automatic" benefits the writer at the cost of the reader, and that trade-off is
 * almost *always* incorrect.
 */
mixin Automagic
        into Mixin
    {
    }
