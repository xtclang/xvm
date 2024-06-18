import convert.codecs.Utf8Codec;
import convert.formats.Base64Format;

import responses.SimpleResponse;

import ecstasy.collections.CaseInsensitive;


/**
 * An implementation of the Authenticator interface for
 * [The 'Basic' HTTP Authentication Scheme](https://datatracker.ietf.org/doc/html/rfc7617).
 */
@Concurrent
service BasicAuthenticator(Realm realm)
        implements Authenticator {
    public/private Realm realm;

    @Override
    AuthStatus|ResponseOut authenticate(RequestIn request, Session session) {
        // TLS is a pre-requisite for authentication
        assert request.scheme.tls;

        // first, check to see if the incoming request includes the necessary authentication
        // information, which will be in one or more "Authorization" header entries
        for (String auth : request.header.valuesOf("Authorization")) {
            auth = auth.trim();
            if (CaseInsensitive.stringStartsWith(auth, "Basic ")) {
                try {
                    auth = Utf8Codec.decode(Base64Format.Instance.decode(auth.substring(6)));
                } catch (Exception e) {
                    return new SimpleResponse(BadRequest);
                }

                if (Int colon := auth.indexOf(':')) {
                    String user = auth[0 ..< colon];
                    String pwd  = auth[colon >..< auth.size];
                    if (realm.authenticate(user, pwd)) {
                        session.authenticate(user);
                        return Allowed;
                    }
                    if (session.authenticationFailed(user)) {
                        return Forbidden;
                    }
                }
            }
        }

        // to cause the client to request the user for a name and password, we need to return an
        // "Unauthorized" error code with a header that directs the client to use basic auth
        ResponseOut response = new SimpleResponse(Unauthorized);
        response.header.put("WWW-Authenticate", $|Basic realm="{realm.name}", charset="UTF-8"
                           );
        return response;
    }
}