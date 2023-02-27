/**
 * An implementation of the Authenticator interface for
 * [The 'Basic' HTTP Authentication Scheme](https://datatracker.ietf.org/doc/html/rfc7617).
 */
service NeverAuthenticator
        implements Authenticator
    {
    @Override
    Boolean|ResponseOut authenticate(RequestIn request, Session session, Endpoint endpoint)
        {
        private Boolean logged = False;
        if (!logged)
            {
            // log a message the first time this Authenticator has to reject a user, so the
            // developer (or deployer) can see that an Authenticator needs to be provided
            // TODO define a Log API and make an injectable Log
            @Inject Console console;
            console.print(\|Authentication was requested, but the WebApp did not provide an\
                           | Authenticator
                         );
            logged = True;
            }

        return False;
        }
    }
