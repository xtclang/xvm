#!/bin/bash

START_TIME=$(date +%s)

# Note: TestLiterals depends on existence of TestFiles.xtc (files.x being compiled)

if [ "$1" = "-f" ]; then
    rm -f Ecstasy.xtc
fi

java -Xms1024m -Xmx1024m -ea -classpath ../classes/ org.xvm.runtime.TestConnector \
    TestAnnotations tests/manual/annos.x \
    TestArray tests/manual/array.x \
    TestDefAsn tests/manual/defasn.x \
    TestTry tests/manual/exceptions.x \
    TestGenerics tests/manual/generics.x \
    TestInnerOuter tests/manual/innerOuter.x \
    TestFiles tests/manual/files.x \
    TestIO tests/manual/IO.x \
    TestLambda tests/manual/lambda.x \
    TestLiterals tests/manual/literals.x \
    TestLoops tests/manual/loop.x \
    TestNesting tests/manual/nesting.x \
    TestNumbers tests/manual/numbers.x \
    TestProps tests/manual/prop.x \
    TestMaps tests/manual/maps.x \
    TestMisc tests/manual/misc.x \
    TestQueues tests/manual/queues.x \
    TestServices tests/manual/services.x \
    TestReflection tests/manual/reflect.x \
    TestTuples tests/manual/tuple.x

END_TIME=$(date +%s)
echo "Elapsed $(($END_TIME - $START_TIME)) seconds" 1>&2