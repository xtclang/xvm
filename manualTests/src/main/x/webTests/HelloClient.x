/**
 * The command example:
 *
 *    xec build/HelloClient.xtc http://localhost:8080
 */
module HelloClient
    {
    package web import web.xtclang.org;

    @Inject Console console;

    void run(String[] args=["http://localhost:8080"])
        {
        @Inject web.Client client;

        String uri = args[0];

        assert String greeting := client.get(uri).to(String);
        console.print(greeting);

        assert String secure := client.get(uri + "/s").to(String);
        console.print(secure);

        for (Int i : 1 .. 4)
            {
            assert Int count := client.get(uri + "/c").to(Int);
            console.print(count);
            }

        console.print(client.get(uri + "/l"));
        }
    }