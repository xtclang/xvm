import json.Lexer.Token;
import json.Mapping;
import json.ObjectInputStream;
import json.Parser;

import TxManager.NO_TX;


/**
 * Common functionality for key-based json stores.
 */
mixin KeyBasedStore<Key extends Hashable>
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
     * Analyze all store files for provided seals and reconstruct their up-to-date contents.
     *
     * @param sealsByTxId  a Map, keyed and ordered by transaction id, with the seal information
     * @param keyName      the name of the key in the json docs used by this store
     *
     * @return True iff recovery was successful
     * @return (conditional) a map of reconstructed StringBuffers per file name
     * @return (conditional) a map of last transaction id per file name
     */
    conditional (Map<String, StringBuffer>, Map<String, Int>) recoverContents(
            SkiplistMap<Int, Token[]> sealsByTxId,
            String keyName, Mapping<Key> keyMapping, json.Schema jsonSchema
            )
        {
        // first, collect all file name that are affected by the specifies seals.
        Map<String, Int> lastSealByFile = new HashMap();

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

                            lastSealByFile.put(nameForKey(key), txId);
                            }
                        }
                    }
                }
            }

        assert Int firstSeal := sealsByTxId.first();

        // now find the last valid transaction in each file by parsing as far as we can

        Map<String, StringBuffer> recoveredContents = new HashMap();
        Map<String, Int>          lastTxInFile      = new HashMap();

        for ((String fileName, Int lastSeal) : lastSealByFile)
            {
            File file = dataDir.fileFor(fileName);

            StringBuffer buf        = new StringBuffer();
            Boolean      exists     = file.exists;
            Int          lastInFile = NO_TX;

            if (exists)
                {
                // find the last valid transaction in the file by parsing as far as we can
                Byte[]  bytes      = file.contents;
                String  jsonStr    = bytes.unpackUtf8();
                Parser  fileParser = new Parser(jsonStr.toReader());
                Int     endOffset  = 0;
                Boolean corrupted  = False;

                try
                    {
                    using (val arrayParser = fileParser.expectArray())
                        {
                        while (!arrayParser.eof)
                            {
                            Token endToken;
                            Int   currentTx;
                            using (val txParser = arrayParser.expectObject())
                                {
                                txParser.expectKey("tx");
                                currentTx = txParser.expectInt();

                                txParser.skipRemaining();
                                endToken = txParser.peek();
                                }

                            assert endToken.id == ObjectExit;
                            lastInFile = currentTx;
                            endOffset  = endToken.end.offset;
                            }
                        }
                    }
                catch (Exception e)
                    {
                    corrupted = True;
                    }

                if (lastInFile > lastSeal)
                    {
                    // something is really wrong; we should never be ahead of the txlog
                    catalog.log($|File {fileName} contains transaction {lastInFile},
                                 | which is beyond the latest recovered transaction {lastSeal}
                               );
                    return False;
                    }

                if (lastInFile == lastSeal)
                    {
                    // this file doesn't need any recovery; go to the next one
                    continue;
                    }

                if (corrupted && lastInFile < firstSeal)
                    {
                    catalog.log($|File {fileName} is corrupted beyond transaction {lastInFile} and
                                 | may contain transaction data preceeding the earliest recovered
                                 | transaction {firstSeal}
                               );
                    return False;
                    }

                buf.append(jsonStr[0..endOffset)).add(',');
                }
            else
                {
                buf.add('[');
                }

            recoveredContents.put(fileName, buf);
            lastTxInFile     .put(fileName, lastInFile);
            }

        return True, recoveredContents, lastTxInFile;
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