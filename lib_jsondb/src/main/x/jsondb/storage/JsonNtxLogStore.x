import model.DBObjectInfo;

import json.Mapping;
import json.ObjectOutputStream;

/**
 * Provides the low-level I/O for a non-transactional (i.e. extra-transactional) log.
 */
@Concurrent
service JsonNtxLogStore<Element extends immutable Const>
        extends JsonLogStoreBase<Element>
    {
    // ----- constructors --------------------------------------------------------------------------

    construct(Catalog          catalog,
              DBObjectInfo     info,
              Mapping<Element> elementMapping,
              Duration         expiry,
              Int              truncateSize,
              Int              maxFileSize,
              )
        {
        super(catalog, info, elementMapping, expiry, truncateSize, maxFileSize);
        }


    // ----- storage API exposed to the client -----------------------------------------------------

    @Override
    @Synchronized
    void append(Int txId, Element element)
        {
        checkWrite();

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
        if (length > maxFileSize)
            {
            rotateLog();
            }
        }
    }