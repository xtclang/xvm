import ecstasy.collections.CaseInsensitive;

import Realm.Hash;

import responses.SimpleResponse;


/**
 * An implementation of the Authenticator interface that is used for a token based authentication,
 * which can also be utilized by the second phase of the [Open Authorization (OAuth 2.0)]
 *   (https://datatracker.ietf.org/doc/html/rfc6819.html).
 */
@Concurrent
service TokenAuthenticator(Realm realm, String apiName)
        implements Authenticator {

    /**
     * The Realm that contains the user/token information.
     */
    public/private Realm realm;


    // ----- Authenticator interface ---------------------------------------------------------------

    @Override
    Boolean|ResponseOut authenticate(RequestIn request, Session session) {
        // TLS is a pre-requisite for authentication
        assert request.scheme.tls;

        // first, check to see if the incoming request includes the necessary authentication
        // information, which will be in one or more "Authorization" header entries
        NextAuthAttempt: for (String auth : request.header.valuesOf("Authorization")) {
            auth = auth.trim();
            // "Authorization" header format: {apiName} {user}:{token}
            Int userOffset = apiName.size;
            if (CaseInsensitive.stringStartsWith(auth, apiName),
                    auth[userOffset] == ' ',
                Int tokenOffset := auth.indexOf(':', userOffset)) {

                String user  = auth[userOffset >..< tokenOffset];
                String token = auth.substring(tokenOffset + 1);

                // the token serves as a password
                if (Set<String> roles := realm.authenticate(user, token)) {
                    session.authenticate(user, roles=roles);
                    return True;
                }
            } else {
                return new SimpleResponse(BadRequest);
            }

        }
        return new SimpleResponse(Unauthorized);
    }
}