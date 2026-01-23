/**
 * Docker functionality test program that echoes arguments like EchoTest.
 * This is specifically designed to test Docker container functionality.
 */
module DockerTest {
    import ecstasy.io.Console;

    void run(String[] args = []) {
        @Inject Console console;
        
        console.print($"DockerTest invoked with {args.size} arguments");
        
        if (args.size == 0) {
            console.print("DockerTest invoked with 0 arguments.");
        } else {
            console.print($"DockerTest invoked with {args.size} arguments:");
            for (Int i : 0..<args.size) {
                console.print($"[{i+1}]=\"{args[i]}\"");
            }
        }
    }
}
