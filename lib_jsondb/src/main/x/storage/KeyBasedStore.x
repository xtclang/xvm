import json.Lexer.Token;
import json.Mapping;
import json.ObjectInputStream;
import json.Parser;


/**
 * Common functionality for key-based json stores.
 */
mixin KeyBasedStore<Key>
        into ObjectStore
    {
    /**
     * The files names used to store the data for the keys. For Large model, this map will be
     * actively purged, retaining only most recently/frequently used keys. For all other models, it
     * contains all existing keys (lazily added).
     */
    protected Map<Key, String> fileNames = new HashMap();

    /**
     * Get the file used to store data for the specified key.
     */
    protected String nameForKey(Key key)
        {
        return fileNames.computeIfAbsent(key, () ->
            {
            return $"{computeURI(key)}.json";
            });

        private String computeURI(Key key)
            {
            String name = key.toString();

            // TODO remove illegal chars
            Int size = name.size;
            return switch (size)
                {
                case 0   : assert;
                case 1, 2: name[0].toString();
                default  : $"{name[0]}{name[size-1]}";
                };
            }
        }

    /**
     * Collect all file name that are affected by the specifies seals.
     */
    protected Map<String, Int> collectFileNames(SkiplistMap<Int, Token[]> sealsByTxId,
                                                String keyName, Mapping<Key> keyMapping,
                                                json.Schema jsonSchema)
        {
        Map<String, Int> latestTxByFile = new HashMap();

        for ((Int txId, Token[] sealTokens) : sealsByTxId)
            {
            using (val sealParser = new Parser(sealTokens.iterator()))
                {
                using (val changeArrayParser = sealParser.expectArray())
                    {
                    while (!changeArrayParser.eof)
                        {
                        using (val changeParser = changeArrayParser.expectObject())
                            {
                            Key key;

                            changeParser.expectKey(keyName);

                            using (ObjectInputStream stream =
                                    new ObjectInputStream(jsonSchema, changeParser))
                                {
                                key = keyMapping.read(stream.ensureElementInput());
                                }

                            latestTxByFile.put(nameForKey(key), txId);
                            }
                        }
                    }
                }
            }
        return latestTxByFile;
        }

    /**
     * Append a json record to the specified StringBuffer.
     */
    protected void appendChange(StringBuffer buf,  Int txId,
                                String keyName,    Token[] keyTokens,
                                String changeName, Token[] changeTokens)
        {
        buf.append("\n{\"tx\":")
           .append(txId)
           .add(',').add(' ').add('"')
           .append(keyName)
           .add('"').add(':');

        for (Token token : keyTokens)
            {
            token.appendTo(buf);
            }

        if (changeTokens.size > 0)
            {
            buf.add(',').add(' ').add('"')
               .append(changeName)
               .add('"').add(':');

            for (Token token : changeTokens)
                {
                token.appendTo(buf);
                }
            buf.add('}').add(',');
            }
        }
    }