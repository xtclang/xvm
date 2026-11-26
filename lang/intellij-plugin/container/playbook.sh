#!/usr/bin/env bash
# Runs the IntelliJ and VS Code compiler playbooks in a Linux container, on a virtual display (Xvfb), so that no IDE
# window opens on the desktop and nothing typed or focused there reaches the test IDE.
#
#   playbook.sh image                       build the image, localhost/xvm-playbook; the other modes build it when
#                                           it is missing
#   playbook.sh build                       xdk:installDist and publishLocal in the container (X336 runs the XDK's xec,
#                                           X303 and X337 build with the plugin and XDK from mavenLocal), which the
#                                           modes below also run first when the slot's XDK is not of this checkout
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
# volumes xvm-playbook-gradle, xvm-playbook-work and xvm-playbook-m2 (each with -sN in slot N) keep Gradle's
# caches, the build and mavenLocal between runs.
# The container is aarch64 Linux on an Apple machine: its fonts and timing are not macOS's. The export
# has no .git: its commit and branch are passed in as XTC_COMMIT and XTC_BRANCH, for javatools' build info
# and the VS Code playbook, with the uncommitted paths. A run's container may use $XTC_CONTAINER_MEMORY, 14g by
# default, which the IntelliJ playbook and tours need (7g let the kernel kill the IDE), or less on a smaller machine.
# As many runs go at once as the engine's memory and CPUs hold, or $XTC_CONTAINER_SLOTS: on a 32 GiB, 8-CPU Podman
# machine, two; on a 16 GiB one, one. Waiting runs take the slots in the order they asked, each the first that is
# free, or only the one $XTC_CONTAINER_SLOT names; each slot has its own volumes and /out.
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
tickets=()
locked=false
cleanup() {
    rm -rf "$source"
    rm -f ${tickets[@]+"${tickets[@]}"}
    if [ "$locked" = true ]; then rm -rf "$lock"; fi
}
trap cleanup EXIT
# where a native run's reports are, so that the same tools read them, and the same retention prunes them
reports="$root/lang/intellij-plugin/build/reports/compiler-playbook"
mkdir -p "$source" "$reports"
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
# the fingerprint of what xdk:installDist and publishLocal build from, which the container compares with the one its
# XDK was built from, to build it again when they differ: javatools and the XDK's libraries, the XDK, the plugin, the
# build logic, the version catalog and wrapper, and the root's build scripts and properties. It hashes their trees at
# HEAD and their uncommitted changes, untracked files too, which takes a fraction of a second
fingerprint="$(
    cd "$root"
    sources=()
    while IFS= read -r path; do
        case "$path" in
            javatools* | lib_* | xdk | plugin | build-logic | gradle | gradlew | *.gradle.kts | *.properties)
                sources+=("$path")
                ;;
        esac
    done < <(git ls-tree --name-only HEAD)
    {
        git ls-tree HEAD -- "${sources[@]}"
        git diff HEAD --binary --no-color --no-ext-diff --no-textconv -- "${sources[@]}"
        untracked="$(git ls-files -z --others --exclude-standard -- "${sources[@]}" | tr '\0' '\n')"
        if [ -n "$untracked" ]; then
            printf '%s\n' "$untracked"
            printf '%s\n' "$untracked" | git hash-object --stdin-paths
        fi
    } | git hash-object --stdin
)"

# one container run at a time in each slot on this machine: the runs of a slot share its volumes, whichever worktree
# they start from, so its lock is in the repository's common git directory, which every worktree shares; a lock whose
# owner has gone is taken over. A run takes the first slot that is free when its turn comes, or only the one that
# $XTC_CONTAINER_SLOT names: as a run that tests builds the XDK first when its slot's is of other sources, no build
# needs to share a slot with the cases that use it. Slot 1's lock, queue, volumes and container names are the ones
# runs have always used, so that a run of an older playbook.sh, which knows only those, or takes the slot that its
# checkout's path hashes to, still takes its turn
common="$(git -C "$root" rev-parse --path-format=absolute --git-common-dir)"
bytes() {
    local value="${1%[kKmMgG]}"
    case "${1: -1}" in
        g | G) echo $((value * 1073741824)) ;;
        m | M) echo $((value * 1048576)) ;;
        k | K) echo $((value * 1024)) ;;
        *) echo "$1" ;;
    esac
}
# what the engine has: Podman's format first, then Docker's
total="$("$engine" info --format '{{.Host.MemTotal}}' 2> /dev/null || "$engine" info --format '{{.MemTotal}}' 2> /dev/null || echo 0)"
cpus="$("$engine" info --format '{{.Host.CPUs}}' 2> /dev/null || "$engine" info --format '{{.NCPU}}' 2> /dev/null || echo 0)"
# a run's memory: 14g, which the IntelliJ playbook needs, or on a smaller machine what it has short of 1 GiB
memory="${XTC_CONTAINER_MEMORY:-}"
if [ -z "$memory" ]; then
    memory=14g
    if [ "$total" -gt 0 ] && [ "$total" -lt $(($(bytes 14g) + 1073741824)) ]; then
        memory="$((total / 1048576 - 1024))m"
        echo "warning: the container engine has $((total / 1048576)) MiB, so a run gets $memory; the IntelliJ playbook needs 14g" >&2
    fi
fi
# as many runs at once as both the memory and the CPUs hold, four CPUs a run, and at least one
slots="${XTC_CONTAINER_SLOTS:-}"
if [ -z "$slots" ]; then
    slots=$((total / $(bytes "$memory")))
    if [ "$((cpus / 4))" -lt "$slots" ]; then slots=$((cpus / 4)); fi
    if [ "$slots" -lt 1 ]; then slots=1; fi
fi
# the slots this run may take: the one $XTC_CONTAINER_SLOT pins it to, or any
wanted=()
if [ -n "${XTC_CONTAINER_SLOT:-}" ]; then
    wanted=("$XTC_CONTAINER_SLOT")
else
    for ((each = 1; each <= slots; each++)); do wanted+=("$each"); done
fi
lock_of() {
    if [ "$1" -eq 1 ]; then
        echo "$common/xvm-playbook-container.lock"
    else
        echo "$common/xvm-playbook-container.lock.s$1"
    fi
}
queue_of() {
    if [ "$1" -eq 1 ]; then
        echo "$common/xvm-playbook-queue"
    else
        echo "$common/xvm-playbook-queue.s$1"
    fi
}
# runs take the slots in the order they asked for them. Each waiter holds a ticket, named by the time it asked and its
# pid, in the queue of each slot it may take: a run that any slot will do in every one, a pinned run in its slot's
# only, as a run of an older playbook.sh does. It takes a slot only when the slot is free and its ticket is the first
# of a waiting run's in that slot's queue: so of the runs that may take a slot, the earliest takes it, and a run that
# waits for one slot keeps no run behind it from another slot that is free. Slot 1's queue is the one that every run
# that may take slot 1 waits in, as runs always have; an older run's ticket there wants slot 1 only, as it is in no
# other queue. A ticket whose run has gone is dropped
ticket="$(printf '%010d-%010d' "$(date +%s)" "$$")"
for each in "${wanted[@]}"; do
    mkdir -p "$(queue_of "$each")"
    touch "$(queue_of "$each")/$ticket"
    tickets+=("$(queue_of "$each")/$ticket")
done
# the name of the first ticket of a waiting run in queue $1
first_ticket() {
    local waiting waiter
    for waiting in "$1"/*; do
        [ -e "$waiting" ] || continue
        waiter=$((10#${waiting##*-}))
        if ps -p "$waiter" -o command= 2> /dev/null | grep -q 'playbook\.sh'; then
            echo "${waiting##*/}"
            return
        fi
        rm -f "$waiting"
    done
}
# takes the first slot this run may take whose queue it heads and whose lock it gets, the lock of a run that has gone
# included, into $slot; fails when there is none
slot=""
take_slot() {
    local each owner
    for each in "${wanted[@]}"; do
        [ "$(first_ticket "$(queue_of "$each")")" = "$ticket" ] || continue
        owner="$(cat "$(lock_of "$each")/pid" 2> /dev/null || true)"
        if [ -n "$owner" ] && ! kill -0 "$owner" 2> /dev/null; then rm -rf "$(lock_of "$each")"; fi
        if mkdir "$(lock_of "$each")" 2> /dev/null; then
            slot="$each"
            return 0
        fi
    done
    return 1
}
until take_slot; do
    holders=""
    for each in "${wanted[@]}"; do
        owner="$(cat "$(lock_of "$each")/pid" 2> /dev/null || true)"
        holders="${holders:+$holders or }slot $each (pid ${owner:-queued})"
    done
    echo "waiting for an earlier playbook container run to finish, in $holders of $slots"
    sleep 15
done
lock="$(lock_of "$slot")"
locked=true
echo $$ > "$lock/pid"
rm -f "${tickets[@]}"
tickets=()
echo "running in slot $slot of $slots"
# all that depends on the slot, set once the run has it
if [ "$slot" -eq 1 ]; then
    volume=""
    prefix=xvm-playbook-
    running='^xvm-playbook-[0-9]'
else
    # its own volumes, Gradle's too: two containers can't share a Gradle user home, as Gradle asks a lock's owner to
    # release it over localhost, which is each container's own ("Timeout waiting to lock journal cache")
    volume="-s$slot"
    prefix="xvm-playbook-s$slot-"
    running="^$prefix"
fi
# each slot's own /out: a run removes the screen recording there as it starts, so that it never takes an earlier
# run's for its own, and one checkout's runs may go in two slots at once, which a shared /out would let one run's
# start delete the other's recording from
out="$container/out$volume"
mkdir -p "$out"
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
    -e XTC_XDK_FINGERPRINT="$fingerprint" \
    -e XTC_RECORD_VIDEO="${XTC_RECORD_VIDEO:-}" \
    -v "xvm-playbook-gradle$volume:/gradle" \
    -v "xvm-playbook-work$volume:/work" \
    -v "xvm-playbook-m2$volume:/home/playbook/.m2" \
    -v "$source:/src:ro" \
    -v "$out:/out" \
    -v "$reports:/out/runs" \
    -v "$here/in-container.sh:/in-container.sh:ro" \
    "$image" \
    bash /in-container.sh "${args[@]}" 2>&1 | tee "$out/$mode-$stamp.log" || status=$?

# the VS Code reports of each slot's /out into the checkout's: a run's directory is its own, and of the files that
# runs share, such as latest-run.txt, the newer one stays, as a run of this checkout in another slot may have
# written it later
if [ -d "$out/vscode-reports" ]; then
    mkdir -p "$root/lang/vscode-extension/build/reports"
    rsync -a --update "$out/vscode-reports/" "$root/lang/vscode-extension/build/reports/"
fi
exit "$status"
