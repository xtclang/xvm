import convert.codecs.Utf8Codec;
import convert.formats.Base64Format;

import responses.SimpleResponse;

import ecstasy.collections.CaseInsensitive;

import sec.PlainTextCredential;


/**
 * An implementation of the Authenticator interface for
 * [The 'Basic' HTTP Authentication Scheme](https://datatracker.ietf.org/doc/html/rfc7617).
 */
@Concurrent
service BasicAuthenticator(Realm realm)
        implements Duplicable, Authenticator {

    // ----- constructors --------------------------------------------------------------------------

    @Override
    construct(BasicAuthenticator that) {
        Realm realm = that.realm;
        if (realm.is(Duplicable)) {
            realm = realm.duplicate();
        }
        this.realm = realm;
    }

    // ----- properties ----------------------------------------------------------------------------

    /**
     * The Realm that contains the user/password information.
     */
    @Override
    public/protected Realm realm;


    // ----- Authenticator API ---------------------------------------------------------------------

    @Override
    Attempt[] findAndRevokeSecrets(RequestIn request) {
        Attempt[] attempts = scan(request);
        if (attempts.empty) {
            return attempts;
        }

        Attempt[] secrets = [];
        for (Attempt attempt : attempts) {
            if (attempt.status >= NotActive,
                    Principal  principal  := attempt.claim.is(Principal),
                    Credential credential ?= attempt.credential,
                               principal := principal.revokeCredential(credential)) {
                realm.updatePrincipal(principal);
                secrets += attempt;
            }
        }
        return secrets;
    }

    @Override
    Attempt[] authenticate(RequestIn request) {
        // to cause the client to request the user for a name and password, we need to return an
        // "Unauthorized" error code with a header that directs the client to use basic auth
        private Attempt[] RequestAuth = [new Attempt(Null, NoData,
                $|Basic realm="{realm.name}", charset="UTF-8"
                )];

        Attempt[] attempts = scan(request);
        return attempts.empty ? RequestAuth : attempts;
    }

    /**
     * Scan the incoming request for any Basic HTTP Authentication Scheme information, which will be
     * in one or more `Authorization` header entries.
     *
     * @param request  the HTTP request
     *
     * @return an array of zero or more [Attempt] records, corresponding to the information found in
     *         the `Authorization` headers
     */
    Attempt[] scan(RequestIn request) {
        static Attempt Corrupt = new Attempt(Null, Failed);

        Attempt[] attempts = [];
        NextHeader: for (String auth : request.header.valuesOf("Authorization")) {
            auth = auth.trim();
            if (CaseInsensitive.stringStartsWith(auth, "Basic ")) {
                try {
                    auth = Utf8Codec.decode(Base64Format.Instance.decode(auth.substring(6)));
                } catch (Exception e) {
                    attempts += Corrupt;
                    continue;
                }

                if (Int colon := auth.indexOf(':')) {
                    String name = auth[0 ..< colon];
                    String pwd  = auth[colon >..< auth.size];

                    if (Principal principal := realm.findPrincipal(PlainTextCredential.Scheme, name)) {
                        PlainTextCredential? failure = Null;
                        for (Credential credential : principal.credentials) {
                            // for plain text credentials that match the name, there are three
                            // outcomes: (1) revoked, (2) wrong password, (3) all good!
                            if (credential.scheme == PlainTextCredential.Scheme
                                    && credential.is(PlainTextCredential)
                                    && credential.active
                                    && credential.name == name) {
                                if (credential.password == pwd) {
                                    attempts += new Attempt(principal,
                                        principal.calcStatus(realm) == Active ? Success : NotActive,
                                        Null, credential);
                                    continue NextHeader;
                                } else {
                                    failure ?:= credential;
                                }
                            }
                        }
                        attempts += new Attempt(principal, Failed, Null, failure);
                    } else {
                        attempts += new Attempt(name, Failed);
                    }
                } else {
                    attempts += Corrupt;
                }
            }
        }

        return attempts;
    }
}