/**
 * The command example:
 *
 *    xec build/HelloClient.xtc http://localhost
 */
@TerminalApp("Hello CLI", "hi> ", timeout=Duration:3s)
module HelloClient {
    package webcli import webcli.xtclang.org;

    import webcli.*;

    @Command("h", "Say hello")
    String hello() = get("/hello");

    @Command("l", "Log in")
    String login() = get("/l");

    @Command("o", "Log out")
    String logout() = get("/d");

    @Command("s", "Secure access")
    String secure() = get("/s");

    @Command("c", "Increment count")
    String count() = get("/c");

    @Command("e", "Echo")
    String echo(@Desc("Value of `debug` to start debugger") String path = "") = get($"/e/{path}");

    @Command("upload", "Upload a file")
    String upload(String path) {

        @Inject Directory curDir;
        @Inject Directory rootDir;

        if (path.startsWith("/")) {
            if (File file := rootDir.findFile(path)) {
                return upload("upload", file);
            }
        } else if (File file := curDir.findFile(path)) {
            return upload("upload", file);
        }
        return $"<Unknown file: {path.quoted()}>";
    }

    service Users {
        @Command("u", "Show user info")
        String user() = HelloClient.get("/user");
    }
}