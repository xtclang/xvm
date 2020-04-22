#!/bin/bash

START_TIME=$(date +%s)

# Note: TestLiterals depends on existence of TestFiles.xtc (files.x being compiled)

java -Xms1024m -Xmx1024m -ea -Dxvm.parallelism=1 -classpath ../classes/ org.xvm.tool.Runner \
    -L . \
    tests/manual/TestRunner.xtc \
    tests/manual/TestAnnotations.xtc \
    tests/manual/TestArray.xtc \
    tests/manual/TestDefAsn.xtc \
    tests/manual/TestTry.xtc \
    tests/manual/TestGenerics.xtc \
    tests/manual/TestInnerOuter.xtc \
    tests/manual/TestFiles.xtc \
    tests/manual/TestIO.xtc \
    tests/manual/TestLambda.xtc \
    tests/manual/TestLiterals.xtc \
    tests/manual/TestLoops.xtc \
    tests/manual/TestNesting.xtc \
    tests/manual/TestNumbers.xtc \
    tests/manual/TestProps.xtc \
    tests/manual/TestMaps.xtc \
    tests/manual/TestMisc.xtc \
    tests/manual/TestQueues.xtc \
    tests/manual/TestServices.xtc \
    tests/manual/TestReflection.xtc \
    tests/manual/TestTuples.xtc

END_TIME=$(date +%s)
echo "Elapsed $(($END_TIME - $START_TIME)) seconds" 1>&2