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
    conditional HttpStatus|ResponseOut checkNonTlsRequest(RequestIn request) {
        (HttpStatus|ResponseOut)? result = Null;
        for (Authenticator auth : chain) {
            if (HttpStatus|ResponseOut oneResult := auth.checkNonTlsRequest(request)) {
                if (result == Null) {
                    result = oneResult;
                } else if (oneResult.is(ResponseOut) || result.is(ResponseOut)) {
                    // one or both are an HTTP response
                    if (!result.is(ResponseOut)) {
                        // take the one HTTP response
                        result = oneResult;
                    } else if (oneResult.is(ResponseOut)) {
                        // TODO merge? what should be done here?
                    }
                } else if (oneResult.code > result.code) {
                    // two result codes; use the higher one (errors are in the 4xx and 5xx range)
                    result = oneResult;
                }
            }
        }
        return result == Null
                ? False
                : (True, result);
    }
}
