/**
 * This module will run all of the JIT tests.
 *
 * xtc run -L build/xtc/main/lib -o build/xtc/main/lib --jit src/main/x/jit/jit_tests.x
 */
module jit_tests.examples.org {

    @Inject Console console;

    void run() {
        console.print(">>>> Running JIT tests >>>>");

        new gp_ops.TestRunner().run();
        new ip_ops.TestRunner().run();

        console.print("<<<< Finished JIT tests <<<<");
    }
}
