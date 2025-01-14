import ecstasy.collections.CaseInsensitive;

import convert.codecs.Utf8Codec;
import convert.formats.Base64Format;

import sec.Entitlement;
import sec.KeyCredential;


/**
 * An implementation of the Authenticator interface that is used for a token based authentication,
 * which can also be utilized by the second phase of the [Open Authorization (OAuth 2.0)]
 *   (https://datatracker.ietf.org/doc/html/rfc6819.html).
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
     * The Realm that contains the user/token information.
     */
    @Override
    public/protected Realm realm;

    // ----- Authenticator interface ---------------------------------------------------------------

    @Override
    Attempt[] findAndRevokeSecrets(RequestIn request) {
        KeyAttempt[] attempts = scan(request);
        if (attempts.empty) {
            return attempts;
        }

        KeyAttempt[] secrets = [];
        for (KeyAttempt attempt : attempts) {
            if (attempt.status >= NotActive,
                    Entitlement entitlement ?= attempt.entitlement,
                    String      locator     ?= attempt.locator,
                                entitlement := entitlement.revokeCredential(KeyCredential.Scheme, locator)) {
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

        KeyAttempt[] attempts = scan(request);
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
    KeyAttempt[] scan(RequestIn request) {
        KeyAttempt[] attempts = [];

        // check to see if the incoming request includes the necessary authentication information,
        // which will be in one or more "Authorization" header entries
        for (String auth : request.header.valuesOf("Authorization")) {
            auth = auth.trim();
            // "Authorization" header format: Bearer Base64([{user}:]{token})
            if (CaseInsensitive.stringStartsWith(auth, "Bearer ")) {
                try {
                    auth = Utf8Codec.decode(Base64Format.Instance.decode(auth.substring(7)));
                } catch (Exception e) {
                    attempts += new KeyAttempt(Null, Failed);
                    continue;
                }

                String user;
                String token;
                if (Int tokenOffset := auth.indexOf(':')) {
                    // the token serves as a password
                    user  = auth[0 ..< tokenOffset];
                    token = auth.substring(tokenOffset + 1);
                } else {
                    user  = "";
                    token = auth;
                }

                // REVIEW CP: how to use the user name if present?
                String locator = KeyCredential.createLocator(realm.name, token);

                if (Entitlement entitlement := realm.findEntitlement(KeyCredential.Scheme, locator)) {
                    Authenticator.Status status = entitlement.calcStatus(realm) == Active ? Success : NotActive;
                    attempts += new KeyAttempt(entitlement, status, locator);
                } else {
                    attempts += new KeyAttempt(locator, Failed);
                }
            }
        }
        return attempts;
    }

    // ----- internal ------------------------------------------------------------------------------

    static const KeyAttempt(Claim? subject, Status status, String? locator = Null)
            extends Attempt(subject, status, Null) {
        @RO Entitlement? entitlement.get() = subject.is(Entitlement) ?: Null;
    }
}