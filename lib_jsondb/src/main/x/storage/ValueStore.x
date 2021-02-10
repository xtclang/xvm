import json.Mapping;
import json.Lexer;
import json.Lexer.Token;

import model.DBObjectInfo;


/**
 * The disk storage implementation for a database "single value".
 */
service ValueStore<Value extends immutable Const>
        extends ObjectStore
    {
    construct(Catalog          catalog,
              DBObjectInfo     info,
              Appender<String> errs,
              Mapping<Value>   valueMapping)
        {
        construct ObjectStore(catalog, info, errs);

        this.valueMapping = valueMapping;
        }

    /**
     * The JSON Mapping for the singleton value.
     */
    public/protected Mapping<Value> valueMapping;

    /**
     * The initial singleton value. A singleton always has a value, even when it is newly created
     * (before it has any value stored); this property provides that initial value.
     */
    Value initial.get()
        {
        TODO
        }

    /**
     * Obtain the singleton value as it existed immediately after the specified transaction finished
     * committing.
     *
     * @param txId  specifies the transaction identifier to use to determine the point-in-time data
     *              stored in the database, as if the value of the singleton were read immediately
     *              after that specified transaction had committed
     *
     * @return True iff the singleton had a value at the completion of the specified transaction
     * @return (conditional) the value of the singleton at the completion of the specified
     *         transaction
     */
    conditional (Token[], Value?) load(Int txId)
        {
        TODO
        }

    /**
     * Modify the singleton as part of the specified transaction by replacing the value.
     *
     * @param txId   the transaction being committed
     * @param value  the new value for the singleton
     */
    void store(Int txId, Value value)
        {
        TODO
        }
    }
