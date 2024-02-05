/**
 * An implementation of the [Authenticator] interface that attempts to authorized the incoming
 * request using any of the specified `Authenticators`.
 */
const ChainAuthenticator(List<Authenticator> chain)
        implements Authenticator {

    @Override
    AuthStatus|ResponseOut authenticate(RequestIn request, Session session) {
        ResponseOut? response = Null;
        for (Authenticator authenticator : chain) {
            AuthStatus|ResponseOut success = authenticator.authenticate(request, session);

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
}
