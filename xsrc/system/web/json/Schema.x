import collections.Hasher;
import collections.HashMap;
import collections.ListMap;

import io.Reader;
import io.Writer;
import io.ObjectInput;
import io.ObjectOutput;


const Schema
        implements io.TextFormat
    {
    /**
     * Construct a schema for a specified set of Mappings.
     *
     * @param mappings          pass a sequence of `Mapping` objects to use to read and write
     *                          various Ecstasy types; order defines precedence of selection in
     *                          cases of ties
     * @param version           pass a `Version` to use for the schema, otherwise one is calculated
     * @param randomAccess      pass `True` to enable random access of name/value pairs within JSON
     *                          objects
     * @param enableMetadata    pass `True` to enable metadata collection from the beginning of JSON
     *                          objects when reading, and to emit metadata to the beginning of JSON
     *                          objects when writing
     * @param enablePointers    pass `True` to use JSON pointers to eliminate duplicate
     *                          serialization of custom objects non-primitive JSON types
     * @param enableReflection  pass `True` to use reflection as the basis for serialization and
     *                          deserialization of types for which the Mapping is missing
     * @param retainNulls       pass `True` to treat JSON `null` values as significant
     * @param storeRemainders   pass `True` to collect and store any metadata and unread properties
     *                          together as a means of supporting forward version compatibility
     */
    construct(Mapping[] mappings         = [],
              Version?  version          = Null,
              Boolean   randomAccess     = False,
              Boolean   enableMetadata   = False,
              Boolean   enablePointers   = False,
              Boolean   enableReflection = False,
              Boolean   retainNulls      = False,
              Boolean   storeRemainders  = False)
        {
        // use the module version if no version is specified
        if (version == Null)
            {
            version = v:1; // TODO version = ...
            }

        this.version          = version;
        this.randomAccess     = randomAccess;
        this.enableMetadata   = enableMetadata;
        this.enablePointers   = enablePointers;
        this.enableReflection = enableReflection;
        this.retainNulls      = retainNulls;
        this.storeRemainders  = storeRemainders;

        // add specified mappings
        ListMap<Type, Mapping> mappingForType = new ListMap();
        Map<String, Type>      typeForName    = new HashMap();
        for (Mapping mapping : mappings)
            {
            if (mappingForType.putIfAbsent(mapping.Serializable, mapping))
                {
                typeForName.putIfAbsent(mapping.typeName?, mapping.Serializable);
                }
            }

        this.mappings    = mappingForType;

// TODO CP should Map be ImmutableAble? maybe conditionally based on the Key/Value const'ness?
        // at the moment, Map is not freezable (ImmutableAble), so we need to
        // "freeze" it explicitly
        this.typeForName = typeForName.makeImmutable();
        }

    /**
     * The "default" Schema, which is not aware of any custom serialization or deserialization
     * mechanisms.
     */
    static Schema DEFAULT = new Schema(enableMetadata = True, enablePointers = True, enableReflection = True);


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The `Schema` version, which defaults to the module version if the schema is drawn from a
     * single module.
     */
    Version version;

    /**
     * An option to enable random access, which means that JSON objects can have their properties
     * accessed in any order (as if the documents were managed internally as a `Map<String, Doc>`),
     * instead of assuming that each name requested will be the next name in the document.
     *
     * By assuming a strict order, the JSON parsing can avoid materializing intermediate data
     * structures to hold the entire contents of the JSON object in order to support random access
     * to fields.
     *
     * By allowing random access, JSON deserialization is expected to incur a significant additional
     * cost.
     */
    Boolean randomAccess;

    /**
     * An option to look for metadata properties at the beginning of each JSON object, and extract
     * them. All leading properties that begin with an "@", a "$", or an "_" character are assumed
     * to be metadata properties.
     *
     * Similarly, when serializing to JSON, this option will cause the class or type of each object
     * to be emitted as metadata.
     *
     * By enabling metadata, JSON serialization and deserialization will incur a minor additional
     * cost.
     *
     * When `collectRemainders` is `True`, the metadata properties are incorporated into the
     * remainder as well.
     */
    Boolean enableMetadata;

    /**
     * An option to allow lookup of deserialized values and objects using JSON pointers. This allows
     * for "graph serialization", in which the same Ecstasy object instance can be referenced from
     * multiple locations within the deserialized object graph.
     *
     * By enabling pointers, JSON serialization and deserialization will incur a significant
     * additional cost.
     *
     * @see [RFC 6901 §5](https://tools.ietf.org/html/rfc6901#section-5)
     */
    Boolean enablePointers;

    /**
     * An option to handle any types and classes of objects that do not have a known mapping by
     * using a "catch-all" mapping. The "catch-all" mapping uses reflection to serialize and
     * deserialize objects without a custom [Mapping] implementation.
     */
    Boolean enableReflection;

    /**
     * An option to treat null values as significant. This is generally only useful for "pretty
     * printing" and debugging.
     */
    Boolean retainNulls;

    /**
     * An option to collect and store any metadata and unread properties together as a means of
     * supporting interop across loosely compatible systems, and to support forward version
     * compatibility.
     *
     * By enabling remainder collection and storage, JSON serialization and deserialization will
     * incur a minor additional cost.
     */
    Boolean storeRemainders;

    /**
     * A prioritized mapping from Ecstasy types to JSON Mapping implementations.
     */
    ListMap<Type, Mapping> mappings;

    /**
     * A mapping from JSON metadata type names to Ecstasy types.
     */
    Map<String, Type> typeForName;

    /**
     * A lazily instantiated type mapping service.
     */
    @Lazy
    TypeMapper typeMapper.calc()
        {
        return new TypeMapper();
        }

    /**
     * A lazily instantiated default Mapping that handles any object.
     TODO CP need Mapping<T> not <Object> - needs to be a const, and cache the pre-calc'd reflection data inside
     */
    @Lazy
    Mapping<Object> defaultMapping.calc()
        {
        assert enableReflection;
        return new ReflectionMapping();
        }


    // ----- TextFormat implementation --------------------------------------------------------------

    @Override
    @RO String name.get()
        {
        return "JSON";
        }

    @Override
    ObjectInput createObjectInput(Reader reader)
        {
        return new ObjectInputStream(this, reader);
        }

    @Override
    ObjectOutput createObjectOutput(Writer writer)
        {
        return new ObjectOutputStream(this, writer);
        }


    // ----- helpers

    /**
     * Search for a mapping for the specified type.
     *
     * @param type  the type for which a Mapping is required
     *
     * @return True iff a Mapping was found
     * @return (conditional) the selected Mapping
     */
    <ObjectType> conditional Mapping<ObjectType> getMapping(Type<ObjectType> type)
        {
        if (Mapping mapping := mappings.get(type), mapping.Serializable.is(Type<ObjectType>))
            {
            return True, mapping.as(Mapping<ObjectType>);
            }

        if (Type typeAlt := typeMapper.selectType(type))
            {
            return True, mappings[typeAlt].as(Mapping<ObjectType>);
            }

        if (enableReflection)
            {
            return True, defaultMapping;
            }

        return False;
        }

    <ObjectType> Mapping<ObjectType> ensureMapping(Type<ObjectType> type)
        {
        if (Mapping<ObjectType> mapping := getMapping(type))
            {
            return mapping;
            }

        throw new UnsupportedOperation($"No JSON Schema Mapping found for Type={type}");
        }

    conditional Mapping getMapping(Doc doc)
        {
        TODO
        }

    /**
     * Convert a JSON pointer to a String that can be used as part of a URI fragment.
     *
     * @param a JSON pointer
     *
     * @return the corresponding ASCII string that can be used in the "fragment" portion of a URI
     */
    static String pointerToUri(String pointer)
        {
        Int length = estimatePointerUriLength(pointer);
        return length == pointer.size
                ? pointer
                : appendPointerUri(pointer, new StringBuffer(length)).toString();
        }

    /**
     * For each ASCII code 0-127, this specifies if the ASCII code is permitted in the "fragment"
     * portion of the URI without being escaped.
     */
    static Boolean[] FRAGMENT_ALLOW =
    //    (control characters...............)  !  $ &' ()*+,-./ 01234567 89:; = ? @ABCDEFG HIJKLMNO PQRSTUVW XYZ    _  abcdefg hijklmno pqrstuvw xyz   ~
        0b00000000_00000000_00000000_00000000_01001011_11111111_11111111_11110101_11111111_11111111_11111111_11100001_01111111_11111111_11111111_11100010
        .toUInt128().toBooleanArray();

    /**
     * Estimate the number of ASCII characters necessary to encode a JSON pointer into the fragment
     * portion of a URI.
     *
     * @param the JSON pointer
     *
     * @return the number of ASCII characters necessary to encode the JSON pointer into the fragment
     *         portion of a URI
     */
    static Int estimatePointerUriLength(String pointer)
        {
        Int length = pointer.size;
        for (Char ch : pointer)
            {
            Int n = ch.toInt();
            if (n <= 0x7F)
                {
                if (!FRAGMENT_ALLOW[n])
                    {
                    length += 2;
                    }
                }
            else
                {
                // first encode the character as UTF8, then convert each non-ASCII byte (i.e. all of
                // them) to the %-encoded form
                length += ch.calcUtf8Length() * 3;
                }
            }
        return length;
        }

    /**
     * Encode a JSON pointer into a sequence of ASCII characters that can be used in the fragment
     * portion of a URI.
     *
     * @param pointer  the JSON pointer
     * @param buf      the Char Appender to encode the JSON pointer into
     *
     * @return the Char Appender
     */
    static <Buf extends Appender<Char>> Buf appendPointerUri(String pointer, Buf buf)
        {
        for (Char ch : pointer)
            {
            Int n = ch.toInt();
            if (n <= 0x7F)
                {
                if (FRAGMENT_ALLOW[n])
                    {
                    buf.add(ch);
                    }
                else
                    {
                    buf.add('%').add((n >>> 4).toHexit()).add(n.toHexit());
                    }
                }
            else
                {
                // first encode the character as UTF8, then convert each non-ASCII byte (i.e. all of
                // them) to the %-encoded form
                for (Byte b : ch.utf())
                    {
                    buf.add('%').add((b >>> 4).toHexit()).add(b.toHexit());
                    }
                }
            }
        return buf;
        }


    // ----- TypeMapper service --------------------------------------------------------------------

    /**
     * A service that looks for `Mapping`-by-type when there is **NOT** a Mapping that specifies
     * that exact type. For example, annotated types may be handled by the non-annotated forms, and
     * subclass types may be handled by a superclass type's `Mapping`.
     */
    service TypeMapper
        {
        /**
         * Given a `Type`, check if there is a compatible `Mapping`, such as one that handles a
         * superclass of the specified `Type`.
         *
         * @param type  the `Type` to find a Mapping for
         *
         * @return True iff a compatible Mapping type was found
         * @return (conditional) the `Type` for which a compatible `Mapping` exists
         *
         * @throws UnsupportedOperation if no compatible Mapping can be found
         */
        <ObjectType> conditional Type<ObjectType> selectType(Type<ObjectType> type)
            {
            if (Type mappingType := cache.get(type))
                {
                return True, mappingType.as(Type<ObjectType>);
                }

            for (Type mappingType : mappings.keys)
                {
                if (type.isA(mappingType))
                    {
                    cache.put(type, mappingType);
                    return True, mappingType.as(Type<ObjectType>);
                    }
                }

            return False;
            }

        private Map<Type, Type> cache = new HashMap();
        }
    }