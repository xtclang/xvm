/**
 * JSON-based database implementation of the OODB API, with storage in a FileSystem.
 *
 * Abbreviations:
 *
 * * TxM  - TxManager - "transaction manager" - not exposed outside of the database engine
 * * DBC  - Client.Connection - the API representation for a "connection"
 * * Tx   - Client.Transaction - the API representation for a "transaction"
 * * TxId - transaction id
 * * DBO  - DBObject (Client.DBObjectImpl and sub-classes) - "database object", such as each schema,
 *   map, etc.
 * * DSS  - ObjectStore and sub-classes - "data storage service" (services, one for each declared
 *   DBObject in the database)
 *
 * There are three forms of a transaction id ("TxId"):
 *
 * * A "base TxId", also called a "read TxId" is used when a transaction reads from the database;
 *   it reads the data that existed in the database as-of the commit of that transaction; the id is
 *   always an integer value `n`, where `n >= 0`. The TxId of `0` is the initial state of the
 *   database, the TxId of `1` is the state after the first transaction committed, and so on.
 *
 * * The "write TxId" is used to identify the destination for modifications that occur within an
 *   in-flight transaction. A TxId that can be written to is always negative, and specifically
 *   `n < -1`, since `-1` is used as an indicator value. Unlike positive TxId values, the magnitude
 *   of the negative TxId is not indicative of any particular order; it is simply a unique
 *   identifier. Each write-TxId has a corresponding read-TxId that serves as its base from which
 *   to overlay its changes; during the prepare phase, it isi possible for a write-TxId to have
 *   another write-TxId as its base, because trigger processing produces changes of its own.
 *
 * * A "prepare TxId" is when the changes corresponding to a particular "write TxId" are "flipped"
 *   to a pending commit. The pending changes from the transaction are re-associated with the next
 *   (positive) available transaction id, such as `18` if the last committed transaction was `17`.
 *   This is necessary to both place the data at the transaction level where it will be committed
 *   (assuming the commit succeeds), and to support the transaction trigger processing. Triggers
 *   execute against two views of the data; in the example above, the "before" view of the
 *   transaction would be a read-only connection with a base TxId of (e.g.) `17`, while the "after"
 *   view of the transaction would be a read-write transaction (modifiable by the trigger) with a
 *   base TxId of `18` (the prepare TxId) and a separate write TxId to allow the changes from the
 *   trigger to be captured separately (in order to support cascading triggers).
 *
 * * A "commit TxId" is when a "prepare TxId" is committed. The id stays the same, but the contents
 *   are now available to be used as a base TxId for a new transaction. (In the case that a
 *   transaction does not commit successfully, the contents of the prepare TxId are wiped clear,
 *   because the next transaction to attempt to commit will reuse the same "prepare TxId".)
 *
 * Service lay-out (including service virtual children):
 *
 * * [Catalog] - The database `Catalog` is the representation of the manageable database, which can
 *   be created, deleted, recovered, opened for processing, and closed. Basically, the `Catalog`
 *   object represents the intersection of the database engine, the database schema information, any
 *   custom code that was provided as part of the database schema module, and the physical storage
 *   for the data in the database (a directory, in the case of this JSON DB engine).
 *
 * * [Client] - This is the service that is available from inside an application container in order
 *   for the application code to query and modify the database. Each time the application requires
 *   a connection to be injected, a Client service is created, and an instance of its `Connection`
 *   virtual child (from the [client's conn property](Client.conn)); the reason for this is that the
 *   Client has a large number of items in its namespace, and both the Connection and the
 *   Transaction have to be merged with the database's custom root schema, so to minimize the chance
 *   of name collision, these classes are implemented as separate virtual children. Additionally,
 *   the DBOs within the database are all represented as virtual children of the client, so that
 *   the application can access the entirety of the database through a single shared service
 *   context, which is configured to disallow re-entrancy from the application. Virtual children of
 *   the `Client`:
 * * * Connection - this is the injected root of the database access into an application; by having
 *     it be part of the `Client` service, almost all of the database work related to the connection
 *     will be performed within the `Client` service, providing near-perfect accounting of the
 *     related times and resource costs.
 * * * Transaction - all queries and modification occur within a transactional context, and at most
 *     one transaction exists within a given connection at a given time. Like the `Connection`, the
 *     `Transaction` also provides the root schema of the database as part of its implementation.
 * * * DBObject - there are a number of implementations of the various `DBObject` forms, each as a
 *     virtual child of the `Client`, so that any call to these DBOs will be able to pick up the
 *     current transaction (or create an auto-commit transaction on the fly) within the context of
 *     the `Connection`
 * * * Worker - a virtual child that allows other services to delegate work back to the client
 *     service for CPU intensive tasks such as object serialization and deserialization (when other
 *     services call methods on the `Worker`, the work is actually performed within the `Client`
 *     service).
 *
 * * [TxManager] - an open Catalog has a single transaction manager that is used to serialize
 *   (order) the transactions as they are committed. Since the `TxManager` is likely to be heavily
 *   contended, it gets its own service.
 *
 * * [ObjectStore] - each DBO that is declared in the schema has a DSS, which is a sub-class
 *   of the `ObjectStore` service. The `ObjectStore` manages the reading and writing of JSON data
 *   from and to disk. By making each into its own service, I/O can theoretically be more highly
 *   concurrent, and caching can be managed close to the I/O implementaiton for the data being
 *   cached.
 *
 * Transactional management:
 *
 * * When a Tx begins (when it does its first read or write), the Tx registers itself with the TxM,
 *   and receives back its read TxId that the Tx will use to perform all transactional reads from
 *   the various DSS that are accessed during the transaction
 * * * At this point, the Tx is considered active by the TxM, and the TxM will track it, so that it
 *     can (e.g.) time the Tx out and roll it back
 * * * Transactional operations occur against DBOs. Each DBO is part of the client service, and
 *     performs its read and write operations using the corresponding DSS. AAs a result, these
 *     operations require the read TxId and write TxId to be passed.
 * * * Any operation on a DBO that has to read from or write to the database will automatically
 *     create a Tx (in auto-commit mode) if a Tx does not already exist; the system will behave as
 *     if a transaction was created immediately before the operation an committed immediately
 *     thereafter
 *
 * * There are two "long-tail" issues for the transaction manager:
 * * * Long-running read-only transactions, because they force the database to hold old versions of
 *     the data, when the database wants to "forget" that data as soon as possible to use less space
 * * * Long-running transactions that mutate data, because (in addition to the issues above) the
 *     transactional changes are likely to be invalidated by other transactions that complete in a
 *     shorter period of time
 *
 * * The app has a Tx (a Client.Transaction object), and at some point it calls “commit()”
 * * * This can occur concurrently across any number of different Tx objects
 * * * The assumption is that we have lots of CPU cores, so we want to maximize throughput by doing
 *     as much as possible concurrently
 *
 * * The Tx commit() delegates to the TxManager (aka TxM) to serialize (i.e. force ordering of) the
 *   operation
 * * * The TxM is a potential traffic jam as a result
 * * * If there are no changes in the Tx (no DBOs in the set of enlisted DBOs), then the commit
 *     succeeds immediately, and the TxM is notified of the completion
 * * * When Tx.commit() calls TxM, it passes a set of DBO ids with pending changes (or transactional
 *     conditions) to the TxM; it is assumed that most (99% of) DBOs will have no changes in a
 *     typical transaction, so we want to ignore those (and not pay in performance to ignore them)
 * * * * (note: there is a potential optimization for pipelining non-overlapping transactions)
 * * * The TxM wants to get this out of the way as fast as possible to avoid a traffic jam; it's
 *     just a manager (coordinator), so it's job is to get everyone else to do the actual work
 * * * After some basic checks (e.g. is the database still open, etc.), TxM takes the latest TxId)
 *     that it has committed (even if the commit has not completed to disk), and uses that value as
 *     the base TxId for **committing**, and the next value as the prepare TxId (`17` and `18` in
 *     the example above)
 * * * The Tx also knows its original read TxId, which may be far behind the base TxId for
 *     committing; the read TxId is the TxId that was the latest committed TxId when the Tx started
 * * * * (note: potential optimization if the the committing base TxId equals the read TxId)
 *
 * * TxM processing for the `commit()` request:
 * * * It has the read TxId, the base TxId for the commit, and the prepare TxId.
 * * * It has the set of DSS that may have any data changes and/or conditions; this is the initial
 *     DSS commit set.
 * * * It issues (concurrently) requests to those DSS to validate the contents of the write TxId
 *     against the prepare TxId, and to validate any related conditions; the result from each DSS
 *     is can-continue vs. cannot-continue, and there-is-data-change vs. there-is-no-data-change.
 * * * * If any data changed by the transaction was changed between the read TxId (exclusive) and
 *       the base TxId for the commit (inclusive), then the transaction is rolled back, unless the
 *       changes in the current transaction are explicitly composable (like a blind counter
 *       increment).
 * * * * If there are any transactional conditions, those are also evaluated at this point, before
 *       the triggers are evaluated.
 * * * The set of DSS with a there-is-data-change result is retained as the DSS commit set.
 * * * If the TxM cannot continue with the transaction (any DSS reporting cannot-continue), or if a
 *     failure happens in one of the subsequent steps, then the TxM notifies the entire DSS commit
 *     set that the transaction is abandoned, and they each clear their prepared data related to the
 *     prepare TxId, and the Tx is notified of the failure to commit and the implicit rollback.
 * * * Now the TxM loops to apply triggers; the loop is required to handle trigger cascades. It
 *     starts by creating a set of DSS to execute triggers on by copying the set of DSS that are
 *     enlisted in the transaction, and it knows what triggers would apply to that set of DSS (and
 *     in what predictable order).
 * * * * The TxM uses two different client views to implement the "before" and "after" trigger
 *       views. The first is a read-only view of the base TxId (the most recent commit level), and
 *       the second is a read/write transaction using the prepare TxId as its "base" and an
 *       initially empty transaction space specified by a write TxId, where the trigger effects will
 *       be captured.
 * * * * The TxM loops over the triggers to execute, based on the "trigger set" of DSS, and whether
 *       this is the first time through this loop (all triggers are executed the first time, but
 *       subsequent iterations only execute cascading triggers); the order of trigger execution must
 *       be predictable and stable. Each trigger is executed using the two (before and after) client
 *       views.
 * * * * Changes can occur during the trigger processing, so those changes (if any) are evaluated;
 *       note that changes may have occurred to DSS other than those for which triggers were being
 *       executed, so the "breadth" of the changes to the database may have increased.
 * * * * If a trigger fails, then the transaction is rolled back. It is important to log sufficient
 *        incriminating evidence to show the root cause.
 * * * * For each DSS with changes from the trigger, first those changes are **moved** from the
 *       trigger write TxId into the prepare TxId, and that DSS is added to the enlisted set of DSS,
 *       and if that DSS has any cascading triggers, then that DSS is added to the trigger set of
 *       DSS for the next iteration of this loop.
 * * * * If the cascade depth (the number of iterations in this loop) is too many, then the
 *       transaction has to be rolled back to avoid an infinite loop. It is important to log
 *       sufficient incriminating evidence to show the root cause.
 * * * * Once the TxM has prepared the transaction and finished the triggers, the TxM internally
 *       marks the transaction as committing, and may issue the final commit indication to each of
 *       the enlisted DSS. (The final commit may be deferred to the DSS to allow them to coalesce
 *       I/O operations.) This step is asynchronous; while the data is being treated as committed,
 *       the client is still blocking on its `commit()` call. Only after all of the DSS successfully
 *       complete their commit processing and I/O and return that result to the TxM does the TxM
 *       "complete" the `commit()` call from the client.
 * * * * * This design is pipe-lined. The TxM can start processing the next transaction as soon as
 *         it decides that the `commit()` of the previous one will complete, even before it issues
 *         the completion instruction to the various DSS, and long before it unblocks the client.
 */
module jsondb.xtclang.org
    {
    package oodb import oodb.xtclang.org;
    package json import json.xtclang.org;


    // ----- temporary helpers ---------------------------------------------------------------------

    static <Serializable> immutable Byte[] toBytes(Serializable value)
        {
        import ecstasy.io.*;
        val raw = new ByteArrayOutputStream();
        json.Schema.DEFAULT.createObjectOutput(new UTF8Writer(raw)).write(value);
        return raw.bytes.freeze(True);
        }

    static <Serializable> Serializable fromBytes(Type<Serializable> type, Byte[] bytes)
        {
        import ecstasy.io.*;
        return json.Schema.DEFAULT.createObjectInput(new UTF8Reader(new ByteArrayInputStream(bytes))).read();
        }

    static void dump(String desc, Object o)
        {
        @Inject Console console;
        String s = switch ()
            {
            case o.is(Byte[]): o.all(b -> b >= 32 && b <= 127 || new Char(b).isWhitespace())
                    ? new String(new Char[o.size](i -> new Char(o[i])))
                    : o.toString();

            default: o.toString();
            };

        console.println($"{desc}={s}");
        }
    }