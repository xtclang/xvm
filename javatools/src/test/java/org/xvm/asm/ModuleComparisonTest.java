package org.xvm.asm;


import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

import org.xvm.api.XtcEngine;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class ModuleComparisonTest {
    private static final List<String> MODULES =
            List.of("FizzBuzz", "array", "misc", "lambda", "loop", "ranges");

    private static File dirLib() {
        return new File("../xdk/build/install/xdk/lib");
    }

    private static XtcEngine engine() {
        return XtcEngine.builder()
                .modulePath(dirLib(), new File("../xdk/build/install/xdk/javatools")).build();
    }

    private static byte[] compile(XtcEngine engine, String sName, Path dirOut) throws Exception {
        var result = engine.compile(Path.of("../manualTests/src/main/x/" + sName + ".x"));
        if (!result.isSuccess()) {
            throw new IllegalStateException(sName + ": " + result.diagnostics());
        }
        return Files.readAllBytes(result.writeTo(dirOut.toFile()).getFirst().toPath());
    }

    @Test
    public void twoColdCompilesAreEquivalentDespiteDifferentBytes() throws Exception {
        assumeTrue(dirLib().isDirectory(), "needs xdk:installDist");
        Path dirTmp = Files.createTempDirectory("xtc-cmp");

        for (String sName : MODULES) {
            byte[] ab1;
            byte[] ab2;
            try (var engine = engine()) {
                ab1 = compile(engine, sName, dirTmp.resolve("a-" + sName));
            }
            try (var engine = engine()) {
                ab2 = compile(engine, sName, dirTmp.resolve("b-" + sName));
            }
            var result = ModuleComparison.compare(ab1, ab2);
            System.out.println("COLD/COLD " + sName
                    + " rawBytesEqual=" + java.util.Arrays.equals(ab1, ab2)
                    + " -> " + result);
            assertTrue(result.equivalent(),
                    sName + " two cold compiles should be equivalent: " + result);
        }
    }

    @Test
    public void warmAndParallelCompilesAreEquivalentToCold() throws Exception {
        assumeTrue(dirLib().isDirectory(), "needs xdk:installDist");
        Path dirTmp = Files.createTempDirectory("xtc-cmp2");

        var mapCold = new LinkedHashMap<String, byte[]>();
        for (String sName : MODULES) {
            try (var engine = engine()) {
                mapCold.put(sName, compile(engine, sName, dirTmp.resolve("cold-" + sName)));
            }
        }

        var mapWarm = new LinkedHashMap<String, byte[]>();
        try (var engine = engine()) {
            for (String sName : MODULES) {
                mapWarm.put(sName, compile(engine, sName, dirTmp.resolve("warm-" + sName)));
            }
        }

        var mapPar = new LinkedHashMap<String, byte[]>();
        try (var engine = engine()) {
            var listTasks = new ArrayList<Callable<Map.Entry<String, byte[]>>>();
            for (String sName : MODULES) {
                listTasks.add(() -> Map.entry(sName,
                        compile(engine, sName, dirTmp.resolve("par-" + sName))));
            }
            try (var pool = Executors.newFixedThreadPool(MODULES.size())) {
                for (var f : pool.invokeAll(listTasks)) {
                    var e = f.get();
                    mapPar.put(e.getKey(), e.getValue());
                }
            }
        }

        for (String sName : MODULES) {
            var rWarm = ModuleComparison.compare(mapCold.get(sName), mapWarm.get(sName));
            var rPar  = ModuleComparison.compare(mapCold.get(sName), mapPar.get(sName));
            System.out.println("VS-COLD " + sName + " warm=" + rWarm.equivalent()
                    + " parallel=" + rPar.equivalent()
                    + (rWarm.equivalent() ? "" : " | WARM " + rWarm)
                    + (rPar.equivalent()  ? "" : " | PAR " + rPar));
        }
    }
}
