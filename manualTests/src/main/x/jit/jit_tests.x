/**
 * This module will run all of the JIT tests.
 *
 * xtc run -L build/xtc/main/lib -o build/xtc/main/lib --jit src/main/x/jit/jit_tests.x
 */
module jit_tests.examples.org {

    @Inject Console console;

    void run() {
        console.print(">>>> Running JIT tests >>>>");
        Boolean passed = True;

        passed &= new gp_ops.TestRunner().run();
        passed &= new ip_ops.TestRunner().run();
        passed &= new numbers.TestRunner().run();

        console.print("<<<< Finished JIT tests <<<<");
        if (passed) {
            console.print("All JIT tests passed");
        } else {
            console.print("One or more JIT tests failed");
        }
    }
}
