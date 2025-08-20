/**
 * To test, create a shell script:
 *     xec -L build/xtc/main/lib -o build/xtc/main/lib src/main/x/TestSimple.x
 *     exit_status=$?
 *     echo "The return value of 'xec' was: $exit_status"
 */
module TestSimple {
    @Inject Console console;

    Int run() {
        Int i = 17;
        return i;
    }
}
