#!/usr/bin/env python3
"""Measure the packaged compiler on an explicit real graph without writing source files.

The sampler observes heap and queue metadata over the same stdio connection. Timings include
transport/debounce; sampled peak heap is a lower bound, not a claim about RSS or retained objects.
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
import threading
import time


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
        with (self.directory / "samples.jsonl").open("w") as output:
            while not self.stop_sampling.is_set():
                try:
                    status = self.call("xtc/languageServiceStatus", record=False)
                    queue = status["compilerQueue"]
                    assert queue["queueSize"] == len(queue["queuedJobs"])
                    sample = {"time": time.monotonic(), "heap": status["heap"], "queue": queue}
                    self.samples.append(sample)
                    output.write(json.dumps(sample) + "\n")
                    output.flush()
                except Exception as error:
                    self.sampling_errors.append(str(error))
                    return
                self.stop_sampling.wait(0.2)

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
    return {"apiTimings": {name: percentiles(values) for name, values in sorted(timings.items())},
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
              "jarSha256": hashlib.sha256(args.jar.read_bytes()).hexdigest(),
              "limits": "Sampled heap, not RSS or retained heap; bounded workload, not a prolonged interactive soak."}
    try:
        for restart in range(args.restarts + 1):
            directory = args.output / f"session-{restart}"
            directory.mkdir()
            session = Session(args, directory, modules)
            result = {"pid": session.process.pid, "cancellations": 0, "exit": None}
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
                session.notify("textDocument/didClose", {"textDocument": document})
                result["exit"] = session.finish(disconnect=restart % 2 == 1)
                result["status"] = "passed"
                print(f"session {restart + 1}/{args.restarts + 1}: PID {session.process.pid} exited, {args.cycles} cycles", flush=True)
            finally:
                result["timings"] = {method: percentiles([t["ms"] for t in session.timings if t["method"] == method])
                                     for method in sorted({t["method"] for t in session.timings})}
                result["sampledPeakHeapBytes"] = max((s["heap"]["usedBytes"] for s in session.samples), default=0)
                result["maxQueueSize"] = max((s["queue"]["queueSize"] for s in session.samples), default=0)
                result["maxRunningJobs"] = max((s["queue"]["runningSize"] for s in session.samples), default=0)
                result["samplingErrors"] = session.sampling_errors
                session.cleanup()
                result.update(trace_statistics(directory))
        report["status"] = "passed"
    except Exception as error:
        report["status"], report["error"] = "failed", str(error)
        raise
    finally:
        report["sourcesUnchanged"] = before == inventory(args.workspace)
        (args.output / "results.json").write_text(json.dumps(report, indent=2) + "\n")
        assert report["sourcesUnchanged"], "Workspace source contents changed during the workload"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workspace", required=True, type=pathlib.Path)
    parser.add_argument("--jar", required=True, type=pathlib.Path)
    parser.add_argument("--graph", type=pathlib.Path, default=pathlib.Path(__file__).resolve().parents[1] / "test-fixtures/compiler-workload/platform.json")
    parser.add_argument("--output", required=True, type=pathlib.Path)
    parser.add_argument("--java", default="java")
    parser.add_argument("--heap", default="2g")
    parser.add_argument("--cycles", type=int, default=10)
    parser.add_argument("--restarts", type=int, default=2)
    parser.add_argument("--timeout", type=float, default=120)
    args = parser.parse_args()
    assert args.cycles > 0 and args.restarts >= 0 and args.timeout > 0
    args.workspace, args.jar, args.output = args.workspace.resolve(), args.jar.resolve(), args.output.resolve()
    args.output.mkdir(parents=True, exist_ok=False)
    run(args)
