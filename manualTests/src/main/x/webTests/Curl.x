/**
 * The command example:
 *
 *    xec build/curl.xtc GET http://www.xqiz.it
 */
module Curl {
    package net import net.xtclang.org;
    package web import web.xtclang.org;

    import net.Uri;

    import web.*;

    void run(String[] args=["GET", "http://xqiz.it"]) {
        @Inject Console console;

        if (args.size < 2) {
            console.print("Command format: [httpMethod] [uri]");
            return;
        }

        String methodName = args[0];
        String uriString  = args[1];

        Client client = new HttpClient();

        HttpMethod method   = HttpMethod.of(methodName);
        Uri        uri      = new Uri(uriString);
        RequestOut request  = client.createRequest(method, uri);

        Client.PasswordCallback callback = realm ->
            {
            console.print($"Realm: {realm}");
            String name     = console.readLine("User name: ");
            String password = console.readLine("Password: ", suppressEcho = True);

            return name, password;
            };

        ResponseIn response = client.send(request, callback);

        // process the response
        console.print(response);
        console.print($"Headers:\n{response.header}");

        if (Body body ?= response.body) {
            console.print(body.bytes.unpackUtf8());
        }
    }
}