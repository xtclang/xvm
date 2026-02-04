# IDE Problems Report

Scan all Kotlin/Java source files and collect IntelliJ IDEA diagnostics.

## Prerequisites

- IntelliJ IDEA must be running with the project open
- Connect to IDE first with `/ide` command

## How It Works

The MCP `getDiagnostics` API only returns diagnostics for the **currently focused file**.
To scan all files, we must:

1. Open each file via `idea://open?file=...` URL scheme
2. Wait briefly for IntelliJ to analyze
3. Call `mcp__ide__getDiagnostics` (no URI) to get diagnostics for that file
4. Repeat for all files, aggregating results

## Instructions

### Step 1: Get file list

```bash
find /Users/marcus/src/xtc-init-wizard/lang -name "*.kt" -type f ! -path "*/build/*" ! -path "*/.gradle/*" | sort
```

### Step 2: For each file, run this pattern

```bash
open "idea://open?file=/path/to/file.kt"
sleep 1
```

Then immediately call:
```
mcp__ide__getDiagnostics  # no URI parameter
```

### Step 3: Aggregate results

Collect all non-empty diagnostics and format as:

```
=== IDE Problems Report ===
Generated: [timestamp]
Project: /Users/marcus/src/xtc-init-wizard

--- lang/path/to/file.kt ---
  [ERROR] Line 42: Description
  [WARNING] Line 17: Description

--- lang/path/to/another.kt ---
  (no problems)

=== Summary ===
Files scanned: 49
Files with problems: 2
Total errors: 1
Total warnings: 3
```

### Step 4: Write report

Save to `/tmp/ide-problems-report.txt`

## Performance Notes

- Each file takes ~1-2 seconds (open + analyze + query)
- 50 files = ~1-2 minutes total
- Consider batching or only scanning changed files for faster iteration

## Quick Scan (Changed Files Only)

For faster checks, scan only git-modified files:

```bash
git diff --name-only HEAD | grep -E '\.(kt|java)$'
```