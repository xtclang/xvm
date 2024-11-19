/**
 * Command Line Interface support for Web applications.
 *
 * To use the WebCLI library, the application code needs to do the following:
 *     - annotate the module as a `WebCLI`, for example:
 *          @TerminalApp("My REST client")
 *          module MyCurl {
 *              package webcli import webcli.xtclang.org;
 *
 *              import webcli.*;
 *              ...
 *          }
 *
 *     - annotate any methods to be executed as a command with the `Command` annotation, for example:
 *
 *          @Command("title", "Get the app title")
 *          String org() = Gateway.sendRequest(GET, "main/title");
 *
 */
module webcli.xtclang.org {

    package cli  import cli.xtclang.org;
    package json import json.xtclang.org;
    package web  import web.xtclang.org;

    typedef cli.Command as Command;
    typedef cli.Desc    as Desc;

    @Inject Console console;

    mixin TerminalApp(String description   = "",
                      String commandPrompt = "> ",
                      String messagePrefix = "# ",
                )
            extends cli.TerminalApp(description, commandPrompt, messagePrefix) {

        import web.MediaType;

        /**
         * The entry point.
         */
        @Override
        void run(String[] args) = Gateway.run(this, args, Password);

        /**
         * Send a GET request.
         */
        String get(String path) = Gateway.sendRequest(GET, path);

        /**
         * Send a PUT request.
         */
        String put(String path, Object content, MediaType? mediaType = Null) =
                Gateway.sendRequest(PUT, path, content, mediaType);

        /**
         * Send a POST request.
         */
        String post(String path, Object content, MediaType? mediaType = Null) =
                Gateway.sendRequest(GET, path, content, mediaType);

        /**
         * Send a DELETE request.
         */
        String delete(String path) = Gateway.sendRequest(DELETE, path);

    }

    static service Gateway {
        import cli.Runner;

        import json.Doc;
        import json.Parser;
        import json.Printer;

        import web.Body;
        import web.Client;
        import web.Client.PasswordCallback;
        import web.HttpClient;
        import web.HttpStatus;
        import web.HttpMethod;
        import web.MediaType;
        import web.ResponseIn;
        import web.RequestOut;
        import web.Uri;


        private @Unassigned Client client;
        private @Unassigned Uri    uri;
        private PasswordCallback?  callback;

        enum Credentials {None, Password, Token}
        void initCallback(Credentials cred = None) {
            switch (cred) {
            case Password:
                callback = realm -> {
                    console.print($"Realm: {realm}");
                    String name     = console.readLine("User name: ");
                    String password = console.readLine("Password: ", suppressEcho = True);
                    return name, password;
                };
                break;

            case Token:
                TODO("Not yet implemented");
            }
        }

        /**
         * The entry point.
         */
        void run(TerminalApp app, String[] args = [], Credentials cred = None) {
            app.print(Runner.description);
            initCallback(cred);
            Gateway.resetClient(args.empty ? "" : args[0]);
            Runner.run(app, suppressHeader = True);
        }

        void resetClient(String defaultUri = "", Boolean forceTls = False) {
            String uriString = console.readLine($"Enter the server uri [{defaultUri}]:");
            if (uriString.empty) {
                uriString = defaultUri;
            }

            Uri uri = new Uri(uriString);
            if (String scheme ?= uri.scheme) {
                assert !forceTls || scheme.toLowercase() == "https"
                    as "This tool can only operate over SSL";
            } else {
                uri = new Uri($"{forceTls ? "https" : "http"}://{uriString}");
            }

            console.print($"Connecting to \"{uri}\"");

            this.client = new HttpClient();
            this.uri    = uri;
        }

        String send(RequestOut request) {
            ResponseIn response = client.send(request, callback);
            HttpStatus status   = response.status;
            if (status == OK) {
                assert Body body ?= response.body;
                Byte[] bytes = body.bytes;
                if (bytes.size == 0) {
                    return "<Empty response>";
                }

                switch (body.mediaType) {
                case Text:
                case Html:
                    return bytes.unpackUtf8();
                case Json:
                    String jsonString = bytes.unpackUtf8();
                    Doc    doc        = new Parser(jsonString.toReader()).parseDoc();
                    return Printer.PRETTY.render(doc);
                default:
                    return $"<Unsupported media type: {body.mediaType}>";
                }
            } else {
                return response.toString();
            }
        }

        String sendRequest(HttpMethod method, String path, Object? content = Null,
                           MediaType? mediaType = Null) {
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            return send(client.createRequest(method, uri.with(path=path), content, mediaType));
        }
    }
}