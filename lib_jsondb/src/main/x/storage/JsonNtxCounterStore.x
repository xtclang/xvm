import model.DBObjectInfo;

import json.Parser;

/**
 * Provides the low-level I/O for a non-transactional (i.e. extra-transactional) counter.
 */
@Concurrent
service JsonNtxCounterStore(Catalog catalog, DBObjectInfo info)
        extends ObjectStore(catalog, info)
        implements CounterStore
    {
    Int current;

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
    void store(Int txId, Int value)
        {
        checkRead();
        current = value;
        dataFile.contents = value.toString().utf8();
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
