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
     * The [Authenticator] for the web application.
     */
    @Lazy Authenticator authenticator.get()
        {
        // use the Authenticator provided by injection, which allows a deployer to select a specific
        // form of authentication; if one is injected, use it, otherwise, use the one specified by
        // this application
        @Inject Authenticator? providedAuthenticator;
        return providedAuthenticator ?: createAuthenticator();
        }

    /**
     * Create the appropriate [Authenticator] for this web application.
     *
     * The default implementation of this method does not have any concept of user identity or
     * authentication, so it provides a [NeverAuthenticator] by default. Any application that needs
     * client/user authentication should override this method.
     *
     * @return the Authenticator service that can authenticate clients of this web application
     */
    protected Authenticator createAuthenticator()
        {
        return new NeverAuthenticator();
        }
    }