/**
 * A database transaction, as viewed from within the database.
 */
interface DBTransaction<Schema extends RootSchema>
    {
    /**
     * The root database schema. The difference between `DBObject`s obtained from this `Schema` and
     * `DBObject`s obtained from the [connection] property is that the DBObjects obtained from this
     * `Schema` are implicitly (and potentially explicitly) tied to this transaction, and should not
     * be used outside of the context of this transaction.
     */
    @RO Schema schema;

    enum Status {Active, Committing, Committed, RolledBack}

    /**
     * The transaction status.
     */
    @RO Status status;

    /**
     * When the transaction began.
     */
    @RO DateTime created;

    /**
     * When the transaction closed with a commit or roll-back.
     */
    @RO DateTime? retired;

    /**
     * The time consumed by the transaction, from creation to the beginning of the commit.
     */
    @RO Duration transactionTime;

    /**
     * The timeout specified for the transaction, if it has not been retired. (The value of this
     * property is undefined for retired transactions.)
     */
    @RO Duration? timeout;

    /**
     * The time consumed by the commit processing for the transaction.
     */
    @RO Duration commitTime;

    /**
     * Allows the transaction to be marked as not-commit-able. This property can be set to `True`,
     * but cannot be set to `False` once it has been set to `True`.
     */
    Boolean rollbackOnly;

    /**
     * The optional identifier provided when the transaction was created.
     */
    @RO UInt? id;

    /**
     * An optional descriptive name provided when the transaction was created.
     */
    @RO String? name;

    /**
     * Transaction priority, potentially used to determine resource allocations for active
     * transactions, and to order commits among a backlog of transactions.
     *
     * * `Idle` - Bottom-most priority, only guaranteed to execute if nothing else is executing.
     *
     * * `Low` - Lower than normal priority.
     *
     * * `Normal` - The default priority.
     *
     * * `High` - Higher than normal priority.
     *
     * * `System` - Highest priority; the `System` priority cannot be assigned to a transaction, but
     *   is instead automatically associated with transactions initiated by the database system
     *   itself
     */
    enum Priority {Idle, Low, Normal, High, System}

    /**
     * The priority of the transaction.
     */
    @RO Priority priority;

    /**
     * The number of times that the execution of the work represented by this transaction was
     * attempted previously without success, as indicated by the creator of this transaction.
     */
    @RO Int retryCount;

    /**
     * If this transaction was created as a side-effect of another transaction, then this property
     * provides that transaction.
     */
    @RO DBTransaction? origin;

    /**
     * The user that initiated this transaction, or `Null` if the transaction was initiated by the
     * database.
     */
    @RO DBUser? dbUser;

    /**
     * The contents of the transaction, which define the total net change represented by the
     * transaction.
     */
    @RO Map<String, DBObject.Change> contents;

    /**
     * Add a transaction _condition_ that indicates additional requirements that must be met for
     * the transaction to be able to commit.
     *
     * @param condition  the condition that can be evaluated to determine if the transaction will be
     *                   permitted to commit (`True` result) or forced to roll back (`False` result)
     *
     * @throws IllegalArgument  if a `function` is provided from which no corresponding [DBFunction]
     *                          object can be inferred
     */
    void addCondition(Condition condition);
    }
