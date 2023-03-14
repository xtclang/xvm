/**
 * This is the prototypical "Hello, World!" example. It is mandated by the programming language
 * gods, but nobody remembers why.
 *
 * To compile this example, execute the following command line from within the directory containing
 * this file:
 *
 *     xtc HelloWorld
 *
 * Then, to run this example:
 *
 *     xec HelloWorld
 */
module HelloWorld
    {
    void run()
        {
        @Inject Console console;
        console.print("Hello, World!");
        }
    }
