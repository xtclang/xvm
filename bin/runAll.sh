#!/bin/bash

START_TIME=$(date +%s)

# Note: TestLiterals depends on existence of TestFiles.xtc (files.x being compiled)

if [ "$1" = "-f" ]; then
    rm -f Ecstasy.xtc
fi

java -classpath ../classes/ org.xvm.runtime.TestConnector \
    TestArray.xqiz.it tests/manual/array.x \
    TestDefAsn.xqiz.it tests/manual/defasn.x \
    TestTry.xqiz.it tests/manual/exceptions.x \
    TestGenerics.xqiz.it tests/manual/generics.x \
    TestInnerOuter.xqiz.it tests/manual/innerOuter.x \
    TestFiles.xqiz.it tests/manual/files.x \
    TestLambda.xqiz.it tests/manual/lambda.x \
    TestLiterals.xqiz.it tests/manual/literals.x \
    TestLoops.xqiz.it tests/manual/loop.x \
    TestNesting.xqiz.it tests/manual/nesting.x \
    TestNumbers.xqiz.it tests/manual/numbers.x \
    TestProps.xqiz.it tests/manual/prop.x \
    TestMaps.xqiz.it tests/manual/maps.x \
    TestMisc.xqiz.it tests/manual/misc.x \
    TestQueues.xqiz.it tests/manual/queues.x \
    TestServices.xqiz.it tests/manual/services.x \
    TestReflection.xqiz.it tests/manual/reflect.x \
    TestTuples.xqiz.it tests/manual/tuple.x

END_TIME=$(date +%s)
echo "Elapsed $(($END_TIME - $START_TIME)) seconds" 1>&2