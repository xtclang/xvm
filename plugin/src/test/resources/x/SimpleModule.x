/**
 * Simple test module for compiler reentrancy testing.
 */
module SimpleModule {
    void run() {
        @Inject Console console;
        console.print("Hello, World!");
    }
}
