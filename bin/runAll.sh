#!/bin/bash

java -classpath ../classes/ org.xvm.runtime.TestConnector \
    TestArray.xqiz.it tests/testTemp/array.x \
    TestInnerOuter.xqiz.it tests/TestTemp/innerOuter.x \
    TestTry.xqiz.it tests/testTemp/exceptions.x \
    TestFiles.xqiz.it tests/TestTemp/files.x \
    TestGenerics.xqiz.it tests/testTemp/generics.x \
    TestLambda.xqiz.it tests/testTemp/lambda.x \
    TestLiterals.xqiz.it tests/TestTemp/literals.x \
    TestLoops.xqiz.it tests/testTemp/loop.x \
    TestNesting.xqiz.it tests/testTemp/nesting.x \
    TestNumbers.xqiz.it tests/testTemp/numbers.x \
    TestMaps.xqiz.it tests/testTemp/maps.x \
    TestMisc.xqiz.it tests/testTemp/misc.x \
    TestProps.xqiz.it tests/testTemp/prop.x \
    TestServices.xqiz.it tests/testTemp/services.x \
    TestQueues.xqiz.it tests/testTemp/queues.x \
    TestTuples.xqiz.it tests/testTemp/tuple.x