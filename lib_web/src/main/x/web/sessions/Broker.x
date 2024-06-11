/**
 * A [Session] `Broker` is a service that knows how to provide a `Session` for an
 * [incoming request](RequestIn), if the request is associated with a `Session` or if the associated
 * `Endpoint` requires a `Session` and one must be created.
 *
 * The `Session` `Broker` is expected to be a bottleneck, since implementations are likely to employ
 * computationally expensive logic including secure hashes and/or encryption, and may also rely on
 * a persistent store such as a database; to maximize concurrency, the web server can
 * [duplicate](Duplicable.duplicate) a `Session` `Broker` as necessary in order to avoid contention
 * on a single instance.
 */
interface Broker
            extends Duplicable, service {
    /**
     * Find the [Session] indicated by the [request](RequestIn). The `Broker` is permitted to create
     * a `Session` if one is specified by the request in a trustworthy manner, but does not yet
     * exist; for example, a "device id" may be assigned to a device, and on the device's first
     * communication with the server, a `Session` may be realized (created) for it.
     *
     * @param request  the incoming [request](RequestIn)
     *
     * @return `True` iff the client is supported by this `Broker`, and a [Session] is indicated by
     *         the [request](RequestIn) and it exists
     * @return (conditional) the [Session] indicated by the [request](RequestIn)
     * @return (conditional) if non-`Null`, this is a [response](ResponseOut) that must immediately
     *         be sent to the client as part of the Session establishment or maintenance dialogue
     */
    conditional (Session, ResponseOut?) findSession(RequestIn request);

    /**
     * Find the [Session] indicated by the [request](RequestIn), if it exists; otherwise, create one
     * if possible, returning either the new `Session` or the response to the client necessary to
     * establish the new `Session`.
     *
     * If the request indicates a session and the indicated session exists, then return it. If no
     * session exists, and one is required, then create and return it. If further communication with
     * the client is required to establish the session, or if an error occurs, then return a
     * [response](ResponseOut) for the client.
     *
     * @param request  the incoming [request](RequestIn)
     *
     * @return `True` iff this Broker is capable of handling the [Session]-related duties for the
     *         request
     * @return (conditional) the [Session] indicated by the [request](RequestIn), or created for it;
     *         if the returned `Session` is `Null`, that indicates that the `Session` cannot be
     *         created yet because the dialogue (session-creation negotiation) with the client has
     *         not progressed sufficiently, and the returned [ResponseOut] must be non-`Null`
     * @return (conditional) if non-`Null`, this is a [response](ResponseOut) that must immediately
     *         be sent to the client as part of the Session establishment or maintenance dialogue
     */
    conditional (Session?, ResponseOut?) requireSession(RequestIn request);
}
