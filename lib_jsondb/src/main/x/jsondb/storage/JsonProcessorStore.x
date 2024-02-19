import model.DboInfo;

import json.Lexer.Token;
import json.Parser;
import json.Mapping;
import json.ObjectInputStream;

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
 *
 * The disk format follows this style: (possible multiple line per transaction)
 *
 *     [
 *     {"tx":14, "m":{M1}, "s":[{"pid":N, ...}]},
 *     {"tx":17, "m":{M2}, "p":[N, ...]},
 *     {"tx":17, "m":{M2}, "s":[{"pid":N}, ...]}]},
 *     {"tx":18, "m":{M3}, "s":[{"pid":N, ...}, ...]},
 *     {"tx":18, "m":{M4}, "s":[{"pid":N, ...}}]}
 *     ]
 *
 * where a "s" (schedule) indicates a scheduling request (empty pids array means "unschedule")
 * and "p" (process) indicates an executed * processor action.
 */
@Concurrent
service JsonProcessorStore<Message extends immutable Const>
        extends ObjectStore(catalog, info)
        implements ProcessorStore<Message>
        incorporates KeyBasedStore<Message> {
    // ----- constructors --------------------------------------------------------------------------

    construct(Catalog          catalog,
              DboInfo     info,
              Mapping<Message> messageMapping,
              ) {
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
    protected @Lazy Scheduler scheduler.calc() {
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

    @Concurrent
    @Override
    protected class Changes {
        @Override
        construct(Int writeId, Future<Int> pendingReadId) {
            super(writeId, pendingReadId);
        }

        /**
         * A map of inserted and updated processing requests.
         */
        OrderedMap<Message, PidSet>? processMods;

        /**
         * @return a map used to view previously collected modifications, but not intended to be
         *         modified by the caller
         */
        OrderedMap<Message, PidSet> peekProcessMods() {
            return processMods ?: NoChanges;
        }

        /**
         * @return the read/write map used to collect processing modifications
         */
        OrderedMap<Message, PidSet> ensureProcessMods() {
            return processMods ?: {
                val map = new SkiplistMap<Message, PidSet>();
                processMods = map;
                return map;
            };
        }

        /**
         * A map of inserted and updated scheduling or unscheduling requests.
         */
        OrderedMap<Message, PidSet>? scheduleMods;

        /**
         * @return a map used to view previously collected modifications, but not intended to be
         *         modified by the caller
         */
        OrderedMap<Message, PidSet> peekScheduleMods() {
            return scheduleMods ?: NoChanges;
        }

        /**
         * @return the read/write map used to collect scheduling modifications
         */
        OrderedMap<Message, PidSet> ensureScheduleMods() {
            return scheduleMods ?: {
                val map = new SkiplistMap<Message, PidSet>();
                scheduleMods = map;
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
        Map<Message, String>? jsonMessages;
        Map<Message, String>? jsonProcessedEntries;
        Map<Message, String>? jsonScheduledEntries;
    }

    @Override
    protected SkiplistMap<Int, Changes> inFlight = new SkiplistMap();

    /**
     * Cached message/transaction/pid(s) triples. This is "the database", in the sense that this is
     * the same data that is stored on disk.
     */
    typedef SkiplistMap<Int, PidSet> as History;
    protected Map<Message, History> scheduleHistory = new SkiplistMap();

    /**
     * A cache of schedules.
     */
    SkiplistMap<Pid, Schedule?> scheduleByPid = new SkiplistMap();

    /**
     * Uncommitted transaction information, held temporarily by prepareId. Basically, while a
     * transaction is being prepared, up until it is committed, the information from [Changes]
     * is copied here, so that a view of the transaction as a separate set of changes is not lost;
     * that information is required by the [commit] processing.
     */
    protected SkiplistMap<Int, OrderedMap<Message, PidSet>> processModsByTx  = new SkiplistMap();
    protected SkiplistMap<Int, OrderedMap<Message, PidSet>> scheduleModsByTx = new SkiplistMap();

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
    void schedule(Int txId, Message message, Schedule? when) {
        assert Changes tx := checkTx(txId, writing=True);

        Pid pid = scheduler.generatePid(this.id, when);

        scheduleByPid.put(pid, when);

        tx.ensureScheduleMods().process(message, e -> {
            e.value = e.exists ? addPid(e.value, pid) : pid;
        });
    }

    @Override
    void unschedule(Int txId, Message message) {
        assert Changes tx := checkTx(txId, writing=True);

        Map<Message, PidSet> mods = tx.ensureScheduleMods();
        if (PidSet pids := mods.get(message)) {
            clearSchedules(pids);
            mods.put(message, Cancel);
        }
    }

    @Override
    void unscheduleAll(Int txId) {
        TODO
    }

    @Override
    Int[] pidListAt(Int txId) {
        TODO
    }

    @Override
    Pending pending(Int txId, Int pid) {
        TODO
    }

    @Override
    Boolean isEnabled(Int txId) {
        TODO
    }

    @Override
    void setEnabled(Int txId, Boolean enable) {
        TODO
    }

    @Override
    void processCompleted(Int txId, Message message, Int pid, Range<Time> elapsed) {
        assert Changes tx := checkTx(txId, writing=True);

        tx.ensureProcessMods().process(message, e -> {
            if (e.exists) {
                PidSet pids = e.value;
                assert !pids.is(Unschedule);
                e.value = addPid(pids, pid);
            } else {
                e.value = pid;
            }
        });
    }

    @Override
    void retryPending(Int txId, Message message, Int pid, Range<Time> elapsed, CommitResult | Exception result) {
        TODO
    }

    @Override
    void abandonPending(Int txId, Message message, Int pid, Range<Time> elapsed, CommitResult | Exception result) {
        TODO
    }


    // ----- transaction API exposed to TxManager --------------------------------------------------

    @Override
    PrepareResult prepare(Int writeId, Int prepareId) {
        // the transaction can be prepared if (a) no transaction has modified this value after the
        // read id, or (b) the "current" value is equal to the read id transaction's value
        assert Changes tx := checkTx(writeId);
        Boolean processed = !tx.peekProcessMods().empty;
        Boolean scheduled = !tx.peekScheduleMods().empty;

        if (!processed && !scheduled) {
            inFlight.remove(writeId);
            return CommittedNoChanges;
        }

        if (processed) {
            // obtain and store the transaction modifications (note: we already verified that
            // modifications exist)
            OrderedMap<Message, PidSet> processMods = tx.processMods ?: assert;
            processModsByTx.put(prepareId, processMods);
        }

        if (scheduled) {
            // obtain the transaction modifications (note: we already verified that modifications exist)
            OrderedMap<Message, PidSet> scheduleMods = tx.scheduleMods ?: assert;

            Boolean checkConflicts = tx.askedForPids;
            Int     readId         = tx.readId;
            if (checkConflicts && readId != prepareId - 1) {
                // interleaving transactions have occurred
                // TODO: what could be possible conflicts between different transactions?
            }

            for ((Message message, PidSet pids) : scheduleMods) {
                History messageHistory;
                if (messageHistory := scheduleHistory.get(message)) {
                    if (checkConflicts) {
                        assert Int    latestTx := messageHistory.last();
                        assert PidSet latest   := messageHistory.get(latestTx);

                        TODO process the changes
                    }
                } else {
                    messageHistory = new History();
                    scheduleHistory.put(message, messageHistory);
                }
                messageHistory.put(prepareId, pids);
            }

            // store off transaction's mods
            scheduleModsByTx.put(prepareId, scheduleMods);
        }

        // re-do the write transaction to point to the prepared transaction
        tx.readId       = prepareId;
        tx.prepared     = True;
        tx.processMods  = Null;
        tx.scheduleMods = Null;
        return Prepared;
    }

    @Override
    MergeResult mergePrepare(Int txId, Int prepareId) {
        MergeResult result;
        TODO
    }

    @Override
    String sealPrepare(Int writeId) {
        private String buildJsonTx(Map<Message, String> jsonMessages,
                                   Map<Message, String> jsonProcessedEntries,
                                   Map<Message, String> jsonScheduledEntries) {
            StringBuffer buf = new StringBuffer();
            buf.add('[');

            for ((Message message, String jsonProcessed) : jsonProcessedEntries) {
                assert String jsonMsg := jsonMessages.get(message);
                buf.append("{\"m\":").append(jsonMsg)
                   .add(',').add(' ')
                   .append(jsonProcessed);

                if (String jsonScheduled := jsonScheduledEntries.get(message)) {
                    buf.add(',').add(' ')
                       .append(jsonScheduled);
                }
                buf.add('}').add(',');
            }

            for ((Message message, String jsonScheduled) : jsonScheduledEntries) {
                if (jsonProcessedEntries.contains(message)) {
                    continue;
                }
                assert String jsonMsg := jsonMessages.get(message);

                buf.append("{\"m\":").append(jsonMsg)
                   .add(',').add(' ')
                   .append(jsonScheduled)
                   .add('}').add(',');
            }

            return buf.truncate(-1).add(']').toString();
        }

        private String buildJsonProcessed(PidSet pids) {
            assert !pids.is(Unschedule);

            StringBuffer buf = new StringBuffer();
            buf.append("\"p\":[");
            if (pids.is(Int)) {
                pids.appendTo(buf);
            } else {
                Loop: for (Pid pid : pids) {
                    pid.appendTo(buf);
                    if (!Loop.last) {
                        buf.add(',');
                    }
                }
            }
            buf.add(']');
            return buf.toString();
        }

        assert Changes tx := checkTx(writeId), tx.prepared;
        if (tx.sealed) {
            return buildJsonTx(tx.jsonMessages         ?: assert,
                               tx.jsonProcessedEntries ?: assert,
                               tx.jsonScheduledEntries ?: assert);
        }

        Int readId = tx.readId;

        Map<Message, PidSet> processMods  = processModsByTx .getOrDefault(readId, NoChanges);
        Map<Message, PidSet> scheduleMods = scheduleModsByTx.getOrDefault(readId, NoChanges);

        assert !processMods.empty || !scheduleMods.empty;

        HashMap<Message, String> jsonMessages         = new HashMap();
        HashMap<Message, String> jsonProcessedEntries = new HashMap();
        HashMap<Message, String> jsonScheduledEntries = new HashMap();
        val                      worker               = tx.worker;

        for ((Message message, PidSet processPids) : processMods) {
            jsonMessages.computeIfAbsent(message,
                    () -> worker.writeUsing(messageMapping, message));

            jsonProcessedEntries.put(message, buildJsonProcessed(processPids));
        }

        for ((Message message, PidSet schedulePids) : scheduleMods) {
            jsonMessages.computeIfAbsent(message,
                    () -> worker.writeUsing(messageMapping, message));

            jsonScheduledEntries.put(message, buildJsonScheduled(schedulePids));
        }

        tx.jsonMessages         = jsonMessages;
        tx.jsonProcessedEntries = jsonProcessedEntries;
        tx.jsonScheduledEntries = jsonScheduledEntries;
        tx.sealed               = True;

        return buildJsonTx(jsonMessages, jsonProcessedEntries, jsonScheduledEntries);
    }

    @Override
    @Synchronized
    void commit(Int[] writeIds) {
        assert !writeIds.empty;

        if (cleanupPending) {
            TODO
        }

        Int lastCommitId = NO_TX;

        Map<String, StringBuffer> buffers = new HashMap();
        for (Int writeId : writeIds) {
            // because the same array of writeIds are sent to all of the potentially enlisted
            // ObjectStore instances, it is possible that this ObjectStore has no changes for this
            // transaction
            if (Changes tx := peekTx(writeId)) {
                assert tx.prepared, tx.sealed,
                    Map<Message, String> jsonMessages         ?= tx.jsonMessages,
                    Map<Message, String> jsonProcessedEntries ?= tx.jsonProcessedEntries,
                    Map<Message, String> jsonScheduledEntries ?= tx.jsonScheduledEntries;

                Int prepareId = tx.readId;

                for ((Message message, String jsonEntry) : jsonProcessedEntries) {
                    StringBuffer buf = buffers.computeIfAbsent(nameForKey(message),
                            () -> new StringBuffer());

                    // build the String that will be appended to the disk file; format is
                    //      {"tx":14, "m":{...}, "p":[...]}
                    assert String jsonMsg := jsonMessages.get(message);
                    appendJsonEntry(buf, prepareId, jsonMsg, jsonEntry);
                }

                 // clean up processed schedules (they are no longer "pending")
                if (Map<Message, PidSet> processMods := processModsByTx.get(prepareId)) {
                    for (PidSet pids : processMods.values) {
                        clearSchedules(pids);
                    }
                }

                for ((Message message, String jsonEntry) : jsonScheduledEntries) {
                    StringBuffer buf = buffers.computeIfAbsent(nameForKey(message),
                            () -> new StringBuffer());

                    // build the String that will be appended to the disk file; format is
                    //      {"tx":14, "m":{...}, "s":[...]}
                    assert String jsonMsg := jsonMessages.get(message);
                    appendJsonEntry(buf, prepareId, jsonMsg, jsonEntry);

                    // register/unregister requests with the scheduler
                    if (Map<Message, PidSet> scheduleMods := scheduleModsByTx.get(prepareId)) {
                        assert PidSet pids := scheduleMods.get(message);

                        if (pids.is(Int)) {
                            assert Schedule? schedule := scheduleByPid.get(pids);
                            scheduler.registerPid(id, pids, clock.now,
                                    new Pending(path, message, schedule));
                        } else if (pids.is(Int[])) {
                            for (Pid pid : pids) {
                                assert Schedule? schedule := scheduleByPid.get(pid);
                                scheduler.registerPid(id, pid, clock.now,
                                        new Pending(path, message, schedule));
                            }
                        } else { // Cancel
                            if (History messageHistory := scheduleHistory.get(message)) {
                                assert PidSet prevPids := messageHistory.get(prepareId);

                                if (prevPids.is(Int)) {
                                    scheduler.unregisterPid(id, prevPids);
                                } else if (prevPids.is(Int[])) {
                                    scheduler.unregisterPids(id, prevPids);
                                }
                            }
                        }
                    }
                }

                processModsByTx .remove(prepareId);
                scheduleModsByTx.remove(prepareId);

                // remember the id of the last transaction that we process here
                lastCommitId = prepareId;
            }
        }

        if (lastCommitId != NO_TX || cleanupPending) {
            // the "for" loop below is an exact copy of the corresponding part from JsonMapStore
            for ((String fileName, StringBuffer buf) : buffers) {
                // the JSON for entries data is inside an array, so "close" the array
                buf.add('\n').add(']');

                File file = dataDir.fileFor(fileName);

                // write the changes to disk
                if (file.exists && !cleanupPending) {
                    Int length = file.size;

                    assert length >= 6;

                    file.truncate(length-2)
                        .append(buf.toString().utf8());
                } else {
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
        }

        // discard the transactional records
        for (Int writeId : writeIds) {
            inFlight.remove(writeId);
        }
    }

    @Override
    void rollback(Int writeId) {
        if (Changes tx := peekTx(writeId)) {
            if (tx.prepared) {
                Int prepareId = tx.readId;

                // the transaction is already sprinkled all over the history
                if (OrderedMap<Message, PidSet> scheduleMods := scheduleModsByTx.get(prepareId)) {
                    for ((Message message, PidSet pids) : scheduleMods) {
                        if (History messageHistory := scheduleHistory.get(message)) {
                            messageHistory.remove(prepareId);
                        }
                        clearSchedules(pids);
                    }
                }

                processModsByTx .remove(prepareId);
                scheduleModsByTx.remove(prepareId);
            }

            inFlight.remove(writeId);
        }
    }

    @Override
    void retainTx(OrderedSet<Int> inUseTxIds, Boolean force = False) {
        // TODO
    }


    // ----- IO operations -------------------------------------------------------------------------

    @Override
    void initializeEmpty() {
        assert model == Empty;
        lastCommit = 0;
    }

    @Override
    void loadInitial() {
        Int desired = txManager.lastCommitted;
        assert desired != NO_TX && desired > 0;

        Int totalBytes = 0;
        Int totalFiles = 0;

        for (File file : dataDir.files()) {
            String  fileName   = file.name;
            Byte[]  bytes      = file.contents;
            String  jsonStr    = bytes.unpackUtf8();
            Boolean rebuild    = False;
            Parser  fileParser = new Parser(jsonStr.toReader());

            Map<Pid, Int>     closestTx       = new HashMap();
            Map<Pid, Token[]> closestSchedule = new HashMap();

            Map<Message, Range<Int>> messageLoc         = new HashMap();
            Map<Message, PidSet>     scheduledByMessage = new HashMap();

            SkiplistMap<Int, Message[]> messagesByTx = new SkiplistMap();

            totalFiles++;
            totalBytes += bytes.size;

            using (val arrayParser = fileParser.expectArray()) {
                while (!arrayParser.eof) {
                    using (val changeParser = arrayParser.expectObject()) {
                        changeParser.expectKey("tx");

                        Int txId = changeParser.expectInt();
                        if (txId > desired) {
                            // this should not be happening
                            rebuild = True;
                            continue;
                        }

                        Message message;

                        changeParser.expectKey("m");
                        Token[] messageTokens = changeParser.skip(new Token[]);
                        using (ObjectInputStream stream =
                                new ObjectInputStream(jsonSchema, messageTokens.iterator())) {
                            message = messageMapping.read(stream.ensureElementInput());
                        }

                        Int startPos = messageTokens[0].start.offset;
                        Int endPos   = messageTokens[messageTokens.size-1].end.offset;

                        messageLoc.putIfAbsent(message, startPos ..< endPos);
                        fileNames.putIfAbsent(message, fileName);

                        messagesByTx.process(txId, e -> {
                            if (e.exists) {
                                if (!e.value.contains(message)) {
                                    e.value += message;
                                }
                            } else {
                                e.value = [message];
                            }
                        });

                        if (changeParser.matchKey("p")) { // processed
                            using (val pidParser = changeParser.expectArray()) {
                                while (!pidParser.eof) {
                                    Pid pid = pidParser.expectInt();

                                    closestTx.remove(pid);
                                    closestSchedule.remove(pid);

                                    scheduledByMessage.process(message, e -> {
                                        if (e.exists) {
                                            PidSet pids = removePids(e.value, pid);
                                            if (pids == Cancel) {
                                                e.delete();
                                            } else {
                                                e.value = pids;
                                            }
                                        }
                                    });
                                    rebuild = True;
                                }
                            }
                        } else if (changeParser.matchKey("s")) { // scheduled
                            using (val schedulesParser = changeParser.expectArray()) {
                                if (schedulesParser.eof) {
                                    // this is an "unschedule" request
                                    scheduledByMessage.remove(message);
                                    rebuild = True;
                                } else {
                                    do {
                                        using (val scheduleParser = schedulesParser.expectObject()) {
                                            scheduleParser.expectKey("pid");
                                            Pid pid = scheduleParser.expectInt();

                                            Token[] tokens = scheduleParser.skip(new Token[]);

                                            closestTx.put(pid, txId);
                                            closestSchedule.put(pid, tokens);

                                            scheduledByMessage.process(message, e -> {
                                                e.value = e.exists ? addPid(e.value, pid) : pid;
                                            });
                                        }
                                    } while (!schedulesParser.eof);
                                }
                            }
                        }
                    }
                }
            }

            if (scheduledByMessage.empty) {
                // all the messages were processed; remove the storage
                file.delete();
                totalFiles--;
                bytesUsed -= bytes.size;
                continue;
            }

            StringBuffer buf = new StringBuffer();

            for ((Int txId, Message[] messages) : messagesByTx) {
                for (Message message : messages) {
                    if (PidSet pids := scheduledByMessage.get(message)) {
                        assert !pids.is(Unschedule);

                        if (pids.is(Int)) {
                            assert Int txLast := closestTx.get(pids);
                            if (txLast == txId) {
                                assert Token[] tokens := closestSchedule.get(pids);
                                // TODO: reconstruct the Schedule

                                Schedule? schedule = Null;
                                scheduleByPid.put(pids, schedule);

                                scheduler.registerPid(id, pids, clock.now,
                                        new Pending(path, message, schedule));
                            } else {
                                continue;
                            }
                        } else {
                            for (Pid pid : pids) {
                                assert Int txLast := closestTx.get(pid);
                                if (txLast == txId) {
                                    // TODO: reconstruct the Schedule
                                    Schedule? schedule = Null;
                                    scheduleByPid.put(pid, schedule);

                                    scheduler.registerPid(id, pid, clock.now,
                                            new Pending(path, message, schedule));
                                } else {
                                    pids = pids - pid;
                                }
                            }
                            if (pids.empty) {
                                continue;
                            }
                        }

                        History messageHistory = scheduleHistory.computeIfAbsent(
                                message, () -> new History());
                        messageHistory.put(txId, pids);

                        if (rebuild) {
                            assert Range<Int> loc := messageLoc.get(message);
                            appendJsonEntry(buf, txId, jsonStr.slice(loc), buildJsonScheduled(pids));
                        }
                    }
                }
            }

            if (rebuild) {
                buf[0] = '[';

                String           jsonNew  = buf.add('\n').add(']').toString();
                immutable Byte[] bytesNew = jsonNew.utf8();

                file.contents = bytesNew;

                bytesUsed += bytesNew.size - bytes.size;
            }
        }

        filesUsed  = totalFiles;
        lastCommit = desired;
    }

    @Override
    @Synchronized
    Boolean recover(SkiplistMap<Int, Token[]> sealsByTxId) {
        Map<String, StringBuffer> recoveredContents;
        Map<String, Int>          lastTxInFile;

        if (!((recoveredContents, lastTxInFile) :=
                recoverContents(sealsByTxId, "m", messageMapping, jsonSchema))) {
            return False;
        }

        for ((Int txId, Token[] tokens) : sealsByTxId) {
            using (val sealParser = new Parser(tokens.iterator())) {
                using (val changeArrayParser = sealParser.expectArray()) {
                    while (!changeArrayParser.eof) {
                        using (val changeParser = changeArrayParser.expectObject()) {
                            String fileName;

                            changeParser.expectKey("m");

                            Token[] messageTokens = changeParser.skip(new Token[]);
                            using (ObjectInputStream stream =
                                    new ObjectInputStream(jsonSchema, messageTokens.iterator())) {
                                Message message = messageMapping.read(stream.ensureElementInput());

                                fileName = nameForKey(message);
                            }

                            // apply just the missing transactions
                            if (Int lastInFile := lastTxInFile.get(fileName),
                                    lastInFile < txId || lastInFile == NO_TX) {
                                assert StringBuffer buf := recoveredContents.get(fileName);

                                if (changeParser.matchKey("p")) {
                                    appendChange(buf, txId, "m", messageTokens,
                                            "p", changeParser.skip(new Token[]));
                                }

                                if (changeParser.matchKey("s")) {
                                    appendChange(buf, txId, "m", messageTokens,
                                            "s", changeParser.skip(new Token[]));
                                }
                            }
                        }
                    }
                }
            }
        }

        for ((String fileName, StringBuffer buf) : recoveredContents) {
            buf.truncate(-1).add('\n').add(']');
            dataDir.fileFor(fileName).contents = buf.toString().utf8();
        }

        return True;
    }

    @Override
    void unload() {
        inFlight.clear();
        scheduleHistory.clear();
        scheduleByPid.clear();
        processModsByTx.clear();
        scheduleModsByTx.clear();
        fileNames.clear();
    }


    // ----- internal ------------------------------------------------------------------------------

    protected static PidSet addPid(PidSet pids, Pid pid) {
        return pids.is(Unschedule)
                ? pid
                : pids.is(Int)
                    ? [pids, pid]
                    : pids + pid;
    }

    protected static PidSet removePids(PidSet pidsOrig, PidSet pidsRemove) {
        if (pidsOrig.is(Unschedule) || pidsRemove.is(Unschedule)) {
            return Cancel;
        }

        if (pidsOrig.is(Int)) {
            Boolean match = pidsRemove.is(Int)
                    ? pidsOrig == pidsRemove
                    : pidsRemove.contains(pidsOrig);
            return match ? Cancel : pidsOrig;
        }

        pidsOrig = pidsRemove.is(Int)
                ? pidsOrig.remove(pidsRemove)
                : pidsOrig.removeAll(pid -> pidsRemove.contains(pid));

        return switch (pidsOrig.size) {
            case 0:  Cancel;
            case 1:  pidsOrig[0];
            default: pidsOrig;
        };
    }

    protected void clearSchedules(PidSet pids) {
        if (pids.is(Int)) {
            scheduleByPid.remove(pids);
        } else if (pids.is(Int[])) {
            scheduleByPid.keys.removeAll(pids);
        }
    }

    private String buildJsonScheduled(PidSet pids) {
        StringBuffer buf = new StringBuffer();
        buf.append("\"s\":[");
        if (!pids.is(Unschedule)) {
            if (pids.is(Int)) {
                assert Schedule? schedule := scheduleByPid.get(pids);
                appendJsonSchedule(buf, pids, schedule);
            } else {
                Loop: for (Pid pid : pids) {
                    assert Schedule? schedule := scheduleByPid.get(pid);
                    appendJsonSchedule(buf, pid, schedule);
                    if (!Loop.last) {
                        buf.add(',');
                    }
                }
            }
        }
        buf.add(']');
        return buf.toString();
    }

    private void appendJsonSchedule(StringBuffer buf, Pid pid, Schedule? schedule) {
        // {"pid":[Int],"s":{"at":[Time],"daily":[TimeOfDay],"repeat":[Duration],"policy":[Policy],"priority":[Priority]}}
        if (schedule == Null) {
            buf.append($"\{\"pid\":{pid}}");
            return;
        }

        Time?      scheduledAt    = schedule.scheduledAt;
        TimeOfDay? scheduledDaily = schedule.scheduledDaily;
        Duration?  repeatInterval = schedule.repeatInterval;
        Policy     repeatPolicy   = schedule.repeatPolicy;
        Priority   priority       = schedule.priority;

        buf.append($"\{\"pid\":{pid}, \"s\":\{");

        Boolean needComma = False;

        if (scheduledAt != Null) {
            buf.append("\"at\":\"");
            scheduledAt.appendTo(buf, True);
            buf.add('"');
            needComma = True;
        }
        if (scheduledDaily != Null) {
            if (needComma) {
                buf.add(',');
            }
            buf.append("\"daily\":\"");
            scheduledDaily.appendTo(buf);
            buf.add('"');
            needComma = True;
        }
        if (repeatInterval != Null) {
            if (needComma) {
                buf.add(',');
            }
            buf.append("\"repeat\":\"");
            repeatInterval.appendTo(buf);
            buf.add('"');
            needComma = True;
        }
        if (repeatPolicy != AllowOverlapping) {
            if (needComma) {
                buf.add(',');
            }
            buf.append("\"repeat\":");
            repeatPolicy.ordinal.appendTo(buf);
            needComma = True;
        }
        if (priority != Normal) {
            if (needComma) {
                buf.add(',');
            }
            buf.append("\"priority\":");
            priority.ordinal.appendTo(buf);
            needComma = True;
        }
        buf.add('}');
    }

    private void appendJsonEntry(StringBuffer buf, Int txId, String jsonMsg, String jsonEntry) {
        buf.append(",\n{\"tx\":")
           .append(txId)
           .add(',').add(' ')
           .append("\"m\":")
           .append(jsonMsg)
           .add(',').add(' ')
           .append(jsonEntry)
           .add('}');
    }

    protected void rotateLog() {
        // TODO
//        String timestamp   = clock.now.toString(True);
//        String rotatedName = $"log_{timestamp}.json";
//
//        assert File rotatedFile := dataFile.renameTo(rotatedName);
    }
}