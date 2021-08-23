import json.Doc;
import json.Lexer;
import json.Lexer.Token;
import json.Mapping;
import json.ObjectInputStream;
import json.Parser;

import model.DBObjectInfo;


/**
 * The disk storage implementation for a database "single value".
 *
 * The disk format follows this style:
 *
 *     [
 *     {"tx":14, "value":{...}},
 *     {"tx":17, "value":{...}},
 *     {"tx":18, "value":{...}}
 *     ]
 *
 * where the "{...}" part is also what sealPrepare() will have returned.
 */
service JsonValueStore<Value extends immutable Const>
        extends ObjectStore
        implements ValueStore<Value>
    {
    // ----- constructors --------------------------------------------------------------------------

    construct(Catalog          catalog,
              DBObjectInfo     info,
              Appender<String> errs,
              Mapping<Value>   valueMapping,
              Value            initial,
              )
        {
        construct ObjectStore(catalog, info, errs);

        this.jsonSchema   = catalog.jsonSchema;
        this.valueMapping = valueMapping;
        this.initial      = initial;
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * A cached reference to the JSON schema.
     */
    public/protected json.Schema jsonSchema;

    /**
     * The JSON Mapping for the singleton value.
     */
    public/protected Mapping<Value> valueMapping;

    /**
     * The initial singleton value. A singleton always has a value, even when it is newly created
     * (before it has any value stored); this property provides that initial value.
     */
    public/protected Value initial;

    /**
     * The file used to store the data for the DBValue.
     */
    @Lazy File dataFile.calc()
        {
        return dataDir.fileFor("value.json");
        }

    /**
     * An internal, mutable record of Changes for a specific transaction.
     */
    @Override
    protected class Changes(Int writeId, Int readId)
        {
        /**
         * Set to True when the transaction contains possible changes related to this ObjectStore.
         */
        Boolean modified;

        /**
         * The value within the transaction; only set if [modified] is set to `True`.
         */
        @Unassigned Value value;

        /**
         * When the transaction is sealed (or after it is sealed, but before it commits), the
         * changes to the value in the transaction are rendered for the transaction log, and for
         * storage on disk.
         */
        String? json;
        }

    @Override
    protected SkiplistMap<Int, Changes> inFlight = new SkiplistMap();

    /**
     * Cached transaction/value pairs. This is "the database", in the sense that this is the same
     * data that is stored on disk.
     */
    protected SkiplistMap<Int, Value> history = new SkiplistMap();

    /**
     * A record of how all persistent transactions are layed out on disk.
     */
    protected SkiplistMap<Int, Range<Int>> storageLayout = new SkiplistMap();

    import TxManager.NO_TX;

    /**
     * The ID of the latest known commit for this ObjectStore.
     */
    public/protected Int lastCommit = NO_TX;

    /**
     * True iff there are transactions on disk that could now be safely deleted.
     */
    public/protected Boolean cleanupPending = False;


    // ----- storage API exposed to the client -----------------------------------------------------

    /**
     * Obtain the singleton value as it existed immediately after the specified transaction finished
     * committing, or as it exists within the transaction (if it has not yet committed).
     *
     * @param txId  the "write" transaction identifier
     *
     * @return the value of the singleton as of the specified transaction
     */
    @Override
    Value load(Int txId)
        {
        if (Changes tx := checkTx(txId))
            {
            return currentValue(tx);
            }
        else
            {
            return latestValue(txId);
            }
        }

    /**
     * Modify the singleton as part of the specified transaction by replacing the value.
     *
     * @param txId   the "write" transaction identifier
     * @param value  the new value for the singleton
     */
    @Override
    void store(Int txId, Value value)
        {
        assert Changes tx := checkTx(txId, writing=True), !tx.sealed;
        tx.value    = value;
        tx.modified = True;
        }


    // ----- transaction API exposed to TxManager --------------------------------------------------

    @Override
    PrepareResult prepare(Int writeId, Int prepareId)
        {
        // the transaction can be prepared if (a) no transaction has modified this value after the
        // read id, or (b) the "current" value is equal to the read id transaction's value
        assert Changes tx := checkTx(writeId);
        if (!tx.modified)
            {
            inFlight.remove(writeId);
            return CommittedNoChanges;
            }

        Value value  = tx.value;
        Int   readId = tx.readId;
        Value prev   = latestValue(readId);

        assert Int latestId := history.last();
        if (latestId == tx.readId)
            {
            if (&value == &prev) // any change assumed significant, so use reference equality
                {
                inFlight.remove(writeId);
                return CommittedNoChanges;
                }
            }
        else
            {
            Value latest = latestValue();
            if (&latest != &prev) // any change assumed significant, so use reference equality
                {
                // the state that this transaction assumes as its starting point was altered, so the
                // transaction must roll back
                inFlight.remove(writeId);
                return FailedRolledBack;
                }

            if (&value == &latest) // any change assumed significant, so use reference equality
                {
                inFlight.remove(writeId);
                return CommittedNoChanges;
                }
            }

        // there is a change to this JsonValueStore in this transaction, and there has not been an
        // interleaving transaction that invalidates this transaction, so the prepare registers the
        // new value into the historical record for the transaction that is being prepared
        history.put(prepareId, tx.value);
        tx.readId   = prepareId;
        tx.prepared = True;
        tx.modified = False;
        return Prepared;
        }

    @Override
    MergeResult mergePrepare(Int writeId, Int prepareId, Boolean seal = False)
        {
        MergeResult result = NoMerge;

        if (Changes tx := peekTx(writeId))
            {
            assert !tx.sealed;
            if (tx.modified)
                {
                Value prev = latestValue(prepareId-1);
                if (tx.&value == &prev)
                    {
                    // the transaction is un-doing itself
                    inFlight.remove(writeId);
                    history.remove(prepareId);
                    result = CommittedNoChanges;
                    }
                else
                    {
                    history.put(prepareId, tx.value);
                    result = Merged;
                    }
                }

            tx.readId   = prepareId;// slide the readId forward to the point that we just prepared
            tx.prepared = True;     // remember that the changed the readId to the prepareId
            tx.modified = False;    // the "changes" no longer differs from the historical record
            tx.sealed   = seal;
            }

        return result;
        }

    @Override
    String sealPrepare(Int writeId)
        {
        assert Changes tx := checkTx(writeId), tx.prepared;
        if (tx.sealed)
            {
            return tx.json ?: assert;
            }

        String json = tx.worker.writeUsing(valueMapping, tx.value);
        tx.json   = json;
        tx.sealed = true;
        return json;
        }

    @Override
    void commit(Int[] writeIds)
        {
        assert !writeIds.empty;

        StringBuffer buf          = new StringBuffer();
        Int          lastCommitId = NO_TX;
        for (Int writeId : writeIds)
            {
            // because the same array of writeIds are sent to all of the potentially enlisted
            // ObjectStore instances, it is possible that this ObjectStore has no changes for this
            // transaction
            if (Changes tx := peekTx(writeId))
                {
                assert tx.prepared, tx.sealed, String json ?= tx.json;

                // build the String that will be appended to the disk file
                // format is "{"tx":14, "value":{...}},"; comma is first (since we are appending)
                buf.append(",\n{\"tx\":")
                  .append(tx.readId)
                  .append(", \"value\":")
                  .append(json)
                  .add   ('}');

                // remember the id of the last transaction that we process here
                lastCommitId = tx.readId;
                }
            }

        if (lastCommitId != NO_TX)
            {
            // the JSON value data is inside an array
            buf.append("\n]");

            // write the changes to disk
            File file = dataFile;
            if (file.exists)
                {
                Int length = file.size;

                // TODO right now this assumes that no manual edits have occurred; must cache "last
                //      update timestamp" and rebuild file if someone else changed it
                assert length >= 6;

                file.truncate(length-2)
                    .append(buf.toString().utf8());
                }
            else
                {
                // replace the opening "," with an array begin "["
                buf[0] = '[';
                file.contents = buf.toString().utf8();
                }

            // remember which is the "current" value
            lastCommit = lastCommitId;

            // discard the transactional records
            for (Int writeId : writeIds)
                {
                inFlight.remove(writeId);
                }
            }
        }

    @Override
    void rollback(Int writeId)
        {
        assert isWriteTx(writeId);

        // if this ObjectStore has any record of the transaction, then that record needs to be
        // discarded
        inFlight.processIfPresent(writeId, entry ->
            {
            // the transaction may point to a prepared copy of the transaction that has already been
            // placed in the "history" records
            Changes tx = entry.value;
            if (tx.prepared)
                {
                history.remove(tx.readId);
                }

            // dispose of the transaction record
            entry.delete();
            });
        }

    @Override
    void retainTx(OrderedSet<Int> inUseTxIds, Boolean force = False)
        {
        Iterator<Int> eachInUse = inUseTxIds.iterator();
        Int inUseId;
        if (inUseId := eachInUse.next())
            {
            }
        else
            {
            assert inUseTxIds.empty;
            inUseId = lastCommit;
            }

        Iterator<Int> eachPresent = history.keys.iterator();
        assert Int presentId := eachPresent.next();
        if (presentId == lastCommit)
            {
            return;
            }

        Boolean discarded = False;
        Loop: while (True)
            {
            Boolean loadNextPresent = False;
            Boolean loadNextInUse   = False;

            switch (presentId <=> inUseId)
                {
                case Lesser:
                    // discard the transaction
                    history.remove(presentId);
                    discarded = True;

                    // advance to the next transaction in our history log
                    loadNextPresent = True;
                    break;

                case Equal:
                    // the current one that we're examining in our history is still in use; advance
                    // to the next of each list
                    loadNextInUse   = True;
                    loadNextPresent = True;
                    break;

                case Greater:
                    // determine the next transaction that we're being instructed to keep
                    loadNextInUse = True;
                    break;
                }

            if (loadNextInUse)
                {
                if (inUseId := eachInUse.next())
                    {
                    if (inUseId > lastCommit)
                        {
                        // we don't have any transactions in this range
                        inUseId = lastCommit;
                        }
                    }
                else if (inUseId >= lastCommit)
                    {
                    // we already moved past our last transaction; we're done
                    break Loop;
                    }
                else
                    {
                    // we're *almost* done; pretend that the only other transaction that we need to
                    // keep is our "current" one
                    inUseId = lastCommit;
                    }
                }

            if (loadNextPresent)
                {
                if (presentId := eachPresent.next())
                    {
                    if (presentId >= lastCommit)
                        {
                        // we need to keep this one (it's our "current" history), so we're done
                        break Loop;
                        }
                    }
                else
                    {
                    // no more transactions to evaluate; we're done
                    break Loop;
                    }
                }
            }

        if (discarded)
            {
            cleanupPending = True;
            if (force)
                {
                cleanup();
                }
            }
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Obtain the update-to-date value from the transaction.
     *
     * @param tx  the transaction's Changes record
     *
     * @return the current value
     */
    protected Value currentValue(Changes tx)
        {
        return tx.modified
                ? tx.value
                : latestValue(tx.readId);
        }

    /**
     * Obtain the original value from when the transaction began.
     *
     * @param readId  the transaction id to read from
     *
     * @return the previous value
     */
    protected Value latestValue(Int readId)
        {
        assert readId := history.floor(readId);
        assert Value value := history.get(readId);
        return value;
        }

    /**
     * Obtain the latest committed value.
     *
     * @return the latest value
     */
    protected Value latestValue()
        {
        assert Int   readId := history.last();
        assert Value latest := history.get(readId);
        return latest;
        }


    // ----- IO operations -------------------------------------------------------------------------

    @Override
    void initializeEmpty()
        {
        assert model == Empty;
        assert !dataFile.exists;

        history.put(0, initial);
        }

    @Override
    void loadInitial()
        {
        File file = dataFile;
        storageLayout.clear();

        assert model == Small;
        assert file.exists;

        Int         closest = NO_TX;
        Range<Int>  txLoc   = -1..0; // some illegal value
        Int         desired = txManager.lastClosedId;
        assert desired != NO_TX && desired > 0;

        Byte[] bytes      = file.contents;
        String jsonStr    = bytes.unpackString();
        Parser fileParser = new Parser(jsonStr.toReader());
        using (val arrayParser = fileParser.expectArray())
            {
            while (!arrayParser.eof)
                {
                using (val objectParser = arrayParser.expectObject())
                    {
                    objectParser.expectKey("tx");
                    Int txId = objectParser.expectInt();
                    if (txId <= desired && (txId > closest || closest == NO_TX))
                        {
                        closest = txId;
                        objectParser.expectKey("value");
                        (Token first, Token last) = objectParser.skipDoc();
                        txLoc = [first.start.offset .. last.end.offset);
                        }
                    }
                }
            }
        assert closest != NO_TX;

        String jsonRecord = jsonStr.slice(txLoc);
        Value  value;
        using (ObjectInputStream stream = new ObjectInputStream(jsonSchema, jsonRecord.toReader()))
            {
            value = valueMapping.read(stream.ensureElementInput());
            }

        history.put(closest, value);
        storageLayout.put(closest, txLoc);
        }

    @Override
    Iterator<File> findFiles()
        {
        File file = dataFile;
        return (file.exists ? [file] : []).iterator();
        }

    @Override
    Boolean deepScan(Boolean fix = True)
        {
        // TODO
        return super();
        }

    /**
     * Clean up old transactions on the disk.
     */
    void cleanup() // REVIEW should this be here or on the base ObjectStore class?
        {
        TODO
        }

    @Override
    void unload()
        {
        inFlight.clear();
        history.clear();
        storageLayout.clear();
        }
    }
