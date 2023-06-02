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

        String method = args[0];
        String uri    = args[1];

        @Inject web.Client client;

        ResponseIn response = switch (method.toUppercase()) {
            case "GET": client.get(uri);
            default   : TODO
        };

        // process the response
        console.print(response);
        console.print($"Headers:\n{response.header}");

        if (Body body ?= response.body) {
            console.print(body.bytes.unpackUtf8());
        }
    }
}