import json.Doc;
import json.Lexer;
import json.Lexer.Token;
import json.Mapping;
import json.ObjectInputStream;
import json.Parser;

import model.DBObjectInfo;

import TxManager.NO_TX;


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
@Concurrent
service JsonValueStore<Value extends immutable Const>
        extends ObjectStore
        implements ValueStore<Value>
    {
    // ----- constructors --------------------------------------------------------------------------

    construct(Catalog          catalog,
              DBObjectInfo     info,
              Mapping<Value>   valueMapping,
              Value            initial,
              )
        {
        super(catalog, info);

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

    @Override
    @Concurrent
    protected class Changes(Int writeId, Future<Int> pendingReadId)
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
     * A record of how all persistent transactions are laid out on disk.
     */
    protected SkiplistMap<Int, Range<Int>> storageLayout = new SkiplistMap();

    /**
     * The append offset, measured in Chars, within the data file.
     */
    protected Int storageOffset = 0;

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
        updateReadStats();
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
        assert Changes tx := checkTx(txId, writing=True);
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
        if (latestId == readId)
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
    MergeResult mergePrepare(Int writeId, Int prepareId)
        {
        MergeResult result = CommittedNoChanges;
        String?     record = Null;

        if (Changes tx := peekTx(writeId))
            {
            assert !tx.sealed;

            result = NoMerge;
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
        tx.sealed = True;
        return json;
        }

    @Override
    @Synchronized
    void commit(Int[] writeIds)
        {
        assert !writeIds.empty;

        if (cleanupPending)
            {
            // rebuild the contents of the file, keeping only the transactions that we need
            (String json, storageLayout) = rebuildJson(dataFile.contents.unpackUtf8(), storageLayout);

            dataFile.contents = json.utf8();

            // we don't want the closing "\n]"
            storageOffset  = json.size - 2;
            cleanupPending = False;
            }

        StringBuffer buf    = new StringBuffer();
        Int          offset = storageOffset;

        Int lastCommitId = NO_TX;
        for (Int writeId : writeIds)
            {
            // because the same array of writeIds are sent to all of the potentially enlisted
            // ObjectStore instances, it is possible that this ObjectStore has no changes for this
            // transaction
            if (Changes tx := peekTx(writeId))
                {
                assert tx.prepared, tx.sealed, String json ?= tx.json;

                Int prepareId = tx.readId;

                // build the String that will be appended to the disk file
                // format is "{"tx":14, "value":{...}},"; comma is first (since we are appending)
                buf.append(",\n{\"tx\":")
                   .append(prepareId)
                   .append(", \"value\":");

                Int startPos = buf.size;
                buf.append(json);
                Int endPos   = buf.size;

                buf.add('}');

                // remember the id of the last transaction that we process here
                lastCommitId = prepareId;

                // remember the transaction location
                storageLayout.put(lastCommitId, offset+startPos ..< offset+endPos);
                }
            }

        if (lastCommitId != NO_TX)
            {
            // update where we will append the next record to, in terms of Chars (not bytes), so
            // that subsequent storageLayout information can be determined without expanding the
            // contents of the UTF-8 encoded file into Chars to calculate the "append location"
            storageOffset += buf.size;

            // the JSON value data is inside an array, so "close" the array
            buf.add('\n').add(']');

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
                buf[0]         = '[';
                file.contents  = buf.toString().utf8();
                }

            // remember which is the "current" value
            lastCommit = lastCommitId;

            // discard the transactional records
            for (Int writeId : writeIds)
                {
                inFlight.remove(writeId);
                }

            updateWriteStats();
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
        function void (Int) discard = txId ->
            {
            history.remove(txId);
            storageLayout.remove(txId);
            };

        if (processDiscarded(lastCommit, history.keys.iterator(), inUseTxIds.iterator(), discard))
            {
            if (force)
                {
                using (new SynchronizedSection())
                    {
                    (String json, storageLayout) =
                        rebuildJson(dataFile.contents.unpackUtf8(), storageLayout);

                    storageOffset += json.size - 2; // appends will occur before the closing "\n]"
                    dataFile.contents = json.utf8();
                    }
                cleanupPending = False;

                updateWriteStats();
                }
            else
                {
                cleanupPending = True;
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
        lastCommit = 0;
        }

    @Override
    void loadInitial()
        {
        File file = dataFile;
        storageLayout.clear();

        assert model == Small;
        assert file.exists;

        Int     closest     = NO_TX;
        Token[] valueTokens = new Token[];
        Int     desired     = txManager.lastCommitted;
        assert desired != NO_TX && desired > 0;

        Byte[] bytes      = file.contents;
        String jsonStr    = bytes.unpackUtf8();
        Parser fileParser = new Parser(jsonStr.toReader());
        Int    txCount    = 0;
        using (val arrayParser = fileParser.expectArray())
            {
            while (!arrayParser.eof)
                {
                ++txCount;
                 using (val objectParser = arrayParser.expectObject())
                    {
                    objectParser.expectKey("tx");
                    Int txId = objectParser.expectInt();
                    if (txId <= desired && (txId > closest || closest == NO_TX))
                        {
                        closest = txId;
                        objectParser.expectKey("value");
                        valueTokens.clear();
                        objectParser.skip(valueTokens);
                        }
                    }
                }
            }

        if (closest == NO_TX)
            {
            // the store doesn't have any valid data; nuke the file
            file.delete();
            model = Empty;
            initializeEmpty();
            return;
            }

        Value value;
        using (ObjectInputStream stream = new ObjectInputStream(jsonSchema, valueTokens.iterator()))
            {
            value = valueMapping.read(stream.ensureElementInput());
            }

        Range<Int> txLoc;
        if (txCount > 1)
            {
            // there's extra stuff in the file that we should get rid of now
            (jsonStr, txLoc)  = rebuildJson(closest, valueTokens);
            dataFile.contents = jsonStr.utf8();
            updateWriteStats();
            }
        else
            {
            Token first = valueTokens[0];
            Token last  = valueTokens[valueTokens.size-1];
            txLoc = first.start.offset ..< last.end.offset;
            }

        history.put(closest, value);
        storageLayout.put(closest, txLoc);
        storageOffset = jsonStr.size - 2; // append position is before the closing "\n]"
        lastCommit    = closest;
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
        if (super() && !dataFile.exists)
            {
            return True;
            }

        Boolean intact = False;
        val     byTx   = new SkiplistMap<Int, Range<Int>>();
        String jsonStr = "";
        try
            {
            jsonStr = dataFile.contents.unpackUtf8();
            using (val fileParser = new Parser(jsonStr.toReader()))
                {
                using (val arrayParser = fileParser.expectArray())
                    {
                    Int prevId = -1;
                    while (!arrayParser.eof)
                        {
                        using (val objectParser = arrayParser.expectObject())
                            {
                            objectParser.expectKey("tx");
                            Int txId = objectParser.expectInt();

                            // verify that the transaction id is legal
                            Boolean valid = True;
                            if (!isReadTx(txId))
                                {
                                log($|During deepScan() of DBValue "{info.path}", encountered the\
                                     | illegal transaction ID {txId}
                                     );
                                valid = False;
                                }
                            // verify that the id is unique
                            else if (byTx.contains(txId))
                                {
                                log($|During deepScan() of DBValue "{info.path}", encountered a\
                                     | duplicate transaction ID {txId}
                                     );
                                }
                            // verify that the id occurs in ascending order
                            else if (txId <= prevId)
                                {
                                log($|During deepScan() of DBValue "{info.path}", encountered an\
                                     | out-of-order transaction ID {txId}
                                     );
                                }
                            else
                                {
                                prevId = txId;
                                }

                            objectParser.expectKey("value");
                            (Token first, Token last) = objectParser.skipDoc();
                            byTx.put(txId, first.start.offset ..< last.end.offset);
                            }
                        }
                    }
                }

            // no problems parsing; make sure that the file doesn't appear to have been edited by
            // hand in a way that isn't fitting with the assumptions made by this store
            intact = jsonStr.startsWith("[\n{") && jsonStr.endsWith("}\n]");
            }
        catch (Exception e)
            {
            log($"During deepScan() of DBValue \"{info.path}\", encountered exception: {e}");
            }

        if (!intact && fix)
            {
            try
                {
                createBackup(dataFile, move=True);

                if (byTx.empty)
                    {
                    if (dataFile.exists)
                        {
                        dataFile.delete();
                        log($|During deepScan() of DBValue "{info.path}", no data could be recovered,\
                             | and the data file was deleted.
                             );
                        }
                    }
                else
                    {
                    // format all of the parsed data into a newly formatted file
                    String jsonFix = rebuildJson(jsonStr, byTx);
                    dataFile.contents = jsonFix.utf8();
                    updateWriteStats();
                    log($|During deepScan() of DBValue "{info.path}", {byTx.size} transactions were\
                         | recovered.
                         );
                    }

                updateWriteStats();
                return True;
                }
            catch (Exception e)
                {
                log($"During deepScan() of DBValue \"{info.path}\", encountered exception: {e}");
                }
            }

        return intact;
        }

    @Override
    @Synchronized
    Boolean recover(SkiplistMap<Int, Token[]> sealsByTxId)
        {
        assert Int     latest      := sealsByTxId.last();
        assert Token[] valueTokens := sealsByTxId.get(latest);

        dataFile.contents = rebuildJson(latest, valueTokens).utf8();
        return True;
        }

    /**
     * Create the JSON structure to be stored on disk to contain the latest value.
     */
    (String newJson, Range<Int> txLoc) rebuildJson(Int latest, Token[] valueTokens)
        {
        StringBuffer buf = new StringBuffer();
        buf.append("[\n{\"tx\":")
           .append(latest)
           .append(", \"value\":");

        Int startPos = buf.size;
        for (Token token : valueTokens)
            {
            token.appendTo(buf);
            }
        Int endPos = buf.size;

        buf.append("}\n]");

        return buf.toString(), startPos ..< endPos;
        }

    /**
     * Re-format the JSON structure that is stored on disk, to contain only the transactions
     * specified in the passed map, pulled from the passed JSON structure.
     */
    (String newJson, SkiplistMap<Int, Range<Int>> newLocationsByTx)
            rebuildJson(String json, SkiplistMap<Int, Range<Int>> byTx)
        {
        StringBuffer                 buf    = new StringBuffer(json.size);
        SkiplistMap<Int, Range<Int>> newLoc = new SkiplistMap();

        assert !byTx.empty;

        buf.add('[');
        for ((Int txId, Range<Int> txLoc) : byTx)
            {
            buf.append("\n{\"tx\":")
               .append(txId)
               .append(", \"value\":");

            Int startPos = buf.size;

            buf.append(json[txLoc]);

            newLoc.put(txId, startPos ..< buf.size);

            buf.add('}').add(',');
            }

        buf.truncate(-1).append("\n]");
        return buf.toString(), newLoc;
        }

    /**
     * Update the data modification statistics.
     */
    void updateWriteStats()
        {
        if (dataFile.exists)
            {
            filesUsed    = 1;
            bytesUsed    = dataFile.size;
            lastModified = dataFile.modified;
            }
        else
            {
            @Inject Clock clock;
            filesUsed    = 0;
            bytesUsed    = 0;
            lastModified = clock.now;
            }
        }

    @Override
    void unload()
        {
        inFlight.clear();
        history.clear();
        storageLayout.clear();
        }
    }
