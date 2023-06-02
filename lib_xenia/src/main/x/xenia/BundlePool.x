/**
 * The pool of call chain bundles. The bundles are shared across Dispatchers.
 */
service BundlePool {
    construct(Catalog catalog) {
        this.catalog = catalog;

        bundles     = new ChainBundle[];
        busy        = new Boolean[];
        bundleBySid = new ChainBundle?[catalog.serviceCount];
    }

    /**
     * The Catalog.
     */
    protected Catalog catalog;

    /**
     * All available bundles.
     */
    private ChainBundle[] bundles;

    /**
     * Busy flags indexed by the bundle index.
     */
    private Boolean[] busy;

    /**
     * The cache of ChainBundles indexed by the WebService id.
     */
    private ChainBundle?[] bundleBySid;

    /**
     * Allocate an idle bundle for the specified WebService and mark it as busy.
     */
    ChainBundle allocateBundle(Int wsid) {
        ChainBundle? bundle = bundleBySid[wsid];
        if (bundle != Null && !busy[bundle.index]) {
            busy[bundle.index] = True;
            return bundle;
        }

        Int lastIndex = bundles.size;
        Int firstIdle = lastIndex;

        loop:
        for (Boolean b : busy) {
            if (!b) {
                firstIdle = loop.count;
                break;
            }
        }

        if (firstIdle < lastIndex) {
            busy[firstIdle] = True;
            return bundles[firstIdle];
        }

        ChainBundle newBundle = new ChainBundle(catalog, lastIndex);

        bundles[lastIndex] = newBundle;
        busy   [lastIndex] = True;

        // this is very naive implementation; need to collect hits into a linked list for more
        // efficient WebService allocations
        if (bundle == Null) {
            bundleBySid[wsid] = newBundle;
        }
        return newBundle;
    }

    /**
     * Release a bundle.
     */
    void releaseBundle(ChainBundle bundle) {
        busy[bundle.index] = False;
    }
}