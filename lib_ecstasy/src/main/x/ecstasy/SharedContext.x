import numbers.PseudoRandom;

/**
 * A `SharedContext` is used to attach a context-specific value to a conceptual thread of execution,
 * such that the value is accessible by any subsequent downstream callee (i.e. any code on the
 * logical thread of execution) within that context.
 *
 *     // anyone with access to this object can access and/or modify the "shared context value"
 *     static SharedContext<String> UserId = new SharedContext("userId");
 *
 *     void handleRequest(String user, Request request) {
 *         using (UserId.withValue(user)) {    // <-- this binds a value to the shared context
 *             // do the request processing in an asynchronous manner on a separate service
 *             new ProcessingService().process^(request.msg);
 *         }                                   // <-- this unbinds the value from the shared context
 *     }
 *
 *     // a separate service class
 *     service ProcessingService {
 *         // ...
 *
 *         void process(Request request) {
 *             assert validUser();
 *             // ...
 *         }
 *
 *         Boolean validUser() {
 *             // even though we're in the context of a different service, running on a different
 *             // fiber, and the service that called us has probably already returned from the
 *             // handleRequest() method, the SharedContext is still available here because we were
 *             // invoked from within the "using" block that specified the UserId
 *             if (String user := UserId.hasValue()) {
 *                 return db.users.contains(user);
 *             }
 *             return False;
 *         }
 *     }
 *
 */
const SharedContext<Value extends service | immutable>
        implements Hashable {
    /**
     * Construct a SharedContext with an optional name and default value.
     *
     * @param name   (optional) a descriptive name for the `SharedContext`, useful when debugging
     * @param value  (optional) the "initial" or "default" value that the SharedContext will provide
     *               when no value has otherwise been specified
     */
    construct(String? name = Null, Value? value = Null) {
        this.name         = name;
        this.defaultValue = value;
        this.hash         = rnd.int64();
    }

    /**
     * The name of the SharedContext. Using this property is optional; its purpose is to support
     * debugging.
     */
    String? name;

    /**
     * The value that will be provided by the SharedContext when no value has otherwise been
     * specified.
     */
    Value? defaultValue;

    /**
     * A random number generator used to produce reasonable hash code values.
     */
    private static PseudoRandom rnd = new PseudoRandom();

    /**
     * A cached hash code value.
     */
    private Int hash;

    /**
     * The current `Token` for this `SharedContext`, otherwise `Null`.
     */
    Token!? current.get() = this:service.findContextToken(this);

    /**
     * Bind a value to the `SharedContext`, and return a [Closeable] object that will unbind the
     * value when closed.
     *
     *     using (UserId.withValue(user)) {
     *         // all activity within this using block can obtain the "user" value from the UserId
     *         // SharedContext
     *         assert String test := UserId.hasValue(), test == user;
     *     }
     *
     * @param value  the value to bind to the `SharedContext`
     *
     * @return a [Closeable] [Token]
     */
    Token withValue(Value value) {
        Token token = new Token(value, this:service.findContextToken(this));
        this:service.registerContextToken(token);
        return token;
    }

    /**
     * Determine if the SharedContext has a value, and what that value is.
     *
     * @return `True` iff the `SharedContext` has a value
     * @return (conditional) the value of the `SharedContext`
     */
    conditional Value hasValue() {
        if (Token token ?= current) {
            return True, token.value;
        }
        return defaultValue.is(Value);
    }

    // ----- Hashable interface --------------------------------------------------------------------

    @Override
    static <CompileType extends SharedContext> Int64 hashCode(CompileType value) = value.hash;

    @Override
    static <CompileType extends SharedContext> Boolean equals(CompileType value1, CompileType value2) {
        // they must be the same exact object instance
        return &value1 == &value2;
    }

    // ----- SharedContext Token -------------------------------------------------------------------

    /**
     * This is a point-in-time value-holder that holds a value for a [SharedContext].
     *
     * @param value     the `Value` held by this `Token`; iff this `Token` is [active], the `value`
     *                  of this `Token` is the value of the enclosing [SharedContext]
     * @param previous  the previous `Token` that this `Token` replaced, if any. When this `Token`
     *                  closes, the act of closing the `Token` will restore the previous `Token`
     */
    const Token(Value value, Token? previous)
            implements Closeable {

        /**
         * `True` iff this `Token` is registered with the current service for the enclosing
         * [SharedContext], regardless of whether this `Token` is the currently-active `Token`.
         */
        Boolean registered.get() {
            Token? token = current;
            while (token != Null) {
                if (this == token) {
                    return True;
                }
                token = token.previous;
            }
            return False;
        }

        /**
         * `True` iff this `Token` is currently the active `Token` for the [SharedContext].
         */
        Boolean active.get() = this == current;

        /**
         * Close the `Token`. This method is invoked automatically by the `using` or
         * `try`-with-resources keyword.
         */
        @Override
        void close(Exception? cause = Null) {
            // the reason that the `Token` checks whether it is [registered] instead of if it
            // is [active] is that it is possible that a subsequent `Token` was not properly
            // closed, e.g. by failing to use a "using" or "try"-with-resources construct
            if (registered) {
                this:service.registerContextToken(previous);
            }
        }

        @Override
        static <CompileType extends Token> Boolean equals(CompileType value1, CompileType value2) {
            // they must be the same exact object instance
            return &value1 == &value2;
        }
    }
}