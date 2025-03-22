import ecstasy.collections.CaseInsensitive;

import sec.KeyCredential;


/**
 * An implementation of the Authenticator interface that is used for a token based authentication,
 * which can also be utilized by the second phase of the [Open Authorization (OAuth 2.0)]
 *   (https://datatracker.ietf.org/doc/html/rfc6819.html).
 *
 * @see https://datatracker.ietf.org/doc/html/rfc6750.html
 */
@Concurrent
service TokenAuthenticator
        implements Authenticator {

    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct the `TokenAuthenticator` for the specified [Realm].
     */
    construct(Realm realm) {
        this.realm = realm;
    }

    @Override
    construct(TokenAuthenticator that) {
        this.realm = that.realm;
    }

    // ----- properties ----------------------------------------------------------------------------

    /**
     * The Realm that contains the token information.
     */
    @Override
    public/protected Realm realm;

    // ----- Authenticator interface ---------------------------------------------------------------

    @Override
    Attempt[] findAndRevokeSecrets(RequestIn request) {
        Attempt[] attempts = scan(request);
        if (attempts.empty) {
            return attempts;
        }

        Attempt[] secrets = [];
        for (Attempt attempt : attempts) {
            if (attempt.status >= NotActive,
                    Entitlement entitlement := attempt.claim.is(Entitlement),
                    Credential  credential  ?= attempt.credential,
                                entitlement := entitlement.revokeCredential(credential)) {
                realm.updateEntitlement(entitlement);
                secrets += attempt;
            }
        }
        return secrets;
    }

    @Override
    Attempt[] authenticate(RequestIn request) {
        // TLS is a pre-requisite for authentication
        assert request.scheme.tls;

        Attempt[] attempts = scan(request);
        return attempts.empty ? [new Attempt(Null, NoData)] : attempts;
    }

    /**
     * Scan the incoming request for any relevant HTTP Authentication Scheme information, which will
     * be in one or more `Authorization` header entries.
     *
     * @param request  the HTTP request
     *
     * @return an array of zero or more [Attempt] records, corresponding to the information found in
     *         the `Authorization` headers
     */
    Attempt[] scan(RequestIn request) {
        Attempt[] attempts = [];

        // check to see if the incoming request includes the necessary authentication information,
        // which will be in one or more "Authorization" header entries
        // (see https://datatracker.ietf.org/doc/html/rfc6750#section-6.1.1)
        for (String auth : request.header.valuesOf(Header.Authorization)) {
            auth = auth.trim();

            // "Authorization" header format: Bearer {b64-token}
            String token;
            if (CaseInsensitive.stringStartsWith(auth, "Bearer ")) {
                try {
                    token = auth.substring(7);
                } catch (Exception e) {
                    attempts += new Attempt(Null, Failed);
                    continue;
                }

                KeyCredential credential = new KeyCredential(realm.name, token);

                if (Entitlement entitlement := realm.findEntitlement(credential)) {
                    Authenticator.Status status = entitlement.calcStatus(realm) == Active ? Success : NotActive;
                    attempts += new Attempt(entitlement, status, credential=credential);
                } else {
                    attempts += new Attempt(token, Failed);
                }
            }
        }
        return attempts;
    }
}