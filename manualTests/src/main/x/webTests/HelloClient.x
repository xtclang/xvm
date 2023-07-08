/**
 * The command example:
 *
 *    xec build/HelloClient.xtc http://localhost:8080
 */
module HelloClient {
    package msg import Messages;
    package web import web.xtclang.org;

    @Inject Console console;

    import msg.Greeting;
    import web.HttpClient;

    void run(String[] args=["http://localhost:8080"]) {
        HttpClient client = new HttpClient();

        String uri = args[0];

        assert Greeting greeting := client.get(uri + "/hello").to(Greeting);
        console.print(greeting);

        assert String secure := client.get(uri + "/s").to(String);
        console.print(secure);

        for (Int i : 1 .. 4) {
            assert Int count := client.get(uri + "/c").to(Int);
            console.print(count);
        }

        console.print(client.get(uri + "/l"));
    }
}