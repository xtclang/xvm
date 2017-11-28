/**
 * A ContextToken is used to constrain the wall-clock time limit for calls made to other services
 * from this service. Specifically, once a contextToken is put in place, all service invocations that
 * originate from this service will carry the contextToken, such that those service invocations will
 * need to complete within the remainder of that contextToken, or risk a ContextTokenException being raised.
 *
 * The ContextToken mechanism is a cooperative mechanism, and is not intended to be used as a strict
 * resource management mechanism. Rather, it is intended for uses in which returning with a bad
 * answer (or an exception) is preferable to not returning at all. Generally, contextTokens are useful
 * for user-interactive systems, in which failing to respond within a reasonable period of time is
 * unacceptable, and for systems that need to assume the worst if an external process -- such as a
 * persistent storage system or network communication with a remote system -- takes an unexpectedly
 * long period of time.
 *
 * The contextToken is stored on the current service and exposed as {@link Service.contextToken}. When a new
 * contextToken is created, it automatically registers itself with the current service using the {@link
 * Service.registerContextToken} method. Employing either a {@code using} or {@code try}-with-resources
 * block will automatically unregister the contextToken at the conclusion of the block, causing all
 * of the potentially-asynchronous service invocations that occurred within the block to be infected
 * by the contextToken. When the contextToken unregisters itself, it re-registers whatever previous contextToken it
 * replaced (if any).
 *
 * ContextTokens _nest_. When a new contextToken is created, it configures itself to use no more time than
 * remains on the current contextToken. This allows the developer to create a new contextToken without
 * concern that it is violating an existing contextToken. In the following example, two different
 * time-outs (1 second and 500 milli-seconds) are used, but if there is less than 1 second remaining
 * on the current contextToken for the current service, then the contextTokens in the example will be reduced
 * in order to fit within that existing contextToken:
 *
 *   // to obtain asynchronous results from other services, use future references for the return
 *   // values from methods on those services
 *   @Future Body body;
 *   @Future Ad   ad1;
 *   @Future Ad   ad2;
 *
 *   // async request for the page body, but don't wait more than 1000ms for it
 *   using (new ContextToken(Duration:"1s"))
 *       {
 *       body = contentSvc.genBody();
 *
 *       // async request for two advertisements, but don't wait more than 500ms for either
 *       using (new ContextToken(Duration:"500ms"))
 *           {
 *           ad1 = adSvc1.selectAd();
 *           ad2 = adSvc2.selectAd();
 *           }
 *       }
 *
 *   // handle time-outs and other exceptions using some default content
 *   ad1  = &ad1.handle(e -> blankAd);
 *   ad2  = &ad2.handle(e -> blankAd);
 *   body = &body.handle(e -> errPage(e));
 *
 *   // an attempt to dereference a future will automatically wait for the future to complete;
 *   // at this point, wait for the three separate results, and use them to render the page, but
 *   // regardless, try not to take more than 1000ms total for all three parts to complete
 *   return renderPage(body, ad1, ad2);
 *
 * If a service needs to begin a long-running task that is independent of the contextToken that the
 * service is currently constrained by, construct an _independent_ contextToken:
 *
 *   using (new ContextToken(Duration:"5h", true))
 *       {
 *       new LongRunningReports().begin();
 *       }
 */
const ContextToken<TokenType>
        implements Closeable
    {
    construct ContextToken(String name, TokenType value, Boolean independent = false)
        {
        // store off the previous contextToken; it will be replaced by this contextToken, and restored when
        // this contextToken is closed
        previousContextToken = this:service.contextToken;

        startTime = runtimeClock.time;
        deadline  = startTime + duration;
        }
    finally
        {
        this:service.registerContextToken(this);
        }

    /**
     * The {@code ContextToken} that this contextToken replaced, if any.
     */
    ContextToken? previousContextToken;

    /**
     * The name of the token.
     */
    String name;

    /**
     * The value of the token.
     */
    TokenType value;

    /**
     * Determine whether this contextToken is the active contextToken for the current service.
     */
    Boolean active.get()
        {
        return this:service.contextToken == this;
        }

    /**
     * Determine whether this contextToken is registered with the current service, regardless of whether
     * it is the currently-active contextToken.
     */
    Boolean registered.get()
        {
        ContextToken? registered = this:service.contextToken;
        while (registered?)
            {
            if (this == registered)
                {
                return true;
                }

            registered = registered.previousContextToken;
            }

        return false;
        }

    /**
     * Close the contextToken. This method is invoked automatically by the {@code using} or
     * {@code try} with-resources keywords.
     */
    Void close()
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