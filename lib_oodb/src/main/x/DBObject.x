/**
 * The base interface for all "database objects", which are the representations of database
 * organization, functionality, and storage. Examples including [DBMap], [DBQueue], [DBList],
 * [DBLog], [DBCounter], and [DBFunction].
 *
 * The various `DBObject` interfaces are expected to be implemented as part of a database engine
 * implementation, but support orthogonal functionality that can be provided by an application
 * developer, packaged in the form of a `mixin`. Additionally, it is expected that database engine
 * implementations will make use of code generation to form a deployable database type system for
 * an application's database module; while not required, the code generation is expected to support
 * a richer set of capabilities (such as database evolution) without a dramatic performance impact.
 * The result is that an application's database design is independent of a specific database engine
 * implementation, coupling to it only through the implementation-independent `oodb` module
 * interfaces, yet still able to provide the custom application object interfaces as if they were
 * built into the database design itself.
 */
interface DBObject
    {
    /**
     * The root database object.
     */
    @RO RootSchema dbRoot.get()
        {
        return dbParent?.dbRoot : this.as(RootSchema);
        }

    /**
     * The parent database object, or `Null` if this is the root (the database itself).
     */
    @RO DBObject!? dbParent;

    /**
     * Categorizations of the various forms of `DBObject`s.
     */
    enum DBCategory {DBSchema, DBMap, DBList, DBQueue, DBLog, DBCounter, DBValue, DBFunction}

    /**
     * The category of this `DBObject`.
     */
    @RO DBCategory dbCategory;

    /**
     * Each `DBObject` has a uniquely identifying `String` name that identifies it within its parent
     * `DBObject`. The name follows the Ecstasy language lexical rules for identifiers, and by
     * convention uses _CamelCase_.
     */
    @RO String dbName;

    /**
     * Each `DBObject` has a uniquely identifying path that identifies it within its database.
     */
    @RO Path dbPath.get()
        {
        return dbParent?.dbPath + dbName : Path.ROOT;
        }

    /**
     * Each `DBObject` potentially contains nested database objects. This `Map` represents all of
     * the nested database objects, keyed by the [dbName] of each nested `DBObject`.
     */
    @RO Map<String, DBObject!> dbChildren;

    /**
     * Obtain the specified `DBObject` by its [dbName], or by its [dbPath] relative to the `dbPath`
     * of this `DBObject`.
     *
     * @param path  the path of a `DBObject` relative to this `DBObject`
     *
     * @return the specified `DBObject`, or `Null` if the path did not identify a `DBObject`
     */
    DBObject!? dbObjectFor(Path path)
        {
        DBObject! result = this;
        for (Path sub : path)
            {
            switch (sub.form)
                {
                case Root:
                    result = dbRoot;
                    break;

                case Parent:
                    val parent = result.dbParent;
                    if (parent == Null)
                        {
                        return Null;
                        }
                    result = parent;
                    break;

                case Current:
                    break;

                case Name:
                    if (result := dbChildren.get(sub.name))
                        {
                        }
                    else
                        {
                        return Null;
                        }
                    break;
                }
            }
        return result;
        }

    /**
     * Obtain the specified `DBMap` by its [dbName], or by its [dbPath] relative to the `dbPath` of
     * this `DBObject`.
     *
     * @param path  the name (or the relative path) of the `DBMap` within this `DBObject`
     *
     * @return the specified `DBMap`
     *
     * @throws TypeMismatch if the specified name or path refers to a `DBObject` that is not a
     *        `DBMap` of the indicated `Key` and `Value` type
     * @throws IllegalArgument if the specified name or path does not refer to a `DBObject`
     */
    <Key, Value> DBMap<Key, Value> mapOf(Path path)
        {
        if (DBObject dbo ?= dbObjectFor(path))
            {
            return dbo.as(DBMap<Key, Value>);
            }

        throw new IllegalArgument($"No DBMap exists for path={path}");
        }

    /**
     * Obtain the specified `DBQueue` by its [dbName], or by its [dbPath] relative to the `dbPath`
     * of this `DBObject`.
     *
     * @param path  the name (or the relative path) of the `DBQueue` within this `DBObject`
     *
     * @return the specified `DBQueue`
     *
     * @throws TypeMismatch if the specified name or path refers to a `DBObject` that is not a
     *        `DBQueue` of the indicated `Element` type
     * @throws IllegalArgument if the specified name or path does not refer to a `DBObject`
     */
    <Element> DBQueue<Element> queueOf(Path path)
        {
        if (DBObject dbo ?= dbObjectFor(path))
            {
            return dbo.as(DBQueue<Element>);
            }

        throw new IllegalArgument($"No DBQueue exists for path={path}");
        }

    /**
     * Obtain the specified `DBList` by its [dbName], or by its [dbPath] relative to the `dbPath`
     * of this `DBObject`.
     *
     * @param path  the name (or the relative path) of the `DBList` within this `DBObject`
     *
     * @return the specified `DBList`
     *
     * @throws TypeMismatch if the specified name or path refers to a `DBObject` that is not a
     *        `DBList` of the indicated `Element` type
     * @throws IllegalArgument if the specified name or path does not refer to a `DBObject`
     */
    <Element> DBList<Element> listOf(Path path)
        {
        if (DBObject dbo ?= dbObjectFor(path))
            {
            return dbo.as(DBList<Element>);
            }

        throw new IllegalArgument($"No DBList exists for path={path}");
        }

    /**
     * Obtain the specified `DBLog` by its [dbName], or by its [dbPath] relative to the `dbPath`
     * of this `DBObject`.
     *
     * @param path  the name (or the relative path) of the `DBLog` within this `DBObject`
     *
     * @return the specified `DBLog`
     *
     * @throws TypeMismatch if the specified name or path refers to a `DBObject` that is not a
     *        `DBLog` of the indicated `Element` type
     * @throws IllegalArgument if the specified name or path does not refer to a `DBObject`
     */
    <Element> DBLog<Element> logOf(Path path)
        {
        if (DBObject dbo ?= dbObjectFor(path))
            {
            return dbo.as(DBLog<Element>);
            }

        throw new IllegalArgument($"No DBLog exists for path={path}");
        }

    /**
     * Obtain the specified `DBCounter` by its [dbName], or by its [dbPath] relative to the `dbPath`
     * of this `DBObject`.
     *
     * @param path  the name (or the relative path) of the `DBCounter` within this `DBObject`
     *
     * @return the specified `DBCounter`
     *
     * @throws TypeMismatch if the specified name or path refers to a `DBObject` that is not a
     *        `DBCounter`
     * @throws IllegalArgument if the specified name or path does not refer to a `DBObject`
     */
    DBCounter counterOf(Path path)
        {
        if (DBObject dbo ?= dbObjectFor(path))
            {
            return dbo.as(DBCounter);
            }

        throw new IllegalArgument($"No DBCounter exists for path={path}");
        }

    /**
     * Obtain the specified `Function` by its name, or by its path relative to the `dbPath`
     * of this `DBObject`.
     *
     * @param path  the name (or the relative path) of the `DBFunction` within this `DBObject`
     *
     * @return the specified `Function`
     *
     * @throws TypeMismatch     if the specified name or path refers to a `DBObject` that is not a
     *                          `DBFunction` representing a `Function`
     * @throws IllegalArgument  if the specified name or path does not exist
     */
    Function functionFor(Path path)
        {
        if (DBObject dbo ?= dbObjectFor(path))
            {
            return dbo.as(DBFunction).callable;
            }

        throw new IllegalArgument($"No DBFunction exists for path={path}");
        }

    /**
     * Invoke a named function.
     *
     * @param fn    the name of the function (or the function itself) to invoke; if a function
     *              reference is passed, it must be a named function registered in the database
     * @param args  the arguments to use for invoking the function
     * @param when  `Null` indicates immediate execution within the current transaction; otherwise,
     *              the value indicates a point in time after the successful completion of the
     *              current transaction at-or-after-which to invoke the function
     *
     * @return the return values from the function, or an empty `Tuple` if the invocation is not
     *         synchronous (i.e. if `when` is not `Null`)
     *
     * @throws TypeMismatch     if the specified name or path refers to a `DBObject` that is not a
     *                          `Function`, or if any of the arguments are not of the correct type
     * @throws IllegalArgument  if the specified name or path does not exist, or if the number of
     *                          arguments is either too many or too few, or if a `Function`
     *                          reference is passed that is either unregistered with the database or
     *                          has unsupported argument types
     */
    Tuple dbInvoke(String | Function fn, Tuple args = Tuple:(), (Duration|DateTime)? when = Null)
        {
        throw new IllegalArgument($"Function does not exists: {fn}");
        }


    // ----- transactional information -------------------------------------------------------------

    /**
     * Indicates whether the database object is both stateful and transactional, meaning that
     * mutations to the object are collected transactionally. Most database objects are
     * transactional, but there are a few specific exceptions:
     *
     * * A [DBCounter] may be extra-transactional, so that it can be used by concurrent transactions
     *   to generate unique values, such as for use as a "key counter";
     * * A [DBLog] may be extra-transactional, so that it can be highly concurrent, and also capable
     *   of retaining log entries made during a transaction even when that transaction rolls back,
     *   such as would be expected from an "error log" use case;
     * * A [DBSchema] is stateless, and thus it is non-transactional.
     */
    @RO Boolean transactional.get()
        {
        return True;
        }

    /**
     * Represents a change within a transactional database object.
     *
     * This interface provides a contextual before-and-after view of the transaction. Obtaining a
     * 'before' or 'after' transactional view of the `DBObject` should be assumed to be a relatively
     * expensive operation, particularly for a historical `TxChange` (one pulled from some previous
     * point in the a commit history).
     */
    interface TxChange
        {
        /**
         * The state of the `DBObject`, before this change was made.
         *
         * The returned `DBObject` does not allow mutation.
         *
         * When evaluating a historical change, this property may be expensive to obtain.
         */
        @RO DBObject pre;

        /**
         * The state of the `DBObject`, after this change was made. Note that, for an async trigger,
         * the state of the database may have since changed from this "post" view.
         *
         * The returned `DBObject` does not allow mutation.
         *
         * When evaluating a historical change, this property may be expensive to obtain.
         */
        @RO DBObject post;
        }


    // ----- transaction trigger API ---------------------------------------------------------------

    /**
     * Represents an automatic validation of any changes that occurred to the DBObject within a user
     * transaction.
     *
     * (The term "user transaction" refers to changes from an application, as opposed to changes
     * that occur from automatic trigger processing.)
     */
    static interface Validator<TxChange extends DBObject.TxChange>
        {
        /**
         * Validate a transactional change.
         *
         * This method **must** not attempt to make changes to the database.
         *
         * @param change  the change that is being validated
         *
         * @return True iff the change is valid; False will cause the transaction to roll back
         */
        Boolean validate(TxChange change);
        }

    /**
     * Represents an automatic processing of any changes that occurred to the DBObject within a user
     * transaction, allowing the DBObject to be further modified before the commit occurs.
     *
     * (The term "user transaction" refers to changes from an application, as opposed to changes
     * that occur from automatic trigger processing.)
     */
    static interface Rectifier<TxChange extends DBObject.TxChange>
        {
        /**
         * Rectify the contents of a transactional change to the containing DBObject.
         *
         * This method **must** not attempt to make changes to any other DBObject.
         *
         * @param change  the change that is being rectified
         *
         * @return True iff the change is valid; False will cause the transaction to roll back
         */
        Boolean rectify(TxChange change);
        }

    /**
     * Represents an automatic processing of any changes that occurred to the DBObject, allowing
     * other DBObjects to be modified accordingly.
     *
     * A Distributor is useful for maintaining summary data, by responding to any change on detail
     * data, and pushing the necessary adjustments to another DBObject that holds the summary
     * information.
     */
    static interface Distributor<TxChange extends DBObject.TxChange>
        {
        /**
         * Distribute changes from the transactional change to the containing DBObject, to other
         * DBObjects.
         *
         * This method **must** not attempt to make changes to either (i) this DBObject, or (ii)
         * any DBObject that was already modified withing the current transaction. (All Distributor
         * instances that are indicated by a transaction are executed in a pass, and during that
         * pass, any number of Distributor instances may modify the same previously-unmodified
         * DBObjects, which in turn could trigger another Distributor pass, during which any
         * DBObjects modified previous to that pass must not be modified by the Distributors in that
         * pass.)
         *
         * @param change  the change that is being distributed
         *
         * @return True iff the change is valid; False will cause the transaction to roll back
         */
        Boolean process(TxChange change);
        }

    /**
     * Represents an automatic response to a change that occurs when a transaction commits.
     *
     * Triggers can be pre-commit or post-commit. The pre-commit form should be used when a trigger
     * has to _prevent_ a transaction, or if it is essential that the information (changes) from the
     * transaction be hidden until after the trigger has executed. The post-commit ("async") form
     * should generally be used in all other scenarios.
     *
     * The `Trigger` is a static interface, because it is expected to (generally) be implemented
     * separately from the `DBObject` itself. For example, it is expected that a `DBObject`
     * implementation will be provided by a database engine implementer, while the `Trigger` will be
     * authored by the application developer.
     */
    static interface AsyncTrigger<TxChange extends DBObject.TxChange>
        {
        /**
         * Determine if this AsyncTrigger applies to the specified change. This method must **not**
         * attempt to modify the database. The use of this method by the database implementation is
         * optional; the database engine may call [process] without first calling this method.
         *
         * @param change  the change being evaluated
         *
         * @return `True` iff the AsyncTrigger is interested in the specified change
         */
        Boolean appliesTo(TxChange change)
            {
            return True;
            }

        /**
         * Execute the AsyncTrigger functionality. This method may modify the database.
         *
         * @param change  the change that the AsyncTrigger is firing in response to
         */
        void process(TxChange change);
        }
    }
