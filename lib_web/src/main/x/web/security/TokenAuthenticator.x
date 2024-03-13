import ecstasy.collections.CaseInsensitive;

import codecs.Base64Format;
import codecs.Utf8Codec;

import responses.SimpleResponse;


/**
 * An implementation of the Authenticator interface that is used for a token based authentication,
 * which can also be utilized by the second phase of the [Open Authorization (OAuth 2.0)]
 *   (https://datatracker.ietf.org/doc/html/rfc6819.html).
 */
@Concurrent
service TokenAuthenticator
        implements Authenticator {

    /**
     * Construct the `TokenAuthenticator` for the specified [Realm].
     */
    construct(Realm realm) {
        this.realm   = realm;
    }

    /**
     * The Realm that contains the user/token information.
     */
    public/private Realm realm;


    // ----- Authenticator interface ---------------------------------------------------------------

    @Override
    AuthStatus|ResponseOut authenticate(RequestIn request, Session session) {
        // TLS is a pre-requisite for authentication
        assert request.scheme.tls;

        // first, check to see if the incoming request includes the necessary authentication
        // information, which will be in one or more "Authorization" header entries
        for (String auth : request.header.valuesOf("Authorization")) {
            auth = auth.trim();
            // "Authorization" header format: Bearer Base64([{user}:]{token})
            if (CaseInsensitive.stringStartsWith(auth, "Bearer ")) {
                try {
                    auth = Utf8Codec.decode(Base64Format.Instance.decode(auth.substring(7)));
                } catch (Exception e) {
                    return new SimpleResponse(BadRequest);
                }

                String user;
                String token;
                if (Int tokenOffset := auth.indexOf(':')) {
                    // the token serves as a password
                    user  = auth[0 ..< tokenOffset];
                    token = auth.substring(tokenOffset + 1);
                } else {
                    // TODO CP: there is no user name, only a token; how to ask the Realm?
                    user  = "";
                    token = auth;
                }

                if (Set<String> roles := realm.authenticate(user, token)) {
                    session.authenticate(user, roles=roles);
                    return Allowed;
                } else {
                    return Forbidden;
                }
            }
        }
        return Unknown;
    }
}