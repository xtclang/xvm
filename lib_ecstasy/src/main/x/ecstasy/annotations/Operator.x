/**
 * The `@Op` mixin is used to annotate a method as being a candidate for an operator. When such a
 * method (matching the necessary argument and return value types) is found, the compiler will use
 * it to implement the operation. For example:
 *
 * * When the compiler encounters a right-associative unary complement `~` expression (or `!`
 *   expression on a Boolean type), it searches for a method on the rightward expression's type that
 *   is annotated by `@Op("~")`.
 *
 * * When the compiler encounters a right-associative unary negation `-` expression, it searches for
 *   a method on the rightward expression's type that is annotated by `@Op("-#")`.
 *
 * * When the compiler encounters a left-associative addition `+` expression,  it searches for a
 *   method on the leftward expression's type that is annotated by `@Op("+")`.
 *
 * * When the compiler encounters a left-associative subtraction `-` expression,  it searches for a
 *   method on the leftward expression's type that is annotated by `@Op("-")`.
 *
 * * When the compiler encounters a left-associative multiplication `*` expression,  it searches for
 *   a method on the leftward expression's type that is annotated by `@Op("*")`.
 *
 * * When the compiler encounters a left-associative division `/` expression,  it searches for a
 *   method on the leftward expression's type that is annotated by `@Op("/")`.
 *
 * * When the compiler encounters a left-associative modulo `%` expression,  it searches for a
 *   method on the leftward expression's type that is annotated by `@Op("%")`.
 *
 * * When the compiler encounters a left-associative division-and-remainder `/%` expression,  it
 *   searches for a method on the leftward expression's type that is annotated by `@Op("/%")`.
 *
 * * When the compiler encounters a left-associative bitwise-and `&` expression,  it searches for a
 *   method on the leftward expression's type that is annotated by `@Op("&")`.
 *
 * * When the compiler encounters a left-associative bitwise-or `|` expression,  it searches for a
 *   method on the leftward expression's type that is annotated by `@Op("|")`.
 *
 * * When the compiler encounters a left-associative bitwise-xor `^` expression,  it searches for a
 *   method on the leftward expression's type that is annotated by `@Op("^")`.
 *
 * * When the compiler encounters a left-associative shift-left `<<` expression,  it searches for a
 *   method on the leftward expression's type that is annotated by `@Op("<<")`.
 *
 * * When the compiler encounters a left-associative shift-right `>>` expression,  it searches for a
 *   method on the leftward expression's type that is annotated by `@Op(">>")`.
 *
 * * When the compiler encounters a left-associative shift-all-right `>>>` expression,  it searches
 *   for a method on the leftward expression's type that is annotated by `@Op(">>>")`.
 *
 * * When the compiler encounters a left-associative inclusive range `..` expression,  it searches
 *   for a method on the leftward expression's type that is annotated by `@Op("..")`.
 *
 * * When the compiler encounters a left-associative exclusive range `..` expression (one that is
 *   prefixed with `[` and suffixed with `)`),  it searches for a method on the leftward
 *   expression's type that is annotated by `@Op("..<")`.
 *
 * * When the compiler encounters an array access expression as an r-value, it searches for a method
 *   on the array expression's type that is annotated by `@Op("[]")`.
 *
 * * When the compiler encounters an array mutation expression as an l-value, it searches for a
 *   method on the array expression's type that is annotated by `@Op("[]=")`.
 *
 * * When the compiler encounters an array slice expression, it searches for a method on the array
 *   expression's type that is annotated by `@Op("[..]")`.
 *
 * This annotation should be used with extreme care:
 *
 * * Binary operators are left associative, and when possible should be commutative. If you aren't
 *   certain about the implications of this rule, then do not use this annotation.
 *
 * * Each operator has an explicit use case. Implementing an operator to provide an alternative use
 *   is forbidden by the law of good taste. Failure to abide by this law will result in the eternal
 *   punishment of maintaining poorly written C++ code, without access to coffee.
 *
 * * Operators should be extremely obvious within their usage context. If you aren't certain that an
 *   operator is extremely obvious within its usage context, then do not use this annotation.
 *
 * * Operators are assumed to have certain behaviors, based both upon a long lineage of language
 *   history, and upon what a programmer may know about this specific language. Any usage of this
 *   annotation must serve to reinforce that understanding, and must never conflict with that.
 *
 * * One must **always** consider the reader of the code first and foremost, and behavior that is
 *   hidden behind an operator (in the sense of it being automatically plucked from the ether and
 *   automatically incorporated without a named method reference or an explicit method call) is
 *   almost always an attack on the reader's senses. Readers can only absorb a small number of such
 *   attacks, and only if they deem those specific uses of an operator to be ultimately both obvious
 *   and significantly beneficial in nature. **In other words, operators benefit the writer at the
 *   cost of the reader, and the more easily that the trade-off is accepted, the more likely it is
 *   to be wrong.**
 */
mixin Operator(String? token = Null)
        into Method {
}
