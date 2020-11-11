/**
 * Database system status record, stored in the root database directory as `/sys.json`.
 */
const SysInfo(Catalog.Status status,
              DateTime       timestamp,
              Version        version)
    {
    /**
     * Create a copy of this `const` with only the specified properties modified.
     *
     * @param status     (optional) the new status
     * @param timestamp  (optional) the new timestamp
     * @param version    (optional) the new version
     */
    SysInfo with(Catalog.Status? status    = Null,
                 DateTime?       timestamp = Null,
                 Version?        version   = Null)
        {
        return new SysInfo(status    = status    ?: this.status,
                           timestamp = timestamp ?: this.timestamp,
                           version   = version   ?: this.version);
        }

    /**
     * @return a new SysInfo with the timestamp updated to the current time
     */
    SysInfo touch()
        {
        @Inject Clock clock;
        return with(timestamp=clock.now);
        }
    }
