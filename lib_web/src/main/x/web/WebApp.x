import ecstasy.reflect.AnnotationTemplate;
import ecstasy.reflect.Argument;
import ecstasy.reflect.ClassTemplate.Composition;

import codecs.Registry;

import security.Authenticator;
import security.NeverAuthenticator;


/**
 * The `@WebApp` annotation is used to mark a module as being a web-application module. It can
 * contain any number of discoverable HTTP endpoints.
 *
 * TODO how to import a web module explicitly as "it's ok to trust any web services in this module"
 *      - can the package be annotated as "@Trusted" or something like that?
 */
mixin WebApp
        into Module
    {
    /**
     * The registry for this WebApp.
     */
    @Lazy Registry registry_.calc()
        {
        return new Registry();
        }

    /**
     * Handle an otherwise-unhandled exception or other error that occurred during [Request]
     * processing within this `WebApp`, and produce a [Response] that is appropriate to the
     * exception or other error that was raised.
     *
     * @param session   the session (usually non-`Null`) within which the request is being
     *                  processed; the session can be `Null` if the error occurred before or during
     *                  the instantiation of the session
     * @param request   the request being processed
     * @param error     the exception thrown, the error description, or an HttpStatus code
     *
     * @return the [Response] to send back to the caller
     */
    ResponseOut handleUnhandledError(Session? session, RequestIn request, Exception|String|HttpStatus error)
        {
        // TODO CP: does the exception need to be logged?
        HttpStatus status = error.is(RequestAborted) ? error.status :
                            error.is(HttpStatus)     ? error
                                                     : InternalServerError;

        return new responses.SimpleResponse(status=status, bytes=error.toString().utf8());
        }

    /**
     * A Webapp that knows how to provide an `Authenticator` should implement this interface in
     * order to do so.
     */
    static interface AuthenticatorFactory
        {
        /**
         * Create (or otherwise provide) the `Authenticator` that this WebApp will use. It is
         * expected that this method will only be called once.
         *
         * @return the `Authenticator` for this WebApp
         */
        Authenticator createAuthenticator();
        }

    /**
     * The [Authenticator] for the web application.
     */
    @Lazy Authenticator authenticator.calc()
        {
        // use the Authenticator provided by injection, which allows a deployer to select a specific
        // form of authentication; if one is injected, use it, otherwise, use the one specified by
        // this application
        @Inject Authenticator? providedAuthenticator;
        return providedAuthenticator?;

        // allow a module to implement the factory method createAuthenticator()
// TODO GG:
//        if (this.is(AuthenticatorFactory))
//            {
//            return this.createAuthenticator();
//            }
        if (AuthenticatorFactory af := this:private.is(AuthenticatorFactory))
            {
            return af.createAuthenticator();
            }

        // disable authentication, since no authenticator was found
        return new NeverAuthenticator();
        }
    }