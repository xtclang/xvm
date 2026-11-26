#!/usr/bin/env bash
# Runs the IntelliJ and VS Code compiler playbooks in a Linux container, on a virtual display (Xvfb), so that no IDE
# window opens on the desktop and nothing typed or focused there reaches the test IDE.
#
#   playbook.sh image                       build the image, localhost/xvm-playbook; the other modes build it when
#                                           it is missing
#   playbook.sh build                       xdk:installDist and publishLocal in the container (X336 runs the XDK's xec,
#                                           X303 and X337 build with the plugin and XDK from mavenLocal)
#   playbook.sh playbook X259,X324 [args]   IntelliJ's features playbook for those cases, or all, with Gradle args
#                                           such as -Plsp4ijChannel=nightly -Plsp4ijVersion=<pinned version>
#   playbook.sh vscode X259,X324 [args]     VS Code's playbook for those cases, or all, with Gradle args
#   playbook.sh gradle <args...>            any Gradle invocation in the container, on the virtual display, such as
#                                           the VS Code extension's suite (:lang:vscode-extension:testVscodeExtension),
#                                           with the lang build flags
#
# The root build's containerPlaybook* tasks run these modes. The container engine is $XTC_CONTAINER_COMMAND, which
# those tasks set from org.xtclang.docker.command, as the docker build does; without it, docker, or podman when
# docker is not installed. Podman on macOS needs its machine started (podman machine start).
#
# Each run exports this checkout as it is, uncommitted changes included, and copies the playbook's run
# directories to lang/intellij-plugin/build/reports/compiler-playbook, where a native run puts them, which the
# container mounts as /out/runs and prunes as a native run's report retention does, and the
# VS Code extension's reports to lang/vscode-extension/build/reports. The
# volumes xvm-playbook-gradle, xvm-playbook-work (xvm-playbook-work-sN in slot N) and xvm-playbook-m2 keep
# Gradle's caches, the build and mavenLocal between runs.
# The container is aarch64 Linux on an Apple machine: its fonts and timing are not macOS's. The export
# has no .git: its commit and branch are passed in as XTC_COMMIT and XTC_BRANCH, for javatools' build info
# and the VS Code playbook, with the uncommitted paths. A run's container may use $XTC_CONTAINER_MEMORY, 14g by
# default, which the IntelliJ playbook and tours need (7g let the kernel kill the IDE). As many runs go at once as
# the engine's memory holds, or $XTC_CONTAINER_SLOTS: on a 32 GiB Podman machine, two.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
root="$(git -C "$here" rev-parse --show-toplevel)"
container="$root/lang/intellij-plugin/build/container"
image=localhost/xvm-playbook:latest
mode="${1:?mode: image, build, playbook, vscode or gradle}"
engine="${XTC_CONTAINER_COMMAND:-}"
if [ -z "$engine" ]; then
    if command -v docker > /dev/null; then engine=docker; else engine=podman; fi
fi

if [ "$mode" = "image" ]; then
    exec "$engine" build -t "$image" -f "$here/Containerfile" "$here"
fi
if ! "$engine" image inspect "$image" > /dev/null 2>&1; then
    "$engine" build -t "$image" -f "$here/Containerfile" "$here"
fi

# VS Code's playbook is a Gradle invocation in the container
lang=(-PincludeBuildLang=true -PincludeBuildAttachLang=true)
args=("$@")
if [ "$mode" = "vscode" ]; then
    cases="${2:?case ids, or all}"
    args=(gradle :lang:vscode-extension:testCompilerPlaybook "${lang[@]}")
    [ "$cases" = "all" ] || args+=("-PcompilerPlaybookCases=$cases")
    args+=("${@:3}")
fi

# each run exports the checkout as it is when the run is asked for, into a directory of its own, which the
# run mounts and removes when it ends, so that a run queued later from the same checkout does not replace
# what an earlier one waits to run; an export whose run has gone is removed. The single src directory that
# runs of an older playbook.sh share is left alone, as one of them may still wait to mount it
for stale in "$container"/src-*; do
    [ -e "$stale" ] || continue
    if ! ps -p "$((10#${stale##*-}))" -o command= 2> /dev/null | grep -q 'playbook\.sh'; then
        rm -rf "$stale"
    fi
done
source="$container/src-$(date +%Y%m%d-%H%M%S)-$$"
ticket=""
locked=false
cleanup() {
    rm -rf "$source"
    if [ -n "$ticket" ]; then rm -f "$ticket"; fi
    if [ "$locked" = true ]; then rm -rf "$lock"; fi
}
trap cleanup EXIT
# where a native run's reports are, so that the same tools read them, and the same retention prunes them
reports="$root/lang/intellij-plugin/build/reports/compiler-playbook"
mkdir -p "$source" "$container/out" "$reports"
# the checkout's files, tracked or not, without what git ignores, such as build outputs, and without
# tracked files deleted from the working tree
(
    cd "$root"
    git ls-files -z --cached --others --exclude-standard \
        | while IFS= read -r -d '' file; do if [ -e "$file" ]; then printf '%s\0' "$file"; fi; done \
        | tar --null -T - -cf -
) | tar -x -C "$source"
# the export has no .git, so the commit and branch, which javatools' build info and the VS Code playbook record, and
# the uncommitted paths come from here, read with the export, so that they name the tree the run gets
commit="$(git -C "$root" rev-parse HEAD)"
branch="$(git -C "$root" rev-parse --abbrev-ref HEAD)"
dirty="$(git -C "$root" status --porcelain)"

# one container run at a time in each slot on this machine: the runs of a slot share its work volume, whichever
# worktree they start from, so its lock is in the repository's common git directory, which every worktree shares; a
# lock whose owner has gone is taken over. Slot 1's lock, queue, work volume and container names are the ones runs
# have always used, so that a run of an older playbook.sh, which knows only those, still takes its turn, and waits
# for every other slot's container too. A checkout always runs in the same slot, or in $XTC_CONTAINER_SLOT, so that
# a build and the cases that need it share a work volume, and its runs share no output directory
common="$(git -C "$root" rev-parse --path-format=absolute --git-common-dir)"
memory="${XTC_CONTAINER_MEMORY:-14g}"
bytes() {
    local value="${1%[kKmMgG]}"
    case "${1: -1}" in
        g | G) echo $((value * 1073741824)) ;;
        m | M) echo $((value * 1048576)) ;;
        k | K) echo $((value * 1024)) ;;
        *) echo "$1" ;;
    esac
}
slots="${XTC_CONTAINER_SLOTS:-}"
if [ -z "$slots" ]; then
    total="$("$engine" info --format '{{.Host.MemTotal}}' 2> /dev/null || "$engine" info --format '{{.MemTotal}}' 2> /dev/null || echo 0)"
    slots=$((total / $(bytes "$memory")))
    if [ "$slots" -lt 1 ]; then slots=1; fi
fi
slot="${XTC_CONTAINER_SLOT:-$(($(printf '%s' "$root" | cksum | cut -d ' ' -f 1) % slots + 1))}"
if [ "$slot" -eq 1 ]; then
    lock="$common/xvm-playbook-container.lock"
    queue="$common/xvm-playbook-queue"
    work=xvm-playbook-work
    prefix=xvm-playbook-
    running='^xvm-playbook-[0-9]'
else
    lock="$common/xvm-playbook-container.lock.s$slot"
    queue="$common/xvm-playbook-queue.s$slot"
    work="xvm-playbook-work-s$slot"
    prefix="xvm-playbook-s$slot-"
    running="^$prefix"
fi
# runs take a slot's lock in the order they asked for it: each waiter holds a ticket, named by the time it asked,
# and takes the lock only when its ticket is the first of a waiting run's; a ticket whose run has gone is dropped
mkdir -p "$queue"
ticket="$queue/$(printf '%010d-%010d' "$(date +%s)" "$$")"
touch "$ticket"
first_ticket() {
    local waiting waiter
    for waiting in "$queue"/*; do
        [ -e "$waiting" ] || continue
        waiter=$((10#${waiting##*-}))
        if ps -p "$waiter" -o command= 2> /dev/null | grep -q 'playbook\.sh'; then
            echo "$waiting"
            return
        fi
        rm -f "$waiting"
    done
}
until [ "$(first_ticket)" = "$ticket" ] && mkdir "$lock" 2> /dev/null; do
    owner="$(cat "$lock/pid" 2> /dev/null || true)"
    if [ -n "$owner" ] && ! kill -0 "$owner" 2> /dev/null; then
        rm -rf "$lock"
        continue
    fi
    echo "waiting for an earlier playbook container run in slot $slot of $slots (pid ${owner:-queued}) to finish"
    sleep 15
done
locked=true
echo $$ > "$lock/pid"
rm -f "$ticket"
ticket=""
# a run that took no lock, such as one of an older checkout, shares the slot's volumes as well
while "$engine" ps --format '{{.Names}}' | grep -q "$running"; do
    echo "waiting for the running playbook container to finish"
    sleep 15
done

stamp="$(date +%Y%m%d-%H%M%S)"
status=0
# --init makes PID 1 reap orphans: without it, a Gradle daemon that exits on a cancel stays a zombie, which Java
# reports alive (X260 waits for the canceled daemon to exit)
"$engine" run --rm --init --name "$prefix$stamp" \
    --memory "$memory" --shm-size 1g --security-opt label=disable \
    -e XTC_COMMIT="$commit" -e XTC_BRANCH="$branch" -e XTC_PLAYBOOK_DIRTY="$dirty" \
    -e XTC_RECORD_VIDEO="${XTC_RECORD_VIDEO:-}" \
    -v xvm-playbook-gradle:/gradle \
    -v "$work:/work" \
    -v xvm-playbook-m2:/home/playbook/.m2 \
    -v "$source:/src:ro" \
    -v "$container/out:/out" \
    -v "$reports:/out/runs" \
    -v "$here/in-container.sh:/in-container.sh:ro" \
    "$image" \
    bash /in-container.sh "${args[@]}" 2>&1 | tee "$container/out/$mode-$stamp.log" || status=$?

if [ -d "$container/out/vscode-reports" ]; then
    mkdir -p "$root/lang/vscode-extension/build/reports"
    rsync -a "$container/out/vscode-reports/" "$root/lang/vscode-extension/build/reports/"
fi
exit "$status"
