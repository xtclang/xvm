import json.Mapping;
import json.Lexer;
import json.Lexer.Token;

import model.DBObjectInfo;


/**
 * The disk storage implementation for a database schema.
 */
service SchemaStore
        extends ObjectStore
    {
    construct(Catalog          catalog,
              DBObjectInfo     info,
              Appender<String> errs,
             )
        {
        construct ObjectStore(catalog, info, errs);
        }

    @Override
    Boolean quickScan()
        {
        return True;
        }
    }
