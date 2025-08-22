/**
 * Test for the return values from the xec (etc.) command.
 *
 * To test on non-Windows, create a shell script "xrun.sh" in this directory:
 *
 *     xec TestRun.x $1
 *     exit_status=$?
 *     echo "The return value of 'xec' was: $exit_status"
 *
 * Save it, then:
 *
 *     chmod +x xrun.sh
 *
 * Alternatively on Windows, create xrun.cmd in this directory:
 *
 *     xec TestSimple.x %1
 *     echo "The return value of 'xec' was: %ERRORLEVEL%"
 *
 * To run it, from this directory:
 *
 *     ./xrun.sh 0                  -> The return value of 'xec' was: 0
 *     ./xrun.sh 1                  -> The return value of 'xec' was: 1
 *     ./xrun.sh 17                 -> The return value of 'xec' was: 17
 *     ./xrun.sh hello              -> The return value of 'xec' was: 1 (because of an exception)
 *
 * On Windows, substitute `.\xrun` or `.\xrun.cmd` for `./xrun.sh` in the commands above.
 */
module TestRun {
    @Inject Console console;

    Int run(String[] args) {
        console.print($"{args=}");
        return args.empty ? 0 : new Int(args[0]);
    }
}
