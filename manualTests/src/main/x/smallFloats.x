/**
 * Shared small floating-point tests, run unchanged by the interpreter and the JIT.
 */
module TestSmallFloats {
    void run() {
        new Float16Tests().run();
        new Float8e4Tests().run();
        new Float8e5Tests().run();
        new BFloat16Tests().run();

        @Inject Console console;
        console.print("Small floating-point tests passed");
    }
}
