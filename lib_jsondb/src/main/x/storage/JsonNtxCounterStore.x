import model.DBObjectInfo;

import json.Parser;

import TxManager.NO_TX;


/**
 * Provides the low-level I/O for a non-transactional (i.e. extra-transactional) counter.
 */
@Concurrent
service JsonNtxCounterStore(Catalog catalog, DBObjectInfo info)
        extends ObjectStore(catalog, info)
        implements CounterStore
    {
    /**
     * The current value of the counter, as it is stored on disk.
     */
    Int current;

    /**
     * The beginning of the block of counter values, if bulk allocation is being used.
     */
    Int block = -1;

    /**
     * The file owned by this ObjectStore for purpose of its data storage. The ObjectStore may
     * create, modify, and remove this file.
     */
    @Lazy public/private File dataFile.calc()
        {
        return dataDir.fileFor("value.json");
        }

    @Override
    Int load(Int txId)
        {
        checkRead();
        return current;
        }

    @Override
    @Synchronized
    void store(Int txId, Int value)
        {
        checkWrite();
        current = value;
        dataFile.contents = value.toString().utf8();
        }

    /**
     * For use internally (only!) for monotonic counters that can have gaps on restart.
     */
    Int next()
        {
        checkWrite();
        if (0 <= block < current)
            {
            return ++block;
            }

        block = current;
        store(NO_TX, current + 100);
        return next();
        }

    @Override
    Iterator<File> findFiles()
        {
        return (dataFile.exists ? [dataFile] : []).iterator();
        }

    @Override
    Boolean deepScan(Boolean fix = True)
        {
        if (super() && !dataFile.exists)
            {
            return True;
            }

        // read the file and verify that it is parsable
        Boolean corrupted   = True;
        Int     assumeValue = 0;
        try
            {
            Parser parser = new Parser(dataFile.contents.unpackUtf8().toReader());
            assumeValue = parser.expectInt();
            assert !parser.next();
            corrupted = False;
            }
        catch (Exception e)
            {
            log($"While attempting to read and parse \"{dataFile}\", encountered exception: {e}");
            }

        if (corrupted && fix)
            {
            dataFile.contents = assumeValue.toString().utf8();
            corrupted = False;
            }

        return !corrupted;
        }

    @Override
    void initializeEmpty()
        {
        assert model == Empty;
        assert !dataFile.exists;
        current = 0;
        }

    @Override
    void loadInitial()
        {
        assert dataFile.exists;

        Parser parser = new Parser(dataFile.contents.unpackUtf8().toReader());
        current = parser.expectInt();
        assert !parser.next();
        }

    @Override
    void unload()
        {
        current = 0;
        }
    }
