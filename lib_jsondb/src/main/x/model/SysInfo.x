/**
 * Database system status record, stored in the root database directory as `/sys.json`.
 */
const SysInfo(Catalog.Status status,
              DateTime       timestamp,
              Version        version)
    {
    /**
     * Create a copy of this `const` with only the specified properties modified.
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
     *
     *
     * @return
     */
    SysInfo touch()
        {
        @Inject Clock clock;
        return with(timestamp=clock.now);
        }
    }
