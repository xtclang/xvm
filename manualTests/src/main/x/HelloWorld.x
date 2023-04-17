/**
 * A simple test program that writes "Hello World!" to the console.
 * Used, among other things, as sample IDE configuration, with a breakpoint, to understand how
 * the XTC system compiles and bootstraps the smallest possible application, and how to hook
 * into the debugger.
 *
 * TODO: We are working on full IDE support for XTC-lang, which would remove the need for explicit
 *   an "assert:break", to debug this program in an IDE. We will however, keep the "assert:debug"
 *   implementation, as it has other powerful use cases in headless environments, or console debugging.
 */

module HelloWorld
    {
    void run()
        {
        @Inject Console console;
        assert:debug;
        console.print("Hello, world!");
        }
    }
