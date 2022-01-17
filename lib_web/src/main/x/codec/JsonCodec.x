import ecstasy.io.UTF8Reader;
import ecstasy.io.UTF8Writer;

import json.Schema;

/**
 * A codec that handles the `application/json` MediaType.
 */
const JsonCodec
        implements MediaTypeCodec
    {
    construct (Schema schema = Schema.DEFAULT, Set<MediaType> additionalTypes = Set:[])
        {
        HashSet<MediaType> mediaTypes = new HashSet();
        mediaTypes.addAll(additionalTypes);
        mediaTypes.add(MediaType.APPLICATION_JSON_TYPE);
        mediaTypes.freeze(True);
        this.types  = mediaTypes;
        this.schema = schema;
        }

    private Set<MediaType> types;

    private Schema schema;

    @Override
    MediaType[] mediaTypes.get()
        {
        return types.toArray();
        }

    @Override
    Boolean supports(Type type)
        {
        return type.isA(Stringable);
        }

    @Override
    <ObjectType> ObjectType decode<ObjectType>(InputStream in)
        {
        return schema.createObjectInput(new UTF8Reader(in)).read<ObjectType>();
        }

    @Override
    <ObjectType> void encode<ObjectType>(ObjectType value, OutputStream out)
        {
        if (value.is(Iterable<Char>))
            {
            for (Char c : value)
                {
                out.writeBytes(c.utf8());
                }
            }
        else
            {
            schema.createObjectOutput(new UTF8Writer(out)).write(value);
            }
        }
    }
