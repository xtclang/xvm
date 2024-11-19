/**
 * The command example:
 *
 *    xec build/HelloClient.xtc http://localhost
 */
@TerminalApp("Hello CLI", "hi> ")
module HelloClient {
    package webcli import webcli.xtclang.org;

    import webcli.*;

    @Command("h", "Say hello")
    String hello() = get("/hello");

    @Command("l", "Log in")
    String login() = get("/l");

    @Command("s", "Secure access")
    String secure() = get("/s");

    @Command("c", "Increment count")
    String count() = get("/c");

    @Command("e", "Echo")
    String echo(@Desc("Value of `debug` to start debugger") String path = "") = get($"/e/{path}");

    service Users {
        @Command("u", "Show user info")
        String user() = HelloClient.get("/user");
    }
}