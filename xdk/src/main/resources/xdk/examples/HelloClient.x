/**
 * This is an extremely basic web client example.
 *
 * To compile this example, execute the following command line from within the directory containing
 * this file:
 *
 *     xtc HelloClient
 *
 * Before running this example, follow the instructions in `HelloServer` to compile and run that
 * example. Then, while `HelloServer` is running, run this example from the command line:
 *
 *     xec HelloClient
 */
module HelloClient
    {
    package web import web.xtclang.org;

    @Inject Console console;

    void run(String[] args=["http://localhost:8080"])
        {
        @Inject web.Client client;

        String uri = args[0];
        console.print($"Accessing: {uri.quoted()}");

        assert String result := client.get(uri).to(String) as $"Request to {uri.quoted()} failed";
        console.print($"The server returned: {result}");
        }
    }