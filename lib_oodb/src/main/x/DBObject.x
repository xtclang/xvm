/**
 * The base interface for all "database objects", which are the representations of entities declared
 * in the database schema, within the context of a database connection. DBObjects encapsulate the
 * notions of data organization, functionality (both generic and custom), and persistent storage.
 * The [categories](DBCategory) of database object are: [DBSchema], [DBCounter], [DBValue], [DBMap],
 * [DBList], [DBQueue], [DBProcessor], and [DBLog].
 *
 * The implementations of these various `DBObject` interfaces are provided by a specific database
 * engine implementation. A database schema developer may also provide their own additional
 * functionality in addition to the methods and properties on any of their own defined DBObjects in
 * their schema, by extending the appropriate DBObject interface, or by creating a `mixin` into that
 * interface. This is the same manner in which database schemas are designed; in the following
 * example, we see the concept applied both to the root database schema and to a DBObject within
 * that schema:
 *
 *     module Bank
 *             incorporates oodb.Database           // <- this marks the module as a database module
 *         {
 *         package oodb import oodb.xtclang.org;    // <- this is the OODB API
 *
 *         // this defines a custom database schema
 *         interface BankSchema
 *                 extends oodb.RootSchema
 *             {
 *             // this declares a database object within the schema
 *             @RO Accounts accounts;
 *             }
 *
 *         // this "customizes" the DBMap interface for holding Account objects
 *         interface Accounts
 *                 extends oodb.DBMap<Int, Account>
 *             {
 *             void transfer(Int fromId, Int toId, Dec amount)
 *                 {
 *                 assert Account from := accounts.get(fromId), Account to := accounts.get(toId);
 *                 accounts.put(fromId, from.adjust(-amount));
 *                 accounts.put(toId, to.adjust(amount));
 *                 }
 *             }
 *
 *         // this is just a value that can be stored in the database
 *         const Account(Int id, Dec balance)
 *             {
 *             Account adjust(Dec amount)
 *                 {
 *                 return new Account(id, this.balance + amount);
 *                 }
 *             }
 *         }
 *
 * That is an entire database schema, although obviously a very simple one. There are restrictions
 * defined for the names used within a schema, but primarily those restrictions are:
 *
 * 1. Do not use any of the names that would conflict with the names in the `oodb` interfaces. This
 *    one is simple; for example, if you are defining a schema, then you cannot use any of the names
 *    on DBSchema (including any names on its base interface, `DBObject`, i.e. this interface.)
 *
 * 2. Names may not end with an underscore. This rule is arbitrary, but it allows the database
 *    implementation of these interfaces to shield its implementations details (from collision),
 *    by suffixing any potentially colliding names with an underscore.
 *
 * It is expected that database engine implementations will make use of code generation to form a
 * deployable database type system built around the application's database module. Consider the
 * schema example above:
 *
 * * The `accounts` property of the schema needs to have an implementation automatically provided by
 *   the database engine that will produce a reference to a DBMap object that implements the
 *   `Accounts` interface;
 *
 * * The implementation of the `Accounts` interface needs to ensure that a call to `transfer()`
 *   is automatically wrapped in a transaction, if one does not already exist.
 *
 * While not required, a database engine is expected to provide a richer set of capabilities than
 * are defined in the `oodb` API; support for schema evolution is one obvious example. The use of
 * code generation allows these capabilities to be baked into an application's database without
 * dramatic performance penalties.
 *
 * The result of this `oodb` design is that an application's database design can remain completely
 * independent of a specific database engine. The custom logic and design within an application's
 * database schema couples to the underlying database engine implementation only through the `oodb`
 * API, yet are still able to support those customizations as if they were built into the database
 * engine itself.
 */
interface DBObject
    {
    /**
     * The "current" Connection.
     */
    @RO Connection dbConnection;

    /**
     * The "current" Transaction, if any.
     */
    @RO Transaction? dbTransaction.get()
        {
        return dbConnection.transaction;
        }

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
     * Perform an arbitrarily coarse-grained test against the DBObject, and register the same test
     * (along with the result from evaluating it) as a precondition for the commit (i.e. a "prepare
     * constraint").
     *
     * This operation allows a test to be conducted on the previous state of the data in the
     * database, without explicitly enlisting _the exact copy of that state_. In other words, the
     * test is semi-blind, because only the result that the test returns to the caller must remain
     * invariant in order for the commit to succeed. Both the test and the corresponding result are
     * registered with the transaction as a necessary precondition for the commit, such that changes
     * to the database sufficient to change the result of the test, will result in the current
     * transaction failing to commit (and thus rolling back).
     *
     * This method represents an explicit mechanism to dynamically register a pre-condition for
     * commit, and as such can be thought of like a [Validator] -- except that a [Validator] is
     * declared in the schema and applies to every transaction (it is not dynamic), while this
     * requirement-test may be parameterized, so long as all of the arguments are immutable.
     *
     * When the commit occurs, the test is repeated against the last-committed transaction (and
     * **not** against the current transaction), which means that the commit is predicated on other
     * concurrent transactions not having invalidated the test requirement. Additionally, the commit
     * processing may omit the test altogether if no database changes have occurred concurrently
     * that would impact the result of the test.
     *
     * @param test  the requirement-test function to evaluate
     *
     * @return the `Result` of evaluating the function
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
     * A [DBLog], [DBQueue], or [DBProcessor] may both be appended to by other `Validators`, and may
     * also have `Validators` of its own, so it is important for the developer to understand some
     * changes being validated may have originated in the user transaction, and some may have
     * originated from other `Validators`.
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
         * including this `DBObject`, with the explicit exception being that a Validator may **add**
         * to a [DBLog], [DBQueue], or [DBProcessor].
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
     * A [DBLog], [DBQueue], or [DBProcessor] may both be appended to by other `Validators` and
     * `Rectifiers`, and may also have `Rectifiers` of its own, so it is important for the developer
     * to understand some changes being rectified may have originated in the user transaction, and
     * some may have originated from other `Validators` and `Rectifiers`.
     */
    static interface Rectifier<TxChange extends DBObject.TxChange>
        {
        /**
         * Rectify the contents of a transactional change to the containing DBObject.
         *
         * This method is not permitted to modify any transactional `DBObjects` in the database,
         * including this `DBObject`, with the explicit exception being that a Rectifier may **add**
         * to a [DBLog], [DBQueue], or [DBProcessor].
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
     * To prevent infinite loops, the [DBLog], [DBQueue], and [DBProcessor] objects are not
     * permitted to have a `Distributor` attached to them.
     */
    static interface Distributor<TxChange extends DBObject.TxChange>
        {
        /**
         * Distribute changes from the transactional change to this containing DBObject, to other
         * DBObjects.
         *
         * This method **must** not attempt to make changes to either (i) this DBObject, or (ii)
         * any transactional DBObject that was already modified withing the current transaction,
         * with the explicit exception being that a Distributor may **add** to a [DBLog], [DBQueue],
         * or [DBProcessor]..
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
