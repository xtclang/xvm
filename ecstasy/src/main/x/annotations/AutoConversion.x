/**
 * The AutoConversion mixin is used as an annotation on methods for the following compile-time
 * purpose:
 *
 * * When the compiler is faced with an incompatible assignment from type A to type B, if type A
 *   contains a no-parameter method annotated with AutoConversion that returns type B, then the
 *   compiler will automatically add an invocation of that method to achieve the necessary type
 *   conversion.
 *
 * This mixin should be used with extreme care. One must *always* consider the reader of the code
 * first and foremost, and behavior that is "automatic" (in the sense of being automatically located
 * in the ether and automatically incorporated without any explicit indication) is almost always an
 * attack on the reader's senses. Readers can only absorb a small number of such attacks, and only
 * if they deem those specific uses of "automatic" to be ultimately both obvious and significantly
 * beneficial in nature.
 *
 * In other words, "automatic" behavior benefits the writer at the cost of the reader, and the more
 * easily that the trade-off is accepted, the more likely it is to be wrong.
 */
mixin AutoConversion
        into Method
    {
    }
