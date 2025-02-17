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
 *          String org() = MyCurl.get("main/title");
 *
 */
module webcli.xtclang.org {

    package cli  import cli.xtclang.org;
    package conv import convert.xtclang.org;
    package json import json.xtclang.org;
    package web  import web.xtclang.org;

    typedef cli.Command as Command;
    typedef cli.Desc    as Desc;

    @Inject Console console;

    /**
     * Authentication method:
     *  - None    :  authentication is not used
     *  - Callback:  if a server responds with an authentication challenge, the user will be
     *               prompted to supply the necessary credentials
     *  - Password:  during CLI tool initialization, the user will be prompted to supply credentials
     *               that will be used to initialize a callback
     *  - Token:     during CLI tool initialization, the user will be prompted to supply the token
     *               that will be passed to every request as an API key
     */
    enum AuthMethod {None, Callback, Password, Token}

    /**
     * The extension of cli.TerminalApp mixin.
     *
     * @param auth     the authentication method; [Callback] by default
     * @param timeout  the default http request timeout; one minute by default
     */
    mixin TerminalApp(String     description   = "",
                      String     commandPrompt = "> ",
                      String     messagePrefix = "# ",
                      AuthMethod auth          = Callback,
                      Duration   timeout       = Duration:1m,
                )
            extends cli.TerminalApp(description, commandPrompt, messagePrefix) {

        import web.HttpStatus;
        import web.MediaType;

        /**
         * The entry point.
         */
        @Override
        void run(String[] args) = Gateway.run(this, args, auth=auth, timeout=timeout);

        /**
         * Send a GET request.
         */
        (String, HttpStatus) get(String path) = Gateway.sendRequest(GET, path);

        /**
         * Send a PUT request.
         */
        (String, HttpStatus) put(String path, Object? content = Null, MediaType? mediaType = Null) =
                Gateway.sendRequest(PUT, path, content, mediaType);

        /**
         * Send a POST request.
         */
        (String, HttpStatus) post(String path, Object? content = Null, MediaType? mediaType = Null) =
                Gateway.sendRequest(POST, path, content, mediaType);

        /**
         * Send a PATCH request.
         */
        (String, HttpStatus) patch(String path, Object? content = Null, MediaType? mediaType = Null) =
                Gateway.sendRequest(PATCH, path, content, mediaType);

        /**
         * Send a DELETE request.
         */
        (String, HttpStatus) delete(String path, Object? content = Null, MediaType? mediaType = Null) =
                Gateway.sendRequest(DELETE, path, content, mediaType);

        /**
         * Upload a file via POST request.
         */
        (String, HttpStatus) upload(String path, File file, MediaType? mediaType = Null) =
                Gateway.upload(path, file, mediaType);
    }

    static service Gateway {
        import cli.Runner;

        import json.Doc;
        import json.Parser;
        import json.Printer;

        import web.Body;
        import web.Client;
        import web.Client.PasswordCallback;
        import web.Header;
        import web.HttpClient;
        import web.HttpStatus;
        import web.HttpMethod;
        import web.MediaType;
        import web.ResponseIn;
        import web.RequestOut;
        import web.Uri;

        private @Unassigned Client client;
        private Uri?               server;
        private AuthMethod         auth = None;
        private PasswordCallback?  callback;
        private String?            name;
        private String?            password; // could be a token
        private function void()?   init;
        private Duration           requestTimeout = Duration:1m;

        /**
         * Initialize authentication credentials.
         *
         * @param authString  a colon-delimited (:) "username:password" value; could be empty
         */
        void initAuth(String authString) {
            if (!authString.empty) {

                switch (auth) {
                case Callback:
                case Password:
                    assert Int delim := authString.indexOf(':') as "Password is missing";
                    String newName     = authString[0 ..< delim];
                    String newPassword = authString.substring(delim+1);

                    // set up the callback and save the credentials for use in "reset()"
                    this.callback = realm -> (newName, newPassword);
                    this.name     = newName;
                    this.password = newPassword;
                    break;

                case Token:
                    this.password = authString;
                    break;
                }
                return;
            }

            switch (auth) {
            case Callback:
                this.name     = Null;
                this.password = Null;
                this.callback = realm -> getPassword(realm, False);
                break;

            case Password:
                (String newName, String newPassword) = getPassword(Null, True);
                this.callback = realm -> (newName, newPassword);
                break;

            case Token:
                String newToken;
                do {
                    newToken = console.readLine("Token: ");
                } while (newToken.empty);
                this.password = newToken;
                break;
            }
        }

        /**
         * Read the user name and password from the console.
         *
         * @param realm  (optional) a realm name
         * @param force  (optional) pass `False` to reuse previously collected credentials (if any);
         *                          `True` to force the user input
         */
        (String, String) getPassword(String? realm = Null, Boolean force = False) {
            if (!force, String name ?= this.name, String password ?= this.password ) {
                return name, password;
            }

            if (realm != Null) {
                console.print($"Realm: {realm}");
            }

            String newName;
            String newPassword;
            if (name == Null) {
                do {
                    newName = console.readLine("User name: ");
                } while (newName.empty);
                newPassword = console.readLine("Password: ", suppressEcho=True);
            } else {
                newName = console.readLine($"User name [{name}]: ");
                if (newName.empty) {
                    assert newName ?= name;
                }
                newPassword = console.readLine("Password:", suppressEcho=True);
                if (newPassword.empty) {
                    assert newPassword ?= password;
                }
            }
            this.name     = newName;
            this.password = newPassword;
            return newName, newPassword;
        }

        /**
         * The entry point.
         *
         * If the `args` argument array is not empty, the first element (at index zero) will be
         * used as a server URI.
         * If the `args` argument array size is more than one, the second element (at index one)
         * will be used as a "name:password" pair or a "token" string depending on the `auth` value.
         *
         * @param app       the app that contains classes with commands.
         * @param args      (optional) the arguments passed by the user via the command line
         * @param auth      (optional) the authentication method
         * @param forceTls  (optional) pass `True` to enforce https connection
         * @param init      (optional) function to call at the end of initialization
         * @param timeout   (optional) requests timeout duration
         */
        void run(TerminalApp app, String[] args = [], AuthMethod auth = None,
                 Boolean forceTls = False, function void()? init = Null,
                 Duration? timeout = Null) {
            app.print(Runner.description);

            this.auth            = auth;
            this.init            = init;
            this.requestTimeout ?= timeout;

            resetClient(uriString =args.size > 0 ? args[0] : "",
                        authString=args.size > 1 ? args[1] : "",
                        forceTls  = forceTls,
                       );
            // don't pass the "init" to the runner; it has already been called by "resetClient"
            Runner.run(app, suppressWelcome=True, extras=[this]);
        }

        @Command("reset", "Reset the server URI and credentials")
        void resetClient(@Desc("Server URI") String uriString = "",
                         @Desc("Specify 'true' to enforce 'https' connection") Boolean forceTls = False,
                         @Desc("Credentials") String authString = "") {
            if (uriString.empty) {
                String prevUri = server == Null ? "" : server.toString();
                String prompt  = server == Null ? "" : $" [{prevUri}]";
                do {
                    uriString = console.readLine($"Enter the server URI{prompt}: ");
                    if (uriString.empty) {
                        uriString = prevUri;
                    }
                } while (uriString.empty);
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
            this.server = uri;

            initAuth(authString);
            init?();
        }

        @Command("server", "Print the server URI")
        String serverUri() = server.toString();

        /**
         * Create and send a request.
         */
        (String, HttpStatus) sendRequest(HttpMethod method, String path, Object? content = Null,
                                         MediaType? mediaType = Null) {
            return send(createRequest(method, path, content, mediaType));
        }

        /**
         * Create and send a POST request for the specified file data.
         */
        (String, HttpStatus) upload(String path, File file, MediaType? mediaType = Null) {
            String name = file.name;
            if (mediaType == Null, Int extOffset := name.lastIndexOf('.')) {
                if (mediaType := MediaType.of(name.substring(extOffset+1))) {} else {
                    mediaType = Binary;
                }
            }

            RequestOut request = createRequest(POST, path, file.contents, mediaType);
            request.header.add(Header.ContentDisposition, $"attachment; filename={name}");
            return send(request);
        }

        /**
         * Create a [RequestOut] object.
         *
         * Note: if this method is called from "outside" of the service boundary, the returned
         * `RequestOut` gets automatically frozen and needs to be cloned (using a "with-er") if any
         * modification is required.
         */
        RequestOut createRequest(HttpMethod method, String path, Object? content = Null,
                           MediaType? mediaType = Null) {
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            Uri        uri     = server?.with(path=path) : assert;
            RequestOut request = client.createRequest(method, uri, content, mediaType);
            if (auth == Token) {
                import conv.formats.Base64Format;
                import conv.codecs.Utf8Codec;

                assert String token ?= this.password;
                request.header[Header.Authorization] =
                        $"Bearer {Base64Format.Instance.encode(Utf8Codec.encode(token))}";
            }
            return request;
        }

        /**
         * Send the specified request and turn the response into a human readable String.
         */
        (String, HttpStatus) send(RequestOut request) {
            import ecstasy.io.IOException;

            ResponseIn response;
            try (val _ = new Timeout(requestTimeout)) {
                response = client.send(request, callback);
            } catch (TimedOut e) {
                return $"Request timed out after {requestTimeout.seconds} sec", ConnectionTimedOut;
            } catch (IOException e) {
                return ($"Error: {e.message.empty ? &e.actualType : e.message}", ServiceUnavailable);
            }
            HttpStatus status = response.status;
            if (status == OK) {
                assert Body body ?= response.body;
                Byte[] bytes = body.bytes;
                if (bytes.size == 0) {
                    return "<Empty response>", OK;
                }

                switch (body.mediaType) {
                case Text:
                case Html:
                    return bytes.unpackUtf8(), OK;
                case Json:
                    String jsonString = bytes.unpackUtf8();
                    Doc    doc        = new Parser(jsonString.toReader()).parseDoc();
                    return Printer.PRETTY.render(doc), OK;
                default:
                    return $"<Unsupported media type: {body.mediaType}>", OK;
                }
            } else {
                return response.toString(), status;
            }
        }
    }
}