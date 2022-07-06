import model.DBObjectInfo;

import json.Mapping;

/**
 * The abstract base for LogStore implementations.
 */
@Abstract
@Concurrent
service JsonLogStoreBase<Element extends immutable Const>
        extends ObjectStore(catalog, info)
        implements LogStore<Element>
    {
    // ----- constructors --------------------------------------------------------------------------

    construct(Catalog          catalog,
              DBObjectInfo     info,
              Mapping<Element> elementMapping,
              Duration         expiry,
              Int              truncateSize,
              )
        {
        super(catalog, info);

        this.jsonSchema     = catalog.jsonSchema;
        this.elementMapping = elementMapping;
        this.truncateSize   = truncateSize > 0 ? truncateSize.maxOf(1024) : Int.maxvalue;
        }


    // ----- properties ----------------------------------------------------------------------------

    @Inject Clock clock;

    /**
     * A cached reference to the JSON schema.
     */
    public/protected json.Schema jsonSchema;

    /**
     * The JSON Mapping for the log elements.
     */
    public/protected Mapping<Element> elementMapping;

    /**
     * The file owned by this LogStore for purpose of its data storage. The LogStore may
     * create, modify, and remove this file.
     */
    @Lazy public/private File dataFile.calc()
        {
        return dataDir.fileFor("log.json");
        }

    /**
     * The maximum size log to store in any one log file.
     */
    protected Int truncateSize;


    // ----- transaction API exposed to TxManager --------------------------------------------------

    @Override
    void initializeEmpty()
        {
        assert model == Empty;
        }

    @Override
    void loadInitial()
        {
        assert model != Empty;

        File file = dataFile;
        if (file.exists)
            {
            Byte[] bytes   = file.contents;
            String jsonStr = bytes.unpackUtf8();

            // the most common corruption is caused by the process termination between "truncate"
            // and "append" call (see JsonNtxLogStore.append), in which case the terminating "\n]"
            // sequence is missing
            if (!jsonStr.endsWith("\n]"))
                {
                file.append("\n]".utf8());
                }
            }
        }

    @Override
    void unload()
        {
        }


    // ----- internal ------------------------------------------------------------------------------

    protected void rotateLog()
        {
        String timestamp   = clock.now.toString(True);
        String rotatedName = $"log_{timestamp}.json";

        assert File rotatedFile := dataFile.renameTo(rotatedName);
        }
    }