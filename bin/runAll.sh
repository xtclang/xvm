#!/bin/bash

START_TIME=$(date +%s)

# Note: TestLiterals depends on existence of TestFiles.xtc (files.x being compiled)

if [ "$1" = "-f" ]; then
    rm -f Ecstasy.xtc
fi

java -classpath ../classes/ org.xvm.runtime.TestConnector \
    TestArray.xqiz.it tests/testTemp/array.x \
    TestDefAsn.xqiz.it tests/testTemp/defasn.x \
    TestTry.xqiz.it tests/testTemp/exceptions.x \
    TestGenerics.xqiz.it tests/testTemp/generics.x \
    TestInnerOuter.xqiz.it tests/TestTemp/innerOuter.x \
    TestFiles.xqiz.it tests/TestTemp/files.x \
    TestLambda.xqiz.it tests/testTemp/lambda.x \
    TestLiterals.xqiz.it tests/TestTemp/literals.x \
    TestLoops.xqiz.it tests/testTemp/loop.x \
    TestNesting.xqiz.it tests/testTemp/nesting.x \
    TestNumbers.xqiz.it tests/testTemp/numbers.x \
    TestProps.xqiz.it tests/testTemp/prop.x \
    TestMaps.xqiz.it tests/testTemp/maps.x \
    TestMisc.xqiz.it tests/testTemp/misc.x \
    TestQueues.xqiz.it tests/testTemp/queues.x \
    TestServices.xqiz.it tests/testTemp/services.x \
    TestReflection.xqiz.it tests/testTemp/reflect.x \
    TestTuples.xqiz.it tests/testTemp/tuple.x

END_TIME=$(date +%s)
echo "Elapsed $(($END_TIME - $START_TIME)) seconds" 1>&2