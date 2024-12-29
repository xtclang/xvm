import sec.Credential;
import sec.Entitlement;
import sec.Group;
import sec.Principal;

import web.*;
import web.responses.SimpleResponse;
import web.security.Authenticator;
import web.security.DigestCredential;

import DigestCredential.Hash;
import DigestCredential.sha512_256;

/**
 * The `AuthEndPoint` is a [WebService] wrapper around an [Authenticator] that uses a [DBRealm].
 *
 * It provides out-the-box REST API to manage the underlying [auth database](AuthSchema).
 */
@WebService("/.well-known/auth")
@LoginRequired
service AuthEndpoint(WebApp app, Authenticator authenticator, DBRealm realm)
        implements Authenticator
        delegates Authenticator - Duplicable(authenticator) {

    @Override
    construct(AuthEndpoint that) {
        this.app           = that.app;
        this.authenticator = that.authenticator;
        this.realm         = that.realm;
    }

    AuthSchema db.get() = realm.db;

    /*
     * Change the password for the current user.
     *
     * The client must append "Base64(oldPassword):Base64(newPassword)" as a message body.
     */
    @Post("/pwd")
    SimpleResponse changePassword(Session session, @BodyParam String passwords) {
        import convert.formats.Base64Format;

        assert Principal principal ?= session.principal;

        assert Int delim := passwords.indexOf(':');

        String b64Old = passwords[0 ..< delim];
        String b64New = passwords.substring(delim+1);

        String passwordOld = Base64Format.Instance.decode(b64Old).unpackUtf8();
        String passwordNew = Base64Format.Instance.decode(b64New).unpackUtf8();

        Hash hashOld = DigestCredential.passwordHash(principal.name, realm.name, passwordOld, sha512_256);
        Hash hashNew = DigestCredential.passwordHash(principal.name, realm.name, passwordNew, sha512_256);

        Credential[] credentials = principal.credentials;
        FindOld: for (Credential credential : credentials) {
            if (credential.is(DigestCredential) &&
                    credential.matches(principal.name, hashOld)) {
                credentials = credentials.reify(Mutable);
                credentials[FindOld.count] = credential.with(password_sha512_256=hashNew);
                credentials = credentials.toArray(Constant, inPlace=True);

                principal = realm.updatePrincipal(principal.with(credentials=credentials));
                session.authenticate(principal);
                return new SimpleResponse(OK);
            }
        }
        return new SimpleResponse(Conflict, "Invalid old password");
    }

    /**
     * Retrieve the current user.
     */
    @Get("/user")
    Principal getUser(Session session) = session.principal?.with(credentials=[]): assert;

    /**
     * Add user
     */
    @Post("/user/{name}")
    Principal addUser(String name, @BodyParam String password) {
        TODO
    }
}
