import oodb.DBLog;
import oodb.DBTransaction;

/**
 * A representation of the database's transaction log.
 */
class TransactionLog
        extends SystemObject
        implements DBLog<DBTransaction>
    {
    construct(SystemSchema:protected parent)
        {
        construct SystemObject(parent.catalog, parent, "transactions");
        }

    @Override
    @RO DBCategory dbCategory.get()
        {
        return DBLog;
        }

    // TODO
    }