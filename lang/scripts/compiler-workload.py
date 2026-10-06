#!/usr/bin/env python3
"""Measure the packaged compiler on real graphs and generated fixtures without editing user sources.

The sampler observes heap and queue metadata over the same stdio connection. Optional RSS sampling
and post-GC checkpoints supplement it. Timings include transport/debounce; sampled peaks are lower
bounds, and post-GC heap includes intentional caches rather than proving object reachability.
Run this separately from other compiler workloads to keep timings interpretable.
"""

import argparse
import collections
import concurrent.futures
import hashlib
import json
import math
import pathlib
import subprocess
import sys
import threading
import time
import urllib.parse
from types import SimpleNamespace


class RpcError(RuntimeError):
    def __init__(self, error):
        super().__init__(str(error))
        self.code = error["code"]


class Session:
    def __init__(self, args, directory, modules):
        self.args, self.directory, self.modules = args, directory, modules
        self.lock = threading.Lock()
        self.pending = {}
        self.next_id = 0
        self.timings = []
        self.samples = []
        self.sampling_errors = []
        self.stop_sampling = threading.Event()
        self.stderr = (directory / "stderr.log").open("wb")
        self.process = subprocess.Popen([
            args.java, "--enable-native-access=ALL-UNNAMED", f"-Xmx{args.heap}",
            f"-Duser.home={directory}", f"-Dxtc.trace.directory={directory / 'trace'}",
            "-jar", str(args.jar),
        ], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=self.stderr)
        self.reader = threading.Thread(target=self._read, daemon=True)
        self.sampler = threading.Thread(target=self._sample, daemon=True)
        self.reader.start()

    def _write_locked(self, message):
        payload = json.dumps(message).encode("utf-8")
        self.process.stdin.write(f"Content-Length: {len(payload)}\r\n\r\n".encode() + payload)
        self.process.stdin.flush()

    def notify(self, method, params):
        with self.lock:
            self._write_locked({"jsonrpc": "2.0", "method": method, "params": params})

    def request(self, method, params):
        future = concurrent.futures.Future()
        with self.lock:
            self.next_id += 1
            request_id = self.next_id
            self.pending[request_id] = future
            self._write_locked({"jsonrpc": "2.0", "id": request_id, "method": method, "params": params})
        return request_id, future

    def call(self, method, params=None, *, record=True):
        started = time.perf_counter()
        _, future = self.request(method, params)
        try:
            return future.result(self.args.timeout)
        finally:
            if record:
                self.timings.append({"method": method, "ms": (time.perf_counter() - started) * 1000})

    def _read(self):
        try:
            while True:
                headers = {}
                while (line := self.process.stdout.readline()) not in (b"\r\n", b"\n"):
                    if not line:
                        raise EOFError("Server closed stdout")
                    key, value = line.decode("ascii").split(":", 1)
                    headers[key.lower()] = value.strip()
                size = int(headers["content-length"])
                payload = self.process.stdout.read(size)
                if len(payload) != size:
                    raise EOFError("Truncated protocol message")
                message = json.loads(payload)
                if "method" in message:
                    if "id" in message:
                        method = message["method"]
                        if method == "workspace/configuration":
                            result = [{"sourceModules": self.modules} if item.get("section") == "xtc.compiler" else {}
                                      for item in message["params"]["items"]]
                        elif method == "workspace/workspaceFolders":
                            result = [{"uri": self.args.workspace.as_uri(), "name": "workload"}]
                        elif method == "workspace/applyEdit":
                            result = {"applied": False, "failureReason": "Read-only workload host"}
                        else:
                            result = None
                        with self.lock:
                            self._write_locked({"jsonrpc": "2.0", "id": message["id"], "result": result})
                else:
                    with self.lock:
                        future = self.pending.pop(message["id"])
                    if "error" in message:
                        future.set_exception(RpcError(message["error"]))
                    else:
                        future.set_result(message.get("result"))
        except Exception as error:
            with self.lock:
                pending, self.pending = self.pending, {}
            for future in pending.values():
                future.set_exception(error)

    def _sample(self):
        next_rss = 0.0
        with (self.directory / "samples.jsonl").open("w") as output:
            while not self.stop_sampling.is_set():
                try:
                    status = self.call("xtc/languageServiceStatus", record=False)
                    queue = status["compilerQueue"]
                    assert queue["queueSize"] == len(queue["queuedJobs"])
                    sample = {"time": time.monotonic(), "heap": status["heap"], "queue": queue}
                    if self.args.sample_rss and time.monotonic() >= next_rss:
                        rss = subprocess.run(["ps", "-o", "rss=", "-p", str(self.process.pid)],
                                             check=True, capture_output=True, text=True, timeout=5)
                        sample["rssBytes"] = int(rss.stdout.strip()) * 1024
                        next_rss = time.monotonic() + 1
                    self.samples.append(sample)
                    output.write(json.dumps(sample) + "\n")
                    output.flush()
                except Exception as error:
                    self.sampling_errors.append(str(error))
                    return
                self.stop_sampling.wait(0.2)

    def collect_heap(self, cycle):
        """Collect live heap between edits; never include diagnostic pauses in request latency."""
        # GC.class_histogram performs a full GC unless -all is specified. Its live-object counts
        # distinguish retained classes from allocation churn without retaining a full heap dump.
        command = "GC.class_histogram" if self.args.heap_histograms else "GC.run"
        collected = subprocess.run([self.args.jcmd, str(self.process.pid), command],
                                   check=True, capture_output=True, text=True, timeout=30)
        if self.args.heap_histograms:
            assert "Total" in collected.stdout, collected.stdout
            (self.directory / f"heap-{cycle}.txt").write_text(collected.stdout)
        else:
            assert "Command executed successfully" in collected.stdout, collected.stdout
        return self.call("xtc/languageServiceStatus", record=False)["heap"]

    def initialize(self):
        self.call("initialize", {
            "processId": None,
            "workspaceFolders": [{"uri": self.args.workspace.as_uri(), "name": "workload"}],
            "capabilities": {"workspace": {"configuration": True, "diagnostics": {"refreshSupport": True}},
                             "textDocument": {"diagnostic": {"dynamicRegistration": False}}},
            "initializationOptions": {"xtcCompiler": {"sourceModules": self.modules}},
        })
        self.notify("initialized", {})
        status = self.call("xtc/languageServiceStatus")
        assert status["compilerQueue"] is not None, "Build with -Plsp.adapter=compiler"
        self.sampler.start()

    def finish(self, disconnect=False):
        self.stop_sampling.set()
        if self.sampler.ident is not None:
            self.sampler.join(self.args.timeout + 1)
            assert not self.sampler.is_alive(), "Sampler did not finish"
        assert not self.sampling_errors, self.sampling_errors
        if disconnect:
            self.process.stdin.close()
        else:
            self.call("shutdown")
            self.notify("exit", None)
        code = self.process.wait(15)
        assert code == (1 if disconnect else 0), f"Unexpected server exit: {code}"
        return code

    def cleanup(self):
        # Cleanup never turns a failure to exit into a successful lifecycle assertion.
        self.stop_sampling.set()
        if self.process.poll() is None:
            self.process.kill()
        self.process.wait(15)
        self.reader.join(5)
        if self.sampler.ident is not None:
            self.sampler.join(self.args.timeout + 1)
        self.process.stdin.close()
        self.process.stdout.close()
        self.stderr.close()


def inventory(root):
    return {str(file.relative_to(root)): hashlib.sha256(file.read_bytes()).hexdigest()
            for file in sorted(root.rglob("*.x")) if ".git" not in file.parts}


def percentiles(values):
    ordered = sorted(values)
    return {"count": len(ordered), "p50Ms": ordered[math.ceil(len(ordered) * 0.5) - 1],
            "p95Ms": ordered[math.ceil(len(ordered) * 0.95) - 1], "maxMs": ordered[-1]}


def trace_statistics(directory):
    timings = collections.defaultdict(list)
    phases = collections.defaultdict(list)
    previous = {}
    max_active = 0
    largest_queue = []
    for file in sorted((directory / "trace").glob("*.jsonl")):
        for line in file.open():
            event = json.loads(line)
            max_active = max(max_active, event.get("activeApiThreads", 0))
            jobs = event.get("queuedJobs", [])
            if len(jobs) > len(largest_queue):
                largest_queue = jobs
            if event.get("kind") == "javatools" and event.get("event") == "end":
                timings[event["operation"]].append(event["elapsedMs"])
            if event.get("kind") == "lsp-query" and event["event"] in (
                    "start", "analysis-ready", "started", "backend-complete", "converted"):
                phase = event["event"]
                if phase != "start" and event["id"] in previous:
                    phases[f"{event['operation']}:{phase}"].append(event["elapsedMs"] - previous[event["id"]])
                previous[event["id"]] = event["elapsedMs"]
            if event.get("writeMs") is not None:
                phases[f"{event['operation']}:write"].append(event["writeMs"])
    return {"apiTimings": {name: percentiles(values) for name, values in sorted(timings.items())},
            "queryPhaseTimings": {name: percentiles(values) for name, values in sorted(phases.items())},
            "maxActiveApiThreads": max_active, "largestTracedQueue": largest_queue,
            "traceDirectory": str(directory / "trace")}


def run(args):
    graph = json.loads(args.graph.read_text())
    modules = [{**module, "uri": (args.workspace / module["uri"]).resolve().as_uri(),
                **({"resourceRoots": [(args.workspace / root).resolve().as_uri() for root in module["resourceRoots"]]}
                   if "resourceRoots" in module else {})} for module in graph["modules"]]
    source_file = args.workspace / graph["document"]
    source = source_file.read_text()
    before = inventory(args.workspace)
    offset = source.index(graph["anchor"])
    position = {"line": source[:offset].count("\n"),
                "character": len(source[:offset].rsplit("\n", 1)[-1].encode("utf-16-le")) // 2}
    document = {"uri": source_file.as_uri()}
    report = {"workspace": str(args.workspace), "sourceFiles": len(before), "modules": modules,
              "cyclesPerSession": args.cycles, "sessions": [], "heapLimit": args.heap,
              "gcEvery": args.gc_every, "heapHistograms": args.heap_histograms,
              "jarSha256": hashlib.sha256(args.jar.read_bytes()).hexdigest(),
              "limits": "Sampled peaks are lower bounds; post-GC heap includes intentional caches. This is a controlled workload, not multi-hour interactive acceptance."}
    try:
        for restart in range(args.restarts + 1):
            directory = args.output / f"session-{restart}"
            directory.mkdir()
            session = Session(args, directory, modules)
            result = {"pid": session.process.pid, "cancellations": 0, "exit": None, "heapCheckpoints": []}
            report["sessions"].append(result)
            try:
                session.initialize()
                reports = session.call("workspace/diagnostic", {})["items"]
                errors = [item for item in reports if any(d.get("severity", 1) == 1 for d in item.get("items", []))]
                assert not errors, errors
                result["diagnosticDocuments"] = len(reports)
                session.notify("textDocument/didOpen", {"textDocument": {**document, "languageId": "xtc", "version": 1, "text": source}})
                for cycle in range(args.cycles):
                    session.notify("textDocument/didChange", {"textDocument": {**document, "version": cycle + 2},
                                   "contentChanges": [{"text": source + f"\n// workload {restart}:{cycle}\n"}]})
                    request_id, pending = session.request("textDocument/references", {
                        "textDocument": document, "position": position, "context": {"includeDeclaration": True}})
                    assert not pending.done(), "Cancellation race not exercised"
                    session.notify("$/cancelRequest", {"id": request_id})
                    try:
                        pending.result(args.timeout)
                        raise AssertionError("Reference completed before cancellation took effect")
                    except RpcError as error:
                        assert error.code == -32800, error
                        result["cancellations"] += 1
                    assert session.call("textDocument/documentSymbol", {"textDocument": document})
                    assert session.call("textDocument/hover", {"textDocument": document, "position": position})
                    diagnostics = session.call("textDocument/diagnostic", {"textDocument": document})
                    assert not diagnostics.get("items"), diagnostics
                    if args.gc_every and (cycle + 1) % args.gc_every == 0:
                        result["heapCheckpoints"].append({"cycle": cycle + 1, "heap": session.collect_heap(cycle + 1)})
                    if (cycle + 1) % 100 == 0:
                        print(f"session {restart + 1}: {cycle + 1}/{args.cycles} edit/cancel cycles", flush=True)
                session.notify("textDocument/didClose", {"textDocument": document})
                result["exit"] = session.finish(disconnect=restart % 2 == 1)
                result["status"] = "passed"
                print(f"session {restart + 1}/{args.restarts + 1}: PID {session.process.pid} exited, {args.cycles} cycles", flush=True)
            finally:
                result["timings"] = {method: percentiles([t["ms"] for t in session.timings if t["method"] == method])
                                     for method in sorted({t["method"] for t in session.timings})}
                result["sampledPeakHeapBytes"] = max((s["heap"]["usedBytes"] for s in session.samples), default=0)
                result["sampledPeakRssBytes"] = max((s.get("rssBytes", 0) for s in session.samples), default=None) if args.sample_rss else None
                result["maxQueueSize"] = max((s["queue"]["queueSize"] for s in session.samples), default=0)
                result["maxRunningJobs"] = max((s["queue"]["runningSize"] for s in session.samples), default=0)
                result["samplingErrors"] = session.sampling_errors
                session.cleanup()
                result.update(trace_statistics(directory))
        report["status"] = "passed"
    except (Exception, KeyboardInterrupt) as error:
        report["status"], report["error"] = "failed", str(error)
        raise
    finally:
        report["sourcesUnchanged"] = before == inventory(args.workspace)
        (args.output / "results.json").write_text(json.dumps(report, indent=2) + "\n")
        assert report["sourcesUnchanged"], "Workspace source contents changed during the workload"


def semantic_workload(args):
    """Measure detached queries after diagnostics confirm compilation has completed."""
    report = {"jarSha256": hashlib.sha256(args.jar.read_bytes()).hexdigest(), "cases": []}
    try:
        for methods in args.semantic_methods:
            for inferred in (False, True):
                directory = args.output / f"methods-{methods}-{'inferred' if inferred else 'plain'}"
                workspace = directory / "workspace"
                workspace.mkdir(parents=True)
                source_file = workspace / "LargeFile.x"
                body = "var local = value; return local;" if inferred else "return value;"
                source = "module LargeFile {\n    static Int value = 1;\n" + "\n".join(
                    f"    Int read{index}() {{ {body} }}" for index in range(methods)
                ) + "\n}\n"
                source_file.write_text(source)
                document = {"uri": source_file.as_uri()}
                session_args = SimpleNamespace(**{**vars(args), "workspace": workspace})
                session = Session(session_args, directory, [{"name": "LargeFile", **document}])
                result = {"methods": methods, "inferred": inferred, "pid": session.process.pid, "queries": []}
                report["cases"].append(result)
                try:
                    session.initialize()
                    session.notify("textDocument/didOpen", {"textDocument": {
                        **document, "languageId": "xtc", "version": 1, "text": source,
                    }})
                    diagnostics = session.call("textDocument/diagnostic", {"textDocument": document})
                    assert not diagnostics.get("items"), diagnostics
                    queue = session.call("xtc/languageServiceStatus")["compilerQueue"]
                    result["compilesBeforeQueries"] = queue["startedTotal"]
                    position = {"line": 1, "character": 16}
                    queries = [
                        ("textDocument/hover", {"position": position}),
                        ("textDocument/references", {"position": position, "context": {"includeDeclaration": True}}),
                        ("textDocument/inlayHint", {"range": {"start": {"line": 2, "character": 0}, "end": {"line": 3, "character": 0}}}),
                        ("textDocument/inlayHint", {"range": {"start": {"line": 0, "character": 0}, "end": {"line": methods + 3, "character": 0}}}),
                        ("textDocument/semanticTokens/full", {}),
                    ]
                    for cycle in range(args.cycles):
                        for method, params in queries:
                            started = time.perf_counter()
                            reply = session.call(method, {"textDocument": document, **params})
                            elapsed = (time.perf_counter() - started) * 1000
                            if method.endswith("hover"):
                                assert reply and "Int" in str(reply), reply
                            elif method.endswith("references"):
                                assert len(reply) == methods + 1, len(reply)
                            elif method.endswith("inlayHint"):
                                expected = (1 if params["range"]["start"]["line"] == 2 else methods) if inferred else 0
                                assert len(reply) == expected, (len(reply), expected)
                            else:
                                assert len(reply["data"]) >= methods * 5, len(reply["data"])
                            measurement = {"method": method, "cycle": cycle, "params": params, "ms": elapsed,
                                           "responseBytes": len(json.dumps(reply).encode())}
                            result["queries"].append(measurement)
                            print(f"{methods} methods, inferred={inferred}: {method} {elapsed:.1f} ms", flush=True)
                    result["queueAfterQueries"] = session.call("xtc/languageServiceStatus")["compilerQueue"]
                    result["exit"] = session.finish()
                    result["status"] = "passed"
                finally:
                    result["sampledPeakHeapBytes"] = max((s["heap"]["usedBytes"] for s in session.samples), default=0)
                    session.cleanup()
                    result.update(trace_statistics(directory))
        report["status"] = "passed"
    except (Exception, KeyboardInterrupt) as error:
        report["status"], report["error"] = "failed", str(error)
        raise
    finally:
        (args.output / "results.json").write_text(json.dumps(report, indent=2) + "\n")


def configuration_workload(args):
    """Replace generated graphs, or add/remove an unrelated root beside a real project graph."""
    workspace = args.workspace.resolve() if args.workspace else args.output / "workspace"
    if args.workspace:
        modules = [{**module, "uri": (workspace / module["uri"]).resolve().as_uri(),
                    **({"resourceRoots": [(workspace / root).resolve().as_uri() for root in module["resourceRoots"]]}
                       if "resourceRoots" in module else {})} for module in json.loads(args.graph.read_text())["modules"]]
        added = args.output / "ConfigurationProbe.x"
        added.write_text("module ConfigurationProbe { Int value = 1; }\n")
        replacement = modules + [{"name": "ConfigurationProbe", "uri": added.as_uri()}]
        changes = [("add-unrelated", replacement), ("unchanged", replacement), ("remove-unrelated", modules)]
    else:
        workspace.mkdir()
        modules = []
        for index in range(args.configuration_documents):
            source = workspace / f"Module{index}.x"
            source.write_text(f"module Module{index} {{\n    Int value = {index};\n}}\n")
            modules.append({"name": source.stem, "uri": source.as_uri()})
        replacement = [{**module, **({"resourceRoots": []} if index == 0 else {})}
                       for index, module in enumerate(modules)]
        changes = [("one-module", replacement), ("unchanged", replacement),
                   ("remove-graph", []), ("restore-graph", modules)]
    session_args = SimpleNamespace(**{**vars(args), "workspace": workspace})
    session = Session(session_args, args.output, modules)
    before_sources = inventory(workspace)
    report = {"jarSha256": hashlib.sha256(args.jar.read_bytes()).hexdigest(),
              "workspace": str(workspace), "pid": session.process.pid, "changes": [],
              "limits": "Dispatch timing is the first status reply after the notification; settled timing includes current document symbols. No GUI or timeout extension."}

    def check_documents():
        results = {}
        for document in documents:
            symbols = session.call("textDocument/documentSymbol", {"textDocument": document})
            assert symbols, document
            diagnostic = session.call("textDocument/diagnostic", {"textDocument": document})
            assert not any(item.get("severity", 1) == 1 for item in diagnostic.get("items", [])), diagnostic
            results[document["uri"]] = {"symbols": symbols, "diagnostics": diagnostic.get("items", [])}
        return results

    try:
        session.initialize()
        if args.workspace:
            reports = session.call("workspace/diagnostic", {})["items"]
            assert not any(d.get("severity", 1) == 1 for item in reports for d in item.get("items", [])), reports
            documents = [{"uri": uri} for uri in sorted({item["uri"] for item in reports})[:args.configuration_documents]]
        else:
            documents = [{"uri": module["uri"]} for module in modules]
        assert documents, "No source documents in the selected graph"
        report["documents"] = len(documents)
        for document in documents:
            session.notify("textDocument/didOpen", {"textDocument": {
                **document, "languageId": "xtc", "version": 1,
                "text": pathlib.Path(urllib.parse.unquote(urllib.parse.urlsplit(document["uri"]).path)).read_text(),
            }})
        baseline = check_documents()
        for cycle in range(args.cycles):
            for label, graph in changes:
                before = session.call("xtc/languageServiceStatus")["compilerQueue"]
                started = time.perf_counter()
                session.notify("workspace/didChangeConfiguration", {
                    "settings": {"xtc": {"compiler": {"sourceModules": graph}}}})
                session.call("xtc/languageServiceStatus")
                dispatch_ms = (time.perf_counter() - started) * 1000
                assert check_documents() == baseline, f"Document symbols or diagnostics changed after {label}"
                after = session.call("xtc/languageServiceStatus")["compilerQueue"]
                change = {"cycle": cycle, "change": label, "dispatchMs": dispatch_ms,
                          "settledMs": (time.perf_counter() - started) * 1000,
                          "submitted": after["submittedTotal"] - before["submittedTotal"],
                          "started": after["startedTotal"] - before["startedTotal"]}
                report["changes"].append(change)
                print(json.dumps(change), flush=True)
        report["exit"] = session.finish()
        report["status"] = "passed"
    except (Exception, KeyboardInterrupt) as error:
        report["status"], report["error"] = "failed", str(error)
        raise
    finally:
        session.cleanup()
        report["sourcesUnchanged"] = before_sources == inventory(workspace)
        report.update(trace_statistics(args.output))
        (args.output / "results.json").write_text(json.dumps(report, indent=2) + "\n")
        assert report["sourcesUnchanged"], "Configuration workload modified source content"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workspace", type=pathlib.Path)
    parser.add_argument("--jar", required=True, type=pathlib.Path)
    parser.add_argument("--graph", type=pathlib.Path, default=pathlib.Path(__file__).resolve().parents[1] / "test-fixtures/compiler-workload/platform.json")
    parser.add_argument("--output", required=True, type=pathlib.Path)
    parser.add_argument("--java", default="java")
    parser.add_argument("--heap", default="2g")
    parser.add_argument("--cycles", type=int, default=10)
    parser.add_argument("--restarts", type=int, default=2)
    parser.add_argument("--timeout", type=float, default=120)
    parser.add_argument("--sample-rss", action="store_true", help="Sample owned server RSS through ps on macOS/Linux")
    parser.add_argument("--gc-every", type=int, default=0, help="Record post-GC heap every N project edit cycles (0 disables)")
    parser.add_argument("--jcmd", default="jcmd")
    parser.add_argument("--heap-histograms", action="store_true",
                        help="Retain live-object histograms at the --gc-every project checkpoints")
    parser.add_argument("--semantic-methods", nargs="+", type=int,
                        help="Generate isolated large files and measure queries after compilation")
    parser.add_argument("--configuration-documents", type=int,
                        help="Measure graph replacement with this many generated buffers, or up to this many real --workspace documents")
    args = parser.parse_args()
    assert args.cycles > 0 and args.restarts >= 0 and args.timeout > 0
    assert args.gc_every >= 0
    assert not (args.semantic_methods and args.configuration_documents), "Select one generated workload"
    assert not args.heap_histograms or (args.gc_every > 0 and not args.semantic_methods and not args.configuration_documents), "Histograms require project editing --gc-every checkpoints"
    assert not args.sample_rss or sys.platform in ("darwin", "linux"), "RSS sampler supports macOS/Linux"
    args.jar, args.output = args.jar.resolve(), args.output.resolve()
    args.output.mkdir(parents=True, exist_ok=False)
    if args.configuration_documents is not None:
        assert args.configuration_documents > 0
        configuration_workload(args)
    elif args.semantic_methods:
        assert all(count > 0 for count in args.semantic_methods)
        semantic_workload(args)
    else:
        assert args.workspace is not None, "--workspace is required for the project workload"
        args.workspace = args.workspace.resolve()
        run(args)
