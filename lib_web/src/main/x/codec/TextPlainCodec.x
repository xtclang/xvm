import ecstasy.io.UTF8Reader;

/**
 * A codec that handles the `text/plain` MediaType.
 */
const TextPlainCodec
        implements MediaTypeCodec
    {
    construct (Set<MediaType> additionalTypes = Set:[])
        {
        HashSet<MediaType> mediaTypes = new HashSet();
        mediaTypes.addAll(additionalTypes);
        mediaTypes.add(MediaType.TEXT_PLAIN_TYPE);
        types = mediaTypes.freeze(True);
        }

    private Set<MediaType> types;

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
    <ObjectType> ObjectType decode<ObjectType>(Type type, InputStream in)
        {
        if (ObjectType.is(Type<String>))
            {
            StringBuffer buffer = new StringBuffer();
            new UTF8Reader(in).pipeTo(buffer);
            return buffer.toString().as(ObjectType);
            }
        throw new IllegalArgument("requested type is not a String");
        }

    @Override
    <ObjectType> void encode<ObjectType>(ObjectType value, OutputStream out)
        {
        assert:arg value.is(Stringable);
        for (Char c : out.toString())
            {
            out.writeBytes(c.utf8());
            }
        }
    }
