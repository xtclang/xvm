package org.xtclang.ecstasy.fs;

import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;

import org.xtclang.ecstasy.nEnum;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * The native implementation of {@code ecstasy.fs.File.x}.
 */
public interface File extends FileNode {

    class ReadOption extends nEnum {
        private ReadOption(long ordinal, String name) {
            super(null);
            $ordinal = ordinal;
            $name    = name;
        }

        public final long   $ordinal;
        public final String $name;

        @Override
        public String name$get(Ctx ctx) {
            return $name;
        }

        @Override
        public long ordinal$get$p(Ctx ctx) {
            return $ordinal;
        }

        @Override
        public java.lang.String toString() {
            return "ReadOption." + $name.toString();
        }

        public static class NoRead extends ReadOption {
            public static final int $ORDINAL = 0;
            public static final NoRead $INSTANCE = new NoRead();
            public NoRead() {
                super($ORDINAL, String.of(null, "NoRead"));
            }
        }

        public static class Read extends ReadOption {
            public static final int $ORDINAL = 1;
            public static final Read $INSTANCE = new Read();
            public Read() {
                super($ORDINAL, String.of(null, "Read"));
            }
        }

        public static class Exclusive extends ReadOption {
            public static final int $ORDINAL = 2;
            public static final Exclusive $INSTANCE = new Exclusive();
            public Exclusive() {
                super($ORDINAL, String.of(null, "Exclusive"));
            }
        }
    }

    class WriteOption extends nEnum {
        private WriteOption(long ordinal, String name, OpenOption opt) {
            super(null);
            $ordinal = ordinal;
            $name    = name;
            $opt     = opt;
        }

        public final long       $ordinal;
        public final String     $name;
        public final OpenOption $opt;

        @Override
        public String name$get(Ctx ctx) {
            return $name;
        }

        @Override
        public long ordinal$get$p(Ctx ctx) {
            return $ordinal;
        }

        @Override
        public java.lang.String toString() {
            return "WriteOption." + $name.toString();
        }

        public static class Write extends WriteOption {
            public static Write $INSTANCE = new Write();
            public Write() {
                super(0, String.of(null, "Write"), StandardOpenOption.WRITE);
            }
        }

        public static class Ensure extends WriteOption {
            public static Ensure $INSTANCE = new Ensure();
            public Ensure() {
                super(1, String.of(null, "Ensure"), StandardOpenOption.CREATE);
            }
        }

        public static class Create extends WriteOption {
            public static Create $INSTANCE = new Create();
            public Create() {
                super(2, String.of(null, "Create"), StandardOpenOption.CREATE_NEW);
            }
        }

        public static class Sparse extends WriteOption {
            public static Sparse $INSTANCE = new Sparse();
            public Sparse() {
                super(3, String.of(null, "Sparse"), StandardOpenOption.SPARSE);
            }
        }

        public static class Temp extends WriteOption {
            public static Temp $INSTANCE = new Temp();
            public Temp() {
                super(4, String.of(null, "Temp"), StandardOpenOption.DELETE_ON_CLOSE);
            }
        }

        public static class Truncate extends WriteOption {
            public static Truncate $INSTANCE = new Truncate();
            public Truncate() {
                super(5, String.of(null, "Truncate"), StandardOpenOption.TRUNCATE_EXISTING);
            }
        }

        public static class Append extends WriteOption {
            public static Append $INSTANCE = new Append();
            public Append() {
                super(6, String.of(null, "Append"), StandardOpenOption.APPEND);
            }
        }

        public static class Exclusive extends WriteOption {
            public static Exclusive $INSTANCE = new Exclusive();
            public Exclusive() {
                super(7, String.of(null, "Exclusive"), null);
            }
        }

        public static class SyncData extends WriteOption {
            public static SyncData $INSTANCE = new SyncData();
            public SyncData() {
                super(8, String.of(null, "SyncData"), StandardOpenOption.DSYNC);
            }
        }

        public static class SyncAll extends WriteOption {
            public static SyncAll $INSTANCE = new SyncAll();
            public SyncAll() {
                super(9, String.of(null, "SyncAll"), StandardOpenOption.SYNC);
            }
        }
    }
}
