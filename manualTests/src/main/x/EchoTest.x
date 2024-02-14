/**
 * This test is invoked by the build. It can also be invoked manually using the following command:
 *
 *     ./gradlew :manualTests:runXtc
 */
module EchoTest {
    void run(String[] args = []) {
        @Inject Console console;

        console.print($"EchoTest invoked with {args.size} arguments{args.empty ? '.' : ':'}");
        loop: for (String arg : args) {
            console.print($"  [{loop.count}]={arg.quoted()}");
        }
    }
}
