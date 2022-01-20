import json.Mapping;
import json.Lexer;
import json.Lexer.Token;

import model.DBObjectInfo;


/**
 * The disk storage implementation for a database schema.
 */
@Concurrent
service SchemaStore
        extends ObjectStore
    {
    construct(Catalog          catalog,
              DBObjectInfo     info,
             )
        {
        construct ObjectStore(catalog, info);
        }

    @Override
    Boolean quickScan()
        {
        return True;
        }
    }
