import model.DBObjectInfo;

import json.Lexer;

/**
 * Provides the low-level I/O for a non-transactional (i.e. extra-transactional) counter.
 */
service JsonNtxCounterStore(Catalog catalog, DBObjectInfo info, Appender<String> errs)
        extends ObjectStore(catalog, info, errs)
        implements CounterStore
    {
    Int current;

    /**
     * The file owned by this ObjectStore for purpose of its data storage. The ObjectStore may
     * create, modify, and remove this file.
     */
    @Lazy public/private File dataFile.calc()
        {
        return dataDir.fileFor(info.name + ".json");
        }

    @Override
    Int load(Int txId)
        {
        return current;
        }

    @Override
    void store(Int txId, Int value)
        {
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
            Lexer lexer = new Lexer(dataFile.contents.unpackString().toReader());
            assumeValue = lexer.next().as(IntLiteral).toInt64();
            assert !lexer.next();
            corrupted = False;
            }
        catch (Exception e)
            {
            errs.add($"While attempting to read and parse \"{dataFile}\", encountered exception: {e}");
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
        current = new Lexer(dataFile.contents.unpackString().toReader()).next().as(IntLiteral).toInt64();
        }

    @Override
    void unload()
        {
        current = 0;
        }
    }
