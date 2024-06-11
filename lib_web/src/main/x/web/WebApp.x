import ecstasy.reflect.AnnotationTemplate;
import ecstasy.reflect.Argument;
import ecstasy.reflect.ClassTemplate.Composition;

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
        into module {
    /**
     * The registry for this WebApp.
     */
    @Lazy Registry registry_.calc() {
        return new Registry();
    }

    /**
     * Handle an otherwise-unhandled exception or other error that occurred during [Request]
     * processing within this `WebApp`, and produce a [Response] that is appropriate to the
     * exception or other error that was raised.
     *
     * @param request   the request being processed
     * @param error     the exception thrown, the error description, or an HttpStatus code
     *
     * @return the [Response] to send back to the caller
     */
    ResponseOut handleUnhandledError(RequestIn request, Exception|String|HttpStatus error) {
        // TODO CP: does the exception need to be logged?
        HttpStatus status = error.is(RequestAborted) ? error.status :
                            error.is(HttpStatus)     ? error
                                                     : InternalServerError;

        return new responses.SimpleResponse(status=status, bytes=error.toString().utf8());
    }

    /**
     * A `Webapp` that knows how to provide an [Authenticator] should implement this interface in
     * order to do so.
     */
    static interface AuthenticatorFactory {
        /**
         * Create (or otherwise provide) the [Authenticator] that this `WebApp` will use. It is
         * expected that this method will only be called once.
         *
         * @return the [Authenticator] for this `WebApp`
         */
        Authenticator createAuthenticator();
    }

    /**
     * The [Authenticator] for the web application.
     */
    @Lazy Authenticator authenticator.calc() {
        // use the Authenticator provided by injection, if any, which can be specified as part of
        // the application deployment process or otherwise provided by the containing HTTP server
        @Inject Authenticator? providedAuthenticator;
        return providedAuthenticator?;

        // allow a WebApp module to implement the factory method createAuthenticator()
        if (this.is(AuthenticatorFactory)) {
            return createAuthenticator();
        }

        // disable authentication, since no authenticator was found
        return new NeverAuthenticator();
    }

    /**
     * A `Webapp` that knows how to provide a [Session Broker](sessions.Broker) should implement
     * this interface in order to do so.
     */
    static interface SessionBrokerFactory {
        /**
         * Create (or otherwise provide) the [Session Broker](sessions.Broker) that this `WebApp`
         * will use. It is expected that this method will only be called once.
         *
         * @return the [Session Broker](sessions.Broker) for this `WebApp`
         */
        sessions.Broker createSessionBroker();
    }

    /**
     * The [Session Broker](sessions.Broker) for the web application.
     */
    @Lazy sessions.Broker sessionBroker.calc() {
        // use the Session Broker provided by injection, if any, which can be specified as part of
        // the application deployment process or otherwise provided by the containing HTTP server
        @Inject sessions.Broker? sessionBroker;
        return sessionBroker?;

        // allow a WebApp module to implement the factory method createSessionBroker()
        if (this.is(SessionBrokerFactory)) {
            return createSessionBroker();
        }

        // disable sessions, since no broker was provided
        return new sessions.NeverBroker();
    }
}