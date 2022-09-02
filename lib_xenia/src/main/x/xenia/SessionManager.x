import SessionStore.IOResult;


/**
 * A service that keeps track of all of the `Session` objects.
 */
@Concurrent
service SessionManager(SessionStore store, SessionProducer instantiateSession)
    {
    // ----- properties ----------------------------------------------------------------------------

    /**
     * The means to persistently store sessions.
     */
    protected/private SessionStore store;

    /**
     * The means to instantiate sessions.
     */
    typedef function SessionImpl(SessionManager, Int) as SessionProducer;
    protected/private SessionProducer instantiateSession;

    /**
     * Increment the session identifier "counter" by a large prime number.
     */
    private static Int64 ID_GAP = 0x0030_8DC2_CBEE_2A75;        // a fun prime: 13666666666666613

    /**
     * Wrap the session identifier counter around before it reaches the integer limit.
     */
    protected static Int64 ID_LIMIT = 0x0FFF_FFFF_FFFF_FFFF;    // don't use the entire 64-bit range

    /**
     * The most recently generated session id.
     */
    protected/private Int previousId =
        {
        // initialize to a random session id
        @Inject Random rnd;
        return rnd.int() & ID_LIMIT;
        };

    /**
     * The total number of sessions created.
     */
    public/private Int createdCount = 0;

    /**
     * The total number of sessions destroyed.
     */
    public/private Int deletedCount = 0;

    /**
     * For each session, the status of the session may be known or unknown. This allows the bulk of
     * the session information to remain in persistent storage until it is explicitly requested.
     *
     * * `Unknown` - the session is not cached in memory, and it may or may not exist in persistent
     *    storage
     * * `Nonexistent` - the session is known to not exist
     * * `OnDisk` - the session is not cached in memory, but it is known to exist in persistent
     *   storage
     * * `InMemory` - the session is cached in memory
     */
    protected enum SessionStatus
        {
        Unknown,
        Nonexistent,
        OnDisk,
        InMemory,
        }

    /**
     * [Session] by id, or the [SessionStatus] if the `Session` is not `InMemory` but the status is
     * known.
     */
    protected/private Map<Int, SessionImpl|SessionStatus> sessions = new HashMap();

    /**
     * The daemon responsible for cleaning up expired session data.
     */
    protected/private SessionPurger purger = new SessionPurger();

    /**
     * When a user has **not** explicitly indicated that their device is trusted, the session
     * cookies are not persistent and the time-out for the session is short.
     */
    public/private Duration untrustedDeviceTimeout = Duration:30M;      // default is half hour

    /**
     * When a user has explicitly indicated that their device is trusted, the session cookies may
     * (with consent) be persistent, and the time-out for the session may be dramatically longer.
     */
    public/private Duration trustedDeviceTimeout   = Duration:60D;      // default is two months


    // ----- session control -----------------------------------------------------------------------

    /**
     * Obtain the session status for the specified session id.
     *
     * @param id  the session id
     *
     * @return the session status
     */
    SessionStatus getStatusById(Int id)
        {
        if (SessionImpl|SessionStatus status := sessions.get(id))
            {
            return status.is(SessionImpl)
                    ? InMemory
                    : status;
            }
        else
            {
            return Unknown;
            }
        }

    /**
     * Obtain the session for the specified session id.
     *
     * @param id  the session id
     *
     * @return `True` iff the session exists
     * @return (conditional) the session
     */
    conditional SessionImpl getSessionById(Int id)
        {
        if (SessionImpl|SessionStatus session := sessions.get(id))
            {
            if (session.is(SessionImpl))
                {
                return True, session;
                }

            switch (session)
                {
                case Nonexistent:
                    return False;

                case Unknown:
                case OnDisk:
                    SessionImpl|IOResult result = store.load(id);
                    if (result.is(SessionImpl))
                        {
                        return True, result;
                        }
                    else
                        {
                        switch (result)
                            {
                            case Success:
                            case SerializationIncomplete:
                                // TODO how would this information be reported? incomplete would have to return the session
                                assert;

                            case NoSuchSession:
                                sessions.put(id, Nonexistent);
                                return False;

                            case SerializationFailure:
                            case IOFailure:
                                // TODO should there be an "error" placed in the session cache?
                                return False;
                            }
                        }

                case InMemory:
                    assert;
                }
            }

        return False;
        }

    /**
     * Determine if the specified id has session data in persistent storage.
     *
     * @param id  the session id
     *
     * @return True iff the specified id has session data in persistent storage
     */
    Boolean sessionExistsInStorage(Int id)
        {
        // TODO check sessions, and add flag to SessionImpl as well "on disk" vs. not and "up to date" vs not
        return False;
        }

    /**
     * Instantiate a new [SessionImpl] object, including any [Session] mix-ins that the [WebApp]
     * contains.
     *
     * @return a new [SessionImpl] object, including any mixins declared by the application
     */
    SessionImpl createSession()
        {
        Int         id      = generateId();
        SessionImpl session = instantiateSession(this, id);
        sessions.put(id, session);

        purger.track^(id);

        return session;
        }

    /**
     * Generate a session ID.
     *
     * @return an unused session ID
     */
    Int generateId()
        {
        while (True)
            {
            Int id = previousId + ID_GAP & ID_LIMIT;
            previousId = id;

            switch (getStatusById(id))
                {
                case Nonexistent:
                    return id;

                case Unknown:
                    if (!sessionExistsInStorage(id))
                        {
                        return id;
                        }
                    continue;
                case OnDisk:
                case InMemory:
                    // strange but not impossible: we have collided with an existing session; to
                    // compensate, increment the base by a different prime value than the gap; note
                    // that we have to assume that this method is concurrent, i.e. not running all
                    // at once (so the previousId may have already been changed by someone else,
                    // after we changed it above)
                    @Inject Random rnd;
                    Int adjust = HashMap.PRIMES[rnd.int(HashMap.PRIMES.size)];
                    previousId = previousId + adjust & ID_LIMIT;
                    break;
                }
            }
        }

    /**
     * Explicitly destroy the specified session.
     *
     * @param id  the session id
     */
    void destroySession(Int id)
        {
        // TODO probably ask the session to delete itself, if the session is (or may be) currently in use
        store.erase^(id);
        sessions.put(id, Nonexistent);
        }
    }