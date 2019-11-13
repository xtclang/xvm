/**
 * TODO
 */
const ContextToken<TokenType extends immutable Object>
        implements Closeable
    {
    construct(String name, TokenType value)
        {
        // store off the previous contextToken; it will be replaced by this contextToken, and restored when
        // this contextToken is closed
        previousContextToken = this:service.findContextToken(name);

//        startTime = clock.time;
//        deadline  = startTime + duration;
        }
    finally
        {
        this:service.registerContextToken(this);
        }

    /**
     * The `ContextToken` that this contextToken replaced, if any.
     */
    ContextToken!<>? previousContextToken;

    /**
     * The name of the token.
     */
    String name;

    /**
     * The value of the token.
     */
    TokenType value;

    /**
     * Find a contextToken for a given name for the current service.
     */
    ContextToken!<>? current.get()
        {
        return this:service.findContextToken(name);
        }

    /**
     * Determine whether this contextToken is the active contextToken for the current service.
     */
    Boolean active.get()
        {
        return current == this;
        }

    /**
     * Determine whether this contextToken is registered with the current service, regardless of whether
     * it is the currently-active contextToken.
     */
    Boolean registered.get()
        {
        ContextToken<>? token = current;

        while (token != null)
            {
            if (&this == &token)
                {
                return true;
                }

            token = token.previousContextToken;
            }

        return false;
        }

    /**
     * Close the contextToken. This method is invoked automatically by the `using` or
     * `try` with-resources keywords.
     */
    @Override
    void close()
        {
        if (registered)
            {
            // the reason that the contextToken checks whether it is registered instead of if it is
            // active is that it is possible that a downstream ContextToken was not properly closed,
            // e.g. by failing to use a "using" or "try"-with-resources construct
            this:service.registerContextToken(previousContextToken);
            }
        }
    }