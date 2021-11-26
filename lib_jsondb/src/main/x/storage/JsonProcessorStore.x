import model.DBObjectInfo;

import json.Mapping;
import json.ObjectOutputStream;

import oodb.DBProcessor;
import oodb.DBProcessor.Pending;
import oodb.DBProcessor.Policy;
import oodb.DBProcessor.Schedule;
import oodb.DBTransaction.Priority;
import oodb.Transaction;
import oodb.Transaction.CommitResult;

import TxManager.NO_TX;


/**
 * Provides the low-level I/O for a DBProcessor, which represents the combination of a queue (with
 * scheduling) and a processor of the messages in the queue.
 */
@Concurrent
service JsonProcessorStore<Message extends immutable Const>
        extends ObjectStore(catalog, info)
        implements ProcessorStore<Message>
        incorporates KeyBasedStore<Message>
    {
    // ----- constructors --------------------------------------------------------------------------

    construct(Catalog          catalog,
              DBObjectInfo     info,
              Mapping<Message> messageMapping,
              )
        {
        construct ObjectStore(catalog, info);

        this.messageMapping = messageMapping;
        this.jsonSchema     = catalog.jsonSchema;
        this.clock          = catalog.clock;
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The transaction manager that this ObjectStore is being managed by. A reference to the
     * TxManager is lazily obtained from the Catalog service and then cached here, to avoid service
     * hopping just to get a reference to the TxManager every time that it is needed.
     */
    @Concurrent
    protected @Lazy Scheduler scheduler.calc()
        {
        return catalog.scheduler;
        }

    public/private Clock clock;

    /**
     * A cached reference to the JSON schema.
     */
    public/protected json.Schema jsonSchema;

    /**
     * The JSON Mapping for the processor message.
     */
    public/protected Mapping<Message> messageMapping;

    /**
     * The file owned by this LogStore for purpose of its data storage. The LogStore may
     * create, modify, and remove this file.
     */
    @Lazy public/private File dataFile.calc()
        {
        return dataDir.fileFor("processor.json");
        }

    /**
     * The maximum size of queue data to store in any one chunk file of the processor data.
     * TODO this setting should be configurable (need a "Prefs" API)
     */
    protected Int maxChunkSize = 100K;

    /**
     * Used internally within the in-memory ProcessorStore data structures to represent an
     * `unregister` request.
     */
    protected enum Unschedule {Cancel}

    typedef Int as Pid;
    typedef Pid | Pid[] | Unschedule as PidSet;

    /**
     * Used as a "singleton" empty map.
     */
    protected immutable OrderedMap<Message, PidSet> NoChanges =
            new SkiplistMap<Message, PidSet>().makeImmutable();

    @Override
    @Concurrent
    protected class Changes(Int writeId, Future<Int> pendingReadId)
        {
        /**
         * A map of inserted and updated scheduling or unscheduling requests.
         */
        OrderedMap<Message, PidSet>? mods;

        /**
         * @return a map used to view previously collected modifications, but not intended to be
         *         modified by the caller
         */
        OrderedMap<Message, PidSet> peekMods()
            {
            return mods ?: NoChanges;
            }

        /**
         * @return the read/write map used to collect modifications
         */
        OrderedMap<Message, PidSet> ensureMods()
            {
            return mods ?:
                {
                val map = new SkiplistMap<Message, PidSet>();
                mods = map;
                return map;
                };
            }

        /**
         * Tracks whether the transaction explicitly retrieved the pids via [pidListAt] API.
         *
         * Without that, the transaction has no way of interfering with other transactions.
         */
        Boolean askedForPids;

        /**
         * When the transaction is sealed (or after it is sealed, but before it commits), the
         * changes in the transaction are rendered for the transaction log, and for storage on disk.
         */
        Map<Message, String>? jsonEntries;
        }

    @Override
    protected SkiplistMap<Int, Changes> inFlight = new SkiplistMap();

    /**
     * Cached message/transaction/pid(s) triples. This is "the database", in the sense that this is
     * the same data that is stored on disk.
     */
    typedef SkiplistMap<Int, PidSet> History;
    protected Map<Message, History> history = new SkiplistMap();

    /**
     * A cache of schedules.
     */
    SkiplistMap<Pid, Schedule?> byPid = new SkiplistMap();

    /**
     * A record of how all persistent transactions are laid out on disk.
     */
    protected SkiplistMap<Int, Range<Int>> storageLayout = new SkiplistMap();

    /**
     * Uncommitted transaction information, held temporarily by prepareId. Basically, while a
     * transaction is being prepared, up until it is committed, the information from [Changes.mods]
     * is copied here, so that a view of the transaction as a separate set of changes is not lost;
     * that information is required by the [commit] processing.
     */
    protected SkiplistMap<Int, OrderedMap<Message, PidSet>> modsByTx = new SkiplistMap();

    /**
     * The ID of the latest known commit for this ObjectStore.
     */
    public/protected Int lastCommit = NO_TX;

    /**
     * True iff there are transactions on disk that could now be safely deleted.
     */
    public/protected Boolean cleanupPending = False;


    // ----- storage API exposed to the client -----------------------------------------------------

    @Override
    void schedule(Int txId, Message message, Schedule? when)
        {
        assert Changes tx := checkTx(txId, writing=True);

        Pid pid = scheduler.generatePid(this.id, when);

        byPid.put(pid, when);

        tx.ensureMods().process(message, e ->
            {
            if (e.exists)
                {
                PidSet pids = e.value;
                e.value = pids.is(Unschedule)
                        ? pid
                        : pids.is(Int)
                            ? [pids, pid]
                            : pids + pid;
                }
            else
                {
                e.value = pid;
                }
            });
        }

    @Override
    void unschedule(Int txId, Message message)
        {
        assert Changes tx := checkTx(txId, writing=True);

        Map<Message, PidSet> mods = tx.ensureMods();
        if (PidSet pids := mods.get(message))
            {
            clearSchedules(pids);
            mods.put(message, Cancel);
            }
        }

    @Override
    void unscheduleAll(Int txId)
        {
        TODO
        }

    @Override
    Int[] pidListAt(Int txId)
        {
        TODO
        }

    @Override
    Pending pending(Int txId, Int pid)
        {
        TODO
        }

    @Override
    Boolean isEnabled(Int txId)
        {
        TODO
        }

    @Override
    void setEnabled(Int txId, Boolean enable)
        {
        TODO
        }

    @Override
    void processCompleted(Int txId, Int pid, Range<DateTime> elapsed)
        {
        TODO
        }

    @Override
    void retryPending(Int txId, Int pid, Range<DateTime> elapsed, CommitResult | Exception result)
        {
        TODO
        }

    @Override
    void abandonPending(Int txId, Int pid, Range<DateTime> elapsed, CommitResult | Exception result)
        {
        TODO
        }


    // ----- transaction API exposed to TxManager --------------------------------------------------

    @Override
    PrepareResult prepare(Int writeId, Int prepareId)
        {
        // the transaction can be prepared if (a) no transaction has modified this value after the
        // read id, or (b) the "current" value is equal to the read id transaction's value
        assert Changes tx := checkTx(writeId);
        if (tx.peekMods().empty)
            {
            inFlight.remove(writeId);
            return CommittedNoChanges;
            }

        // obtain the transaction modifications (note: we already verified that modifications exist)
        OrderedMap<Message, PidSet> mods = tx.mods ?: assert;

        Boolean checkConflicts = tx.askedForPids;
        Int     readId         = tx.readId;
        if (checkConflicts && readId != prepareId - 1)
            {
            // interleaving transactions have occurred
            // TODO: what could be possible conflicts between different transactions?
            }

        for ((Message message, PidSet pids) : mods)
            {
            History messageHistory;
            if (messageHistory := history.get(message))
                {
                if (checkConflicts)
                    {
                    assert Int    latestTx := messageHistory.last();
                    assert PidSet latest   := messageHistory.get(latestTx);

                    TODO process the changes
                    }
                }
            else
                {
                messageHistory = new History();
                history.put(message, messageHistory);
                }
            messageHistory.put(prepareId, pids);
            }

        // store off transaction's mods
        modsByTx.put(prepareId, mods);

        // re-do the write transaction to point to the prepared transaction
        tx.readId   = prepareId;
        tx.prepared = True;
        tx.mods     = Null;
        return Prepared;
        }

    @Override
    String sealPrepare(Int writeId)
        {
        private String buildJsonTx(Map<Message, String> jsonEntries)
            {
            StringBuffer buf = new StringBuffer();
            buf.add('[');
            Loop: for (String jsonEntry : jsonEntries.values)
                {
                if (!Loop.first)
                    {
                    ", ".appendTo(buf);
                    }
                buf.append(jsonEntry);
                }
            buf.add(']');
            return buf.toString();
            }

        private void appendJsonSchedule(StringBuffer buf, Pid pid, Schedule? schedule)
            {
            // {"pid":[Int],"s":{"at":[DateTime],"daily":[Time],"repeat":[Duration],"policy":[Policy],"priority":[Priority]}}
            if (schedule == Null)
                {
                buf.append($"\{\"pid\":{pid}}");
                return;
                }

            DateTime? scheduledAt    = schedule.scheduledAt;
            Time?     scheduledDaily = schedule.scheduledDaily;
            Duration? repeatInterval = schedule.repeatInterval;
            Policy    repeatPolicy   = schedule.repeatPolicy;
            Priority  priority       = schedule.priority;

            buf.append($"\{\"pid\":{pid}, \"s\":\{");

            Boolean needComma = False;

            if (scheduledAt != Null)
                {
                buf.append("\"at\":\"");
                scheduledAt.appendTo(buf, True);
                buf.add('"');
                needComma = True;
                }
            if (scheduledDaily != Null)
                {
                if (needComma)
                    {
                    buf.add(',');
                    }
                buf.append("\"daily\":\"");
                scheduledDaily.appendTo(buf);
                buf.add('"');
                needComma = True;
                }
            if (repeatInterval != Null)
                {
                if (needComma)
                    {
                    buf.add(',');
                    }
                buf.append("\"repeat\":\"");
                repeatInterval.appendTo(buf);
                buf.add('"');
                needComma = True;
                }
            if (repeatPolicy != AllowOverlapping)
                {
                if (needComma)
                    {
                    buf.add(',');
                    }
                buf.append("\"repeat\":");
                repeatPolicy.ordinal.appendTo(buf);
                needComma = True;
                }
            if (priority != Normal)
                {
                if (needComma)
                    {
                    buf.add(',');
                    }
                buf.append("\"priority\":");
                priority.ordinal.appendTo(buf);
                needComma = True;
                }
            buf.add('}');
            }

        assert Changes tx := checkTx(writeId), tx.prepared;
        if (tx.sealed)
            {
            return buildJsonTx(tx.jsonEntries ?: assert);
            }

        assert Map<Message, PidSet> mods := modsByTx.get(tx.readId);

        HashMap<Message, String> jsonEntries = new HashMap();
        val                      worker      = tx.worker;

        for ((Message message, PidSet pids) : mods)
            {
            StringBuffer buf = new StringBuffer();

            String jsonMsg = worker.writeUsing(messageMapping, message);

            buf.append("{\"m\":").append(jsonMsg);

            if (!pids.is(Unschedule))
                {
                buf.append(", \"s\":[");
                if (pids.is(Int))
                    {
                    assert Schedule? schedule := byPid.get(pids);
                    appendJsonSchedule(buf, pids, schedule);
                    }
                else
                    {
                    Loop: for (Pid pid : pids)
                        {
                        assert Schedule? schedule := byPid.get(pid);
                        appendJsonSchedule(buf, pid, schedule);
                        if (!Loop.last)
                            {
                            buf.add(',');
                            }
                        }
                    }
                buf.add(']');
                }
            buf.add('}');

            jsonEntries.put(message, buf.toString());
            }

        tx.jsonEntries = jsonEntries;
        tx.sealed      = True;

        return buildJsonTx(jsonEntries);
        }

    @Override
    @Synchronized
    void commit(Int[] writeIds)
        {
        assert !writeIds.empty;

        if (cleanupPending)
            {
            TODO
            }

        Int lastCommitId = NO_TX;

        Map<String, StringBuffer> buffers = new HashMap();
        for (Int writeId : writeIds)
            {
            // because the same array of writeIds are sent to all of the potentially enlisted
            // ObjectStore instances, it is possible that this ObjectStore has no changes for this
            // transaction
            if (Changes tx := peekTx(writeId))
                {
                assert tx.prepared, tx.sealed, Map<Message, String> jsonEntries ?= tx.jsonEntries;

                Int prepareId = tx.readId;

                assert Map<Message, PidSet> mods := modsByTx.get(prepareId);

                // the "for" loop below is almost the same as at JsonMapStore
                for ((Message message, String jsonEntry) : jsonEntries)
                    {
                    StringBuffer buf = buffers.computeIfAbsent(nameForKey(message),
                            () -> new StringBuffer());

                    // build the String that will be appended to the disk file
                    // format is "{"tx":14, "c":[{"m":{...}, "s":{...}}, ...],"; comma is first (since we are appending)
                    if (buf.size == 0)
                        {
                        buf.append(",\n{\"tx\":")
                           .append(prepareId)
                           .append(", \"c\":[");
                        }
                    else
                        {
                        buf.append(", ");
                        }
                    buf.append(jsonEntry);

                    assert PidSet pids := mods.get(message);
                    clearSchedules(pids);

                    // remember the id of the last transaction that we process here
                    lastCommitId = prepareId;
                    }

                modsByTx.remove(prepareId);
                }
            }

        if (lastCommitId != NO_TX || cleanupPending)
            {
            // the "for" loop below is an exact copy of the corresponding part from JsonMapStore
            for ((String fileName, StringBuffer buf) : buffers)
                {
                // update where we will append the next record to, in terms of Chars (not bytes), so
                // that subsequent storageLayout information can be determined without expanding the
                // contents of the UTF-8 encoded file into Chars to calculate the "append location"

                // the JSON for entries data is inside an array, so "close" the array
                buf.append("]}\n]");

                File file = dataDir.fileFor(fileName);

                // write the changes to disk
                if (file.exists && !cleanupPending)
                    {
                    Int length = file.size;

                    assert length >= 6;

                    file.truncate(length-2)
                        .append(buf.toString().utf8());
                    }
                else
                    {
                    // replace the opening "," with an array begin "["
                    buf[0]         = '[';
                    file.contents  = buf.toString().utf8();
                    filesUsed++;
                    }

                // update the stats
                bytesUsed    += buf.size;
                lastModified = file.modified;
                }

            cleanupPending = False;

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
        if (Changes tx := peekTx(writeId))
            {
            if (tx.prepared)
                {
                Int prepareId = tx.readId;

                // the transaction is already sprinkled all over the history
                assert OrderedMap<Message, PidSet> mods := modsByTx.get(prepareId);
                for ((Message message, PidSet pids) : mods)
                    {
                    if (History messageHistory := history.get(message))
                        {
                        messageHistory.remove(prepareId);
                        }
                    clearSchedules(pids);
                    }

                modsByTx.remove(prepareId);
                }

            inFlight.remove(writeId);
            }
        }


    // ----- IO operations -------------------------------------------------------------------------

    @Override
    Iterator<File> findFiles()
        {
        return (dataFile.exists ? [dataFile] : []).iterator();
        }

    @Override
    void initializeEmpty()
        {
        assert model == Empty;
        assert !dataFile.exists;
        }

    @Override
    void loadInitial()
        {
        TODO
        }

    @Override
    void unload()
        {
        }


    // ----- internal ------------------------------------------------------------------------------

    protected void clearSchedules(PidSet pids)
        {
        if (pids.is(Int))
            {
            byPid.remove(pids);
            }
        else if (pids.is(Int[]))
            {
            byPid.keys.removeAll(pids);
            }
        }

    protected void rotateLog()
        {
        // TODO
//        String timestamp   = clock.now.toString(True);
//        String rotatedName = $"log_{timestamp}.json";
//
//        assert File rotatedFile := dataFile.renameTo(rotatedName);
        }
    }
