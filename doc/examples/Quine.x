/**
 * At compile time, Ecstasy source code has access to a virtual file system that represents the
 * module's source code directory, overlaid by any explicitly provided resource directories. Using
 * directory and/or file literals, Ecstasy source code can obtain the contents of any of those
 * directories and/or files.
 *
 * In this example, the `Quine` module is located in the `Quine.x` file, and obtains its own source
 * code at compile time by referring to its own file as a string literal.
 */
module Quine {
    @Inject Console console;
    void run() {
        console.print($./Quine.x);
    }
}