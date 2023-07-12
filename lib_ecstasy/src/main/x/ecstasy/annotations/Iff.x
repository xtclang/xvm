/**
 * `Iff` is used to mark a class, property, or method (a "_feature_") as being _conditionally
 * present_. When a feature is conditionally present, its compiled form, called a _template_,
 * exists in the module file, but it may or may not be present at runtime based on the specified
 * condition.
 *
 * There are three basic conditions:
 *
 * * [String.enabled] - similar in concept to the use of `#ifdef` in the C/C++ pre-processor, this
 *   allows a name (such as "test" and "debug") to represent functionality that can be conditionally
 *   enabled; for example: `@Iff("verbose".defined) @Override String toString() {...}`
 *
 * * [Class.present], [Method.present], [Function.present], and [Property.present] - these support
 *   the presence (or absence) of conditionally support modules or any portion thereof, including
 *   the possibility that a class, method, or property is present (and thus useful) in one version
 *   but not another. For example: `class LogFile implements @Iff(Logger.present) Logger {...}`
 *
 * * Module version conditions are used by the compiler and linker to allow multiple module versions
 *   to be present within a single module file, and for other modules to avoid being impacted by
 *   breaking changes across module versions while also potentially exploiting new capabilities
 *   introduced in newer versions. The following are supported:
 *
 * * * Testing whether a module's version is equal-to, not-equal-to, less-than,
 *     less-than-or-equal-to, greater-than, or greater-than-or-equal-to a specified version, for
 *     example `@Iff(LogUtils.version < v:3)`; and
 *
 * * * Testing whether a module's version [satisfies](Version.satisfies) a specified version, for
 *     example `@Iff(LogUtils.version.satisfies(v:3))`.
 *
 * As with any Boolean expression, it is possible to create complex conditions by combining other
 * conditions using the logical `&&` and `||`, using parenthesis to explicitly specify precedence
 * among multiple conditions, and using `!` to invert a condition, for example:
 *
 *     @Iff(LogUtils.version >= v:3 && LogUtils.version < v:6)
 *
 * Or:
 *
 *     @Iff(LogUtils.version.satisfies(v:3)
 *       || LogUtils.version.satisfies(v:4)
 *       || LogUtils.version.satisfies(v:5))
 *
 * Because the expression specified in the `@Iff` annotation must be compiled to a specific binary
 * form that the linker analyzes and operates on, it is a compile-time error to specify any
 * condition this is not explicitly permitted by this documentation.
 *
 * Note: The term "iff" is a well known abbreviation for the phrase "if and only if".
 */
mixin Iff(Boolean include)
        into Class | Property | Method | Function {
}
