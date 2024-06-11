/**
 * An `SessionBroker` is a service that knows how to provide a session for an incoming request, if
 * the request is associated with a session or if the associated `Endpoint` requires a session and
 * one must be created.
 */
service NeverBroker
            implements Duplicable, Broker {

    // ----- constructors --------------------------------------------------------------------------

    construct() {}

    @Override
    construct(NeverBroker that) {}

    // ----- Broker API ----------------------------------------------------------------------------

    @Override
    Session?|ResponseOut findSession(RequestIn request, Boolean required) {
        return required
                ? new SimpleResponse(ServiceUnavailable, "Server session management has not been enabled")
                : Null;
    }
}
