/**
 * The AutoConversion mixin is used as an annotation on methods for the following compile-time
 * purpose:
 *
 * * When the compiler is faced with an incompatible assignment from type A to type B, if type A
 *   contains a no-parameter method annotated with AutoConversion that returns type B, then the
 *   compiler will automatically add an invocation of that method to achieve the necessary type
 *   conversion.
 *
 * This mixin should be used with extreme care:
 *
 * * An automatic conversion should only be used if the reader of the code would anticipate that the
 *   information in the prior form would naturally be converted to the new form.
 *
 * * An automatic conversion should not lose data; it should either move from one representation of
 *   data to another equally precise representation, or it should move from a less precise
 *   representation to a more precise representation. There is both an obvious and a non-obvious
 *   reason for this rule, with the obvious being that one does not wish to lose information without
 *   a warning, or to automatically (implicitly) invoke a method that has a likelihood of throwing
 *   an exception in the event of information loss.
 *
 * * The non-obvious reason is that Ecstasy compiles automatic conversions _depth-first_ in order to
 *   maximize the precision of results. For example, given two `UInt8` values `n1` and `n2` with the
 *   value of `16` in each, the result of `UInt16 n3 = n1 * n2;` is `256`, and not `0` (or an
 *   overflow exception), because the individual values `n1` and `n2` are converted to `UInt16`
 *   **before** they are multiplied together. (In assembly, multiplying two unsigned 8-bit integers
 *   with the value `16` will produce a `0` result, because only the least significant 8 bits of the
 *   binary result `1_0000_0000` are retained.) This would have unanticipated side-effects, though,
 *   if automatic conversions were allowed in the other direction, e.g. from `UInt16` to `UInt8`;
 *   for example, `UInt8 n4 = n3 / n2;` should produce 16, and not zero; because potential
 *   information loss occurs in the conversion, an explicit conversion is required:
 *   `UInt8 n4 = (n3 / n2).toUInt8();`.
 *
 * * One must **always** consider the reader of the code first and foremost, and behavior that is
 *   "automatic" (in the sense of being automatically plucked from the ether and automatically
 *   incorporated without any explicit indication) is almost always an attack on the reader's
 *   senses. Readers can only absorb a small number of such attacks, and only if they deem those
 *   specific uses of "automatic" to be ultimately both obvious and significantly beneficial in
 *   nature. **In other words, "automatic" behavior benefits the writer at the cost of the reader,
 *   and the more easily that the trade-off is accepted, the more likely it is to be wrong.**
 */
mixin AutoConversion
        into Method /* TODO GG remove */ | Property
    {
    }
