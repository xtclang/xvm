import json.Mapping;
import json.Lexer;
import json.Lexer.Token;

import model.DboInfo;


/**
 * The disk storage implementation for a database schema.
 */
@Concurrent
service SchemaStore
        extends ObjectStore {

    construct(Catalog catalog,
              DboInfo info,
             ) {
        construct ObjectStore(catalog, info);
    }

    @Override
    Boolean quickScan() {
        return True;
    }
}