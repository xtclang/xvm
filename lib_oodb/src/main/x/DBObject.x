/**
 * The base interface for all "database objects", which are the representations of database
 * organization, functionality, and storage. Examples including [DBMap], [DBQueue], [DBList],
 * [DBProcessor], [DBLog], and [DBCounter].
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
    enum DBCategory {DBSchema, DBCounter, DBValue, DBMap, DBList, DBQueue, DBProcessor, DBLog}

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
    DBCounter dbCounterFor(Path path)
        {
        if (DBObject dbo ?= dbObjectFor(path))
            {
            return dbo.as(DBCounter);
            }

        throw new IllegalArgument($"No DBCounter exists for path={path}");
        }

    /**
     * Obtain the specified `DBValue` by its [dbName], or by its [dbPath] relative to the `dbPath`
     * of this `DBObject`.
     *
     * @param path  the name (or the relative path) of the `DBValue` within this `DBObject`
     *
     * @return the specified `DBValue`
     *
     * @throws TypeMismatch if the specified name or path refers to a `DBObject` that is not a
     *        `DBValue`
     * @throws IllegalArgument if the specified name or path does not refer to a `DBObject`
     */
    <Value extends immutable Const> DBValue<Value> dbValueFor(Path path)
        {
        if (DBObject dbo ?= dbObjectFor(path))
            {
            return dbo.as(DBValue<Value>);
            }

        throw new IllegalArgument($"No DBValue exists for path={path}");
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
    <Key extends immutable Const, Value extends immutable Const> DBMap<Key, Value> dbMapFor(Path path)
        {
        if (DBObject dbo ?= dbObjectFor(path))
            {
            return dbo.as(DBMap<Key, Value>);
            }

        throw new IllegalArgument($"No DBMap exists for path={path}");
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
    <Element extends immutable Const> DBList<Element> dbListFor(Path path)
        {
        if (DBObject dbo ?= dbObjectFor(path))
            {
            return dbo.as(DBList<Element>);
            }

        throw new IllegalArgument($"No DBList exists for path={path}");
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
    <Element extends immutable Const> DBQueue<Element> dbQueueFor(Path path)
        {
        if (DBObject dbo ?= dbObjectFor(path))
            {
            return dbo.as(DBQueue<Element>);
            }

        throw new IllegalArgument($"No DBQueue exists for path={path}");
        }

    /**
     * Obtain the specified `DBProcessor` by its [dbName], or by its [dbPath] relative to the
     * `dbPath` of this `DBObject`.
     *
     * @param path  the name (or the relative path) of the `DBProcessor` within this `DBObject`
     *
     * @return the specified `DBProcessor`
     *
     * @throws TypeMismatch if the specified name or path refers to a `DBObject` that is not a
     *        `DBProcessor` of the indicated `Element` type
     * @throws IllegalArgument if the specified name or path does not refer to a `DBObject`
     */
    <Element extends immutable Const> DBProcessor<Element> dbProcessorFor(Path path)
        {
        if (DBObject dbo ?= dbObjectFor(path))
            {
            return dbo.as(DBProcessor<Element>);
            }

        throw new IllegalArgument($"No DBProcessor exists for path={path}");
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
    <Element extends immutable Const> DBLog<Element> dbLogFor(Path path)
        {
        if (DBObject dbo ?= dbObjectFor(path))
            {
            return dbo.as(DBLog<Element>);
            }

        throw new IllegalArgument($"No DBLog exists for path={path}");
        }


    // ----- transactionally composable operations -------------------------------------------------

    /**
     * Perform a semi-blind test against the DBObject.
     *
     * @param test  a function to evaluate immediately, which will return a `Result` to the caller,
     *              without enlisting any data that is read in the process into the transaction
     *
     * @return the result of evaluating the function
     */
    <Result extends immutable Const> Result peek(function Result(DBObject) test)
        {
        return test(this);
        }

    /**
     * Perform a semi-blind test against the DBObject, and register the same test (along with the
     * result from running it) as a precondition for the commit (i.e. a "prepare constraint").
     *
     * This operation provides several capabilities:
     *
     * * First, it allows a test to be conducted on the current state of the data in the database,
     *   without explicitly enlisting _that exact copy of that state_. In other words, the test is
     *   semi-blind, because it does not return the data contents of the database to the caller.
     *
     * * Second, it registers the test and the corresponding result as a necessary precondition for
     *   the commit, such that changes to the database sufficient to change the result of the test,
     *   will result in the current transaction failing to commit (and thus rolling back). This
     *   method represents the only explicit way to dynamically register a pre-condition for commit,
     *   and as such can be thought of like a [Validator] -- except that a [Validator] must be
     *   declared in the schema and applies to every transaction (it is not dynamic), and this test
     *   may be parameterized, as long as all of the information that it captures is immutable.
     *
     * When the commit occurs, the test is repeated against the _immediately-preceding_ transaction,
     * although a database implementation may choose to skip the test if no database changes have
     * occurred that would impact the result of the test.
     *
     * @param test  a function to evaluate immediately, which will return a `Result` to the caller,
     *              without enlisting any data that is read in the process into the transaction,
     *              but which is also stored (along with that Boolean) as a precondition for commit
     *
     * @return the result of evaluating the function
     */
    <Result extends immutable Const> Result require(function Result(DBObject) test);

    /**
     * Perform a blind adjustment of the DBObject.
     *
     * A "blind" operation is one that is used to operate on the DBObject in a manner that is
     * transactionally composable, such that the invoking this operation would not itself cause the
     * current transaction to roll back, even in the presence of other concurrent transactions. The
     * expected implementation of this method is to defer its execution until the latter of (1)
     * any read operation on this same DBObject, or (2) the beginning of the prepare phase of the
     * transaction. By deferring the execution until the start of the prepare, the changes caused
     * by the function will come **after** any other transactions were running concurrently and have
     * since committed, and thus will not cause a roll-back to occur, even if those other
     * transactions altered this same DBObject.
     *
     * * The function must be freezable; it must not capture mutable references.
     *
     * * Access of, and changes to data from other `DBObject` is not predictable in terms of when
     *   it occurs; the guarantee of ordering is tied to this specific DBObject, and states simply
     *   that this operation will have occurred before any subsequent read operation against this
     *   same DBObject in this same current transaction is processed.
     *
     * * Subsequent non-blind operations against this same DBObject within the current transaction
     *   will destroy the composability of this operation, by forcing the execution of the function.
     *
     * * By moving the processing to the prepare phase, the prepare processing will be extended,
     *   reducing the transaction rate if transactions are not prepared in parallel.
     *
     * @param process  a function that operates against (and may both read and modify) the DBObject
     *                 reference passed to the function (representing this DBObject)
     */
    void defer(function Boolean(DBObject) adjust);


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
         * The state of the `DBObject`, after this change was made.
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
     * The purpose of this method is to prevent invalid data from being committed to the database.
     * This is a simple mechanism that allows a database to "assert" on any user transaction data,
     * before that data is committed.
     *
     * (The term "user transaction" refers to changes from an application, as opposed to changes
     * that occur from automatic trigger processing.)
     */
    static interface Validator<TxChange extends DBObject.TxChange>
        {
        /**
         * Validate a transactional change.
         *
         * This method is not permitted to modify any transactional `DBObjects` in the database,
         * (other than adding to a [DBLog], [DBQueue], or [DBProcessor]).
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
     *
     * The [DBLog], [DBQueue], and [DBProcessor] objects are considered transactional terminals, and
     * thus a rectifier may add to any of those. Since those objects may also have `Rectifiers` of
     * their own, it is important for the developer to understand some changes may have originated
     * in the user transaction, and some may have originated from the validation and rectification
     * phases.
     */
    static interface Rectifier<TxChange extends DBObject.TxChange>
        {
        /**
         * Rectify the contents of a transactional change to the containing DBObject.
         *
         * This method is not permitted to modify any transactional `DBObjects` in the database,
         * (other than adding to a [DBLog], [DBQueue], or [DBProcessor]).
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
     *
     * The [DBLog], [DBQueue], and [DBProcessor] objects are considered transactional terminals, and
     * thus none of these DBObjects can have a `Distributor` attached to it. This rule helps to
     * prevent infinite loops.
     */
    static interface Distributor<TxChange extends DBObject.TxChange>
        {
        /**
         * Distribute changes from the transactional change to the containing DBObject, to other
         * DBObjects.
         *
         * This method **must** not attempt to make changes to either (i) this DBObject, or (ii)
         * any transactional DBObject (other than a [DBLog], [DBQueue], or [DBProcessor]) that was
         * already modified withing the current transaction.
         *
         * (All Distributor instances that are indicated by a transaction are executed in a pass,
         * and during that pass, any number of Distributor instances may modify the same
         * previously-unmodified DBObjects, which in turn could trigger another Distributor pass,
         * during which any DBObjects modified previous to that pass must not be modified by the
         * Distributors in that new pass.)
         *
         * @param change  the change that is being distributed
         *
         * @return True iff the change is valid; False will cause the transaction to roll back
         */
        Boolean process(TxChange change);
        }
    }
