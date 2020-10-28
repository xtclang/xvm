import ecstasy.collections.Hasher;

import ecstasy.io.Reader;
import ecstasy.io.Writer;
import ecstasy.io.ObjectInput;
import ecstasy.io.ObjectOutput;
import ecstasy.io.TextFormat;


/**
 * A JSON Schema represents the ability to read and write Ecstasy objects (of a particular group of
 * Ecstasy types) from and to JSON textual data, or conversely, the ability to transform JSON
 * textual data from and to Ecstasy object graphs.
 */
const Schema
        implements TextFormat
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
     * @param typeSystem        pass the TypeSystem to use for reflection; the TypeSystem must be
     *                          the type system that contains the JSON module, or a TypeSystem
     *                          derived thereof; defaults to the type system that contains the JSON
     *                          module
     * @param retainNulls       pass `True` to treat JSON `null` values as significant
     * @param storeRemainders   pass `True` to collect and store any metadata and unread properties
     *                          together as a means of supporting forward version compatibility
     */
    construct(Mapping[]   mappings         = [],
              Version?    version          = Null,
              Boolean     randomAccess     = False,
              Boolean     enableMetadata   = False,
              String      typeKey          = "$type",
              Boolean     enablePointers   = False,
              String      pointerKey       = "$ref",
              Boolean     enableReflection = False,
              TypeSystem? typeSystem       = Null,
              Boolean     retainNulls      = False,
              Boolean     storeRemainders  = False)
        {
        // verify that the type system is the TypeSystem that includes this class, or a TypeSystem
        // that derives from the TypeSystem that includes this class
        if (typeSystem == Null)
            {
            typeSystem = &this.actualType.typeSystem;
            }
        else
            {
            assert typeSystem == &this.actualType.typeSystem || &this.actualClass.pathWithin(typeSystem);
            }

        // use the module version if no version is specified
        version ?:= typeSystem.primaryModule.version;

        // store off specified options
        this.version          = version;
        this.randomAccess     = randomAccess;
        this.enableMetadata   = enableMetadata | enablePointers | enableReflection;
        this.typeKey          = typeKey;
        this.enablePointers   = enablePointers;
        this.pointerKey       = pointerKey;
        this.enableReflection = enableReflection;
        this.retainNulls      = retainNulls;
        this.storeRemainders  = storeRemainders;
        this.typeSystem       = typeSystem;

        // build indexes for the provided mappings
        ListMap<Type, Mapping> mappingByType   = new ListMap();
        HashMap<String, Type>  typeByName      = new HashMap();
        Mapping[]              defaultMappings = mapping.DEFAULT_MAPPINGS;
        mappings = mappings.empty ? defaultMappings : (new Mapping[]) + mappings + defaultMappings;
        for (Mapping mapping : mappings)
            {
            mappingByType.putIfAbsent(mapping.Serializable, mapping);
            typeByName.putIfAbsent(mapping.typeName, mapping.Serializable);
            }

        if (enableReflection)
            {
            // add a reflection mapping that "handles" any object (by providing a more specific
            // mapping for whatever object type is requested)
            import mapping.*;
            // TODO GG (??): val mapping = new @Narrowable ReflectionMapping("Object", Object, []);
            val mapping = new @Narrowable ReflectionMapping<Object, Object:struct>("Object", Object, []);
            mappingByType.putIfAbsent(Object, mapping);
            typeByName.putIfAbsent("Object", Object);
            }

        this.mappingByType = mappingByType;
        this.typeByName    = typeByName.makeImmutable();
        }

    /**
     * The "default" Schema, which is not aware of any custom serialization or deserialization
     * mechanisms.
     */
    static Schema DEFAULT = new Schema(
            enableReflection = True,
            enableMetadata   = True,
            enablePointers   = True,
            randomAccess     = True);


    // ----- TextFormat interface ------------------------------------------------------------------

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
     * The String name in a name/value pair that identifies the type of the JSON object that is used
     * to determine the type of the Ecstasy object if the use of metadata is enabled in the schema.
     */
    String typeKey;

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
     * The String name in a name/value pair that identifies that the JSON object acts as a pointer
     * to a previously read object, if the use of pointers is enabled in the schema.
     */
    String pointerKey;

    /**
     * An option to handle any types and classes of objects that do not have a known mapping by
     * using a "catch-all" mapping. The "catch-all" mapping uses reflection to serialize and
     * deserialize objects without a custom [Mapping] implementation.
     */
    Boolean enableReflection;

    /**
     * The TypeSystem that the reflection-based capabilities will use.
     */
    TypeSystem typeSystem;

    /**
     * An option to treat null values as significant. This is generally only useful for "pretty
     * printing" and debugging.
     */
    Boolean retainNulls;

    /**
     * An option to collect and store any metadata and unread properties together as a means of
     * supporting inter-op across loosely compatible systems, and to support forward version
     * compatibility.
     *
     * By enabling remainder collection and storage, JSON serialization and deserialization will
     * incur a minor additional cost.
     */
    Boolean storeRemainders;

    /**
     * A prioritized mapping from Ecstasy types to JSON Mapping implementations.
     */
    ListMap<Type, Mapping> mappingByType;

    /**
     * A mapping from JSON metadata type names to Ecstasy types.
     */
    HashMap<String, Type> typeByName;

    /**
     * A lazily instantiated type mapping service.
     */
    @Lazy
    MappingService mapper.calc()
        {
        return new MappingService();
        }


    // ----- Mapping look-up -----------------------------------------------------------------------

    /**
     * Helper to render a type as a string that can be serialized into a JSON document for later use
     * in the deserialization process.
     *
     * @param type  the type to obtain a serializable string representation for
     *
     * @return a string representation of the passed type
     */
    String nameForType(Type type)
        {
        return type.toString();
        }

    /**
     * Helper to "deserialize" a previously serialized type string into an Ecstasy type. Failure to
     * deserialize the string into a type will result in an exception.
     *
     * @param typeName  a string from a JSON document that represents a type
     *
     * @return a type corresponding to the passed string
     */
    Type typeForName(String typeName)
        {
        assert Type type := typeSystem.typeForName(typeName);
        return type;
        }

    /**
     * Find or create a mapping for the specified type.
     *
     * @param type  the type for which a Mapping is desired
     *
     * @return True iff a Mapping was found for the specified type
     * @return (conditional) the Mapping for the specified type
     */
    <Serializable> conditional Mapping<Serializable> findMapping(Type<Serializable> type)
        {
        if (val mapping := mappingByType.get(type))
            {
            return True, mapping.as(Mapping<Serializable>);
            }

        return mapper.findMapping(type);
        }

    /**
     * Find or create a mapping for the specified type.
     *
     * @param type  the type for which a Mapping is required
     *
     * @return the Mapping for the specified type
     *
     * @throws MissingMapping  if no appropriate mapping can be provided
     */
    <Serializable> Mapping<Serializable> ensureMapping(Type<Serializable> type)
        {
        if (val mapping := findMapping(type))
            {
            return mapping;
            }

        throw new MissingMapping($"Unable to identify a potential mapping for type {type}");
        }

    /**
     * A service that handles the Mapping lookups (and if necessary, creation) when the Mapping is
     * not obvious or present in the Schema.
     */
    service MappingService
        {
        construct()
            {
            allMappingsByType = new HashMap<Type, Mapping>().putAll(mappingByType);
            }

        /**
         * A lookup cache from Ecstasy type to JSON Mapping.
         */
        protected/private HashMap<Type, Mapping> allMappingsByType;

        /**
         * Search for (or create if possible) a mapping for the specified type.
         *
         * @param type  the type for the Mapping
         *
         * @return the selected Mapping
         *
         * @throws MissingMapping  if no appropriate mapping can be provided
         */
        <Serializable> conditional Mapping<Serializable> findMapping(Type<Serializable> type)
            {
            Mapping<Serializable>? backupPlan = Null;

            if (val mapping := allMappingsByType.get(type))
                {
                return True, mapping.as(Mapping<Serializable>);
                }

            // go through the original list of mappings (ordered by precedence) and see if any of
            // them could apply to the requested type; the first one to provide a specific type
            // mapping for the requested type wins, otherwise the first one that matches at all wins
            for (Mapping mapping : mappingByType.values)
                {
                if (mapping.Serializable.is(type)) // TODO GG - or should this be ".is(Type<type>)"?
                    {
                    backupPlan ?:= mapping.as(Mapping<Serializable>);

                    if (val narrowedMapping := mapping.narrow(this.Schema, type))
                        {
                        allMappingsByType.put(type, narrowedMapping);
                        return True, narrowedMapping.as(Mapping<Serializable>);
                        }
                    }
                }

            if (backupPlan != Null)
                {
                allMappingsByType.put(type, backupPlan);
                return True, backupPlan;
                }

            return False;
            }
        }


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Examine the specified name to determine if it is part of a name/value pair that holds
     * metadata information.
     *
     * @param name  a name from a name/value pair in a JSON object
     *
     * @return True iff the name/value pair should be treated as metadata
     */
    Boolean isMetadata(String name)
        {
        return name.size >= 1 && switch (name[0])
            {
            case '$': True;
            case '@': True;
            case '_': True;
            default : False;
            };
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
    }