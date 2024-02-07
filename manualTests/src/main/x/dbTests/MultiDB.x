@Database
module MultiDB {
    package oodb import oodb.xtclang.org;

    import oodb.*;

    interface MainSchema
            extends RootSchema {
        @RO Counter counter;
        @RO ChildSchema child;
    }

    mixin Counter into DBCounter {
        Int tick() {
            return next();
        }
    }

    interface ChildSchema
            extends DBSchema {
        @RO Counter counter;

        static mixin Counter into DBCounter {
            Int tick() {
                return next(2);
            }
        }
    }
}