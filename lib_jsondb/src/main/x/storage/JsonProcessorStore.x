import model.DBObjectInfo;

import json.Mapping;
import json.ObjectOutputStream;

/**
 * Provides the low-level I/O for a DBProcessor, which represents the combination of a queue (with
 * scheduling) and a processor of the messages in the queue.
 */
@Concurrent
service JsonProcessorStore<Message extends immutable Const>
        extends ObjectStore(catalog, info)
        implements ProcessorStore<Message>
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


    // ----- storage API exposed to the client -----------------------------------------------------

    // TODO


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
        TODO
        }

    @Override
    void unload()
        {
        }


    // ----- internal ------------------------------------------------------------------------------

    protected void rotateLog()
        {
        // TODO
//        String timestamp   = clock.now.toString(True);
//        String rotatedName = $"log_{timestamp}.json";
//
//        assert File rotatedFile := dataFile.renameTo(rotatedName);
        }
    }
