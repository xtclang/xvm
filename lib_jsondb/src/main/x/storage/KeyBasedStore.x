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
    }