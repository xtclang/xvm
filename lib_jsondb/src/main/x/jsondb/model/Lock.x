/**
 * Database lock record, stored (for the duration of a state change) in the root database directory
 * as `/sys.lock`.
 */
const Lock(DateTime stampedBy,
           DateTime created)
    {
    construct(Catalog catalog)
        {
        @Inject Clock clock;
        construct Lock(catalog.timestamp, clock.now);
        }
    }
