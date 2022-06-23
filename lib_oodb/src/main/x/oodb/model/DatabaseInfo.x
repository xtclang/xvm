/**
 * A simple `const` implementation of `DBInfo`.
 */
const DatabaseInfo(String  name,
                   Version version,
                   Time    created,
                   Time    modified,
                   Time    accessed,
                   Boolean readable,
                   Boolean writable,
                   Int     size,
                  )
        implements DBInfo
    {
    /**
     * Create a copy of this `const` with only the specified properties modified.
     */
    DatabaseInfo with(String?  name     = Null,
                      Version? version  = Null,
                      Time?    created  = Null,
                      Time?    modified = Null,
                      Time?    accessed = Null,
                      Boolean? readable = Null,
                      Boolean? writable = Null,
                      Int?     size     = Null,
                     )
        {
        return new DatabaseInfo(name     = name     ?: this.name,
                                version  = version  ?: this.version,
                                created  = created  ?: this.created,
                                modified = modified ?: this.modified,
                                accessed = accessed ?: this.accessed,
                                readable = readable ?: this.readable,
                                writable = writable ?: this.writable,
                                size     = size     ?: this.size,
                               );
        }
    }
