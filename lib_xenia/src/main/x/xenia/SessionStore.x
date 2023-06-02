/**
 * A service that provides support for [Session] objects in persistent storage.
 *
 * TODO concurrent or not?
 */
service SessionStore {
    /**
     * * `Success` - the operation completed successfully
     * * `NoSuchSession` - the operation could not complete because the specified session was not
     *   found in persistent storage
     * * `SerializationIncomplete` - the operation completed, but some portion of the session data
     *   could not be stored/loaded
     * * `SerializationFailure` - the operation could not complete because of a failure during the
     *   serialization or deserialization process
     * * `IOFailure` - the operation could not complete because of an underlying persistent storage
     *   failure
     */
    enum IOResult {
        Success,
        NoSuchSession,
        SerializationIncomplete,
        SerializationFailure,
        IOFailure,
    }


    // ----- properties ----------------------------------------------------------------------------


    // ----- session control -----------------------------------------------------------------------


    // ----- session persistence -------------------------------------------------------------------

    /**
     * Determine if the specified id has session data in persistent storage.
     *
     * @param id  the session id
     *
     * @return True iff the specified id has session data in persistent storage
     */
    Boolean exists(Int id) {
        // TODO check sessions, and add flag to SessionImpl as well "on disk" vs. not and "up to date" vs not
        return False;
    }

    /**
     * Read the specified session from persistent storage.
     *
     * @param id  the session id
     *
     * @return either the session, or an indicator of the reason why the specified session could
     *         not be loaded
     */
    SessionImpl|IOResult load(Int id) {
        // TODO
        return NoSuchSession;
    }

    /**
     * Write the specified session to persistent storage.
     *
     * @param session  the session to store in persistent storage
     *
     * @return an indicator of the success or failure of the store operation
     */
    IOResult store(SessionImpl session) {
        // TODO
        return IOFailure;
    }

    /**
     * Erase the specified session from the persistent storage.
     *
     * @param id  the session id
     *
     * @return an indicator of the success or failure of the erase operation
     */
    IOResult erase(Int id) {
        // TODO
        return NoSuchSession;
    }


    // ----- internal -----------------------------------------------------------------------

}
