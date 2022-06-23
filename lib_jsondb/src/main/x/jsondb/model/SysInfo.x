import Catalog.Status as CatStat;

/**
 * Database system status record, stored in the root database directory as `/sys.json`.
 */
const SysInfo(CatStat  status,
              Time     stampedBy,
              Time     updated,
              Version? version)
    {
    /**
     * Create a SysInfo for the specified Catalog.
     *
     * @param catalog  the Catalog
     */
    construct(Catalog catalog)
        {
        @Inject Clock clock;
        construct SysInfo(catalog.status, catalog.timestamp, clock.now, catalog.version);
        }

    /**
     * Create a copy of this `const` with only the specified properties modified.
     *
     * @param status     (optional) the new status
     * @param stampedBy  (optional) the timestamp from the Catalog that created the status
     * @param updated    (optional) the time at which the status was last updated
     * @param version    (optional) the new version
     */
    SysInfo with(CatStat? status    = Null,
                 Time?    stampedBy = Null,
                 Time?    updated   = Null,
                 Version? version   = Null)
        {
        return new SysInfo(status    = status    ?: this.status,
                           stampedBy = stampedBy ?: this.stampedBy,
                           updated   = updated   ?: this.updated,
                           version   = version   ?: this.version);
        }

    /**
     * @return a new SysInfo with the timestamp updated to the current time
     */
    SysInfo touch()
        {
        @Inject Clock clock;
        return with(updated=clock.now);
        }
    }
