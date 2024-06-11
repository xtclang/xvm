/**
 * An implementation of the [Authenticator] interface that attempts to authorized the incoming
 * request using any of the specified `Authenticators`.
 */
const ChainAuthenticator(List<Authenticator> chain)
        implements Authenticator {

    @Override
    Boolean requiresSession(RequestIn request) = chain.any(a -> a.requiresSession(request));

    @Override
    AuthStatus|ResponseOut authenticate(Session? session, RequestIn request) {
        ResponseOut? response = Null;
        for (Authenticator authenticator : chain) {
            AuthStatus|ResponseOut success = authenticator.authenticate(session, request);

            switch (success) {
            case Allowed, Forbidden:
                return success;

            case Unknown:
                // try the next one
                break;

            default:
                // remember the response (ignore multiple ones), but try the next one still
                response ?:= success.as(ResponseOut);
                break;
            }
        }
        return response ?: Unknown;
    }

    @Override
    conditional ResponseOut? containsSecrets(RequestIn request) {
        Boolean      leaked   = False;
        ResponseOut? response = Null;
        for (Authenticator auth : chain) {
            if (ResponseOut? curResponse := auth.containsSecrets(request)) {
                leaked = True;
                response ?:= curResponse;   // unfortunately, at most one response can be sent
            }
        }
        return leaked, response;
    }
}
