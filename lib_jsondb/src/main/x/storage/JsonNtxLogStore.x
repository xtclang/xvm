import model.DBObjectInfo;

import json.Mapping;
import json.ObjectOutputStream;

/**
 * Provides the low-level I/O for a non-transactional (i.e. extra-transactional) log.
 */
service JsonNtxLogStore<Element extends immutable Const>
        extends ObjectStore(catalog, info)
        implements LogStore<Element>
    {
    // ----- constructors --------------------------------------------------------------------------

    construct(Catalog          catalog,
              DBObjectInfo     info,
              Mapping<Element> elementMapping,
              )
        {
        construct ObjectStore(catalog, info);

        this.jsonSchema     = catalog.jsonSchema;
        this.elementMapping = elementMapping;
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
     * TODO this setting should be configurable (need a "Prefs" API)
     */
    protected Int maxLogSize = 100K;


    // ----- storage API exposed to the client -----------------------------------------------------

    @Override
    void append(Int txId, Element element)
        {
        StringBuffer buf = new StringBuffer(64);

        buf.append(",\n{\"t\":\"")
           .append(clock.now.toString(True))
           .append("\", \"e\":");

        ObjectOutputStream stream = new ObjectOutputStream(jsonSchema, buf);
        elementMapping.write(stream.createElementOutput(), element);
        stream.close();

        buf.append("}\n]");

        File file   = dataFile;
        Int  length = file.exists ? file.size : 0;
        if (length > 2)
            {
            file.truncate(length-2)
                .append(buf.toString().utf8());
            }
        else
            {
            // replace the opening "," with an array begin "["
            buf[0]         = '[';
            file.contents  = buf.toString().utf8();
            }

        length += buf.size;
        if (length > maxLogSize)
            {
            // TODO schedule a rotation
            }
        }


    // ----- transaction API exposed to TxManager --------------------------------------------------

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
