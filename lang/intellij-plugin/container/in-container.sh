#!/usr/bin/env bash
# Runs inside the xvm-playbook container; playbook.sh starts it.
#   in-container.sh build                      sync /src into /work/xvm and run xdk:installDist and publishLocal
#   in-container.sh playbook <ids>|all [args]  sync, then run the features playbook for <ids>, or all, on Xvfb
#   in-container.sh gradle <args...>           sync, then run ./gradlew <args> on Xvfb
# /src is the read-only exported checkout, /out receives the run reports, /work, /gradle and
# /home/playbook/.m2 are volumes. The playbook and gradle modes first run xdk:installDist and publishLocal when the
# XDK in the volumes was built from other sources than the checkout's, as playbook.sh's fingerprint of them says.
set -euo pipefail

# started as root, which owns the volumes: hand them to the playbook user, once, and run as it
if [ "$(id -u)" = 0 ]; then
    mkdir -p /work /gradle /out /home/playbook/.m2
    find /work /gradle /out /home/playbook/.m2 -xdev ! -user playbook -exec chown playbook:playbook {} + 2>/dev/null || true
    exec runuser -u playbook -- env HOME=/home/playbook GRADLE_USER_HOME=/gradle bash "$0" "$@"
fi

mode="${1:?mode}"
shift || true
: "${XTC_XDK_FINGERPRINT:?playbook.sh passes in the fingerprint of the sources of the XDK}"

mkdir -p /work/xvm /out
# keep build outputs, caches and the IDE Starter cache (under lang/intellij-plugin/build) between runs
rsync -a --delete \
    --exclude 'build/' --exclude '.gradle/' --exclude '.kotlin/' --exclude '.intellijPlatform/' \
    --exclude 'node_modules/' --exclude 'out/' \
    /src/ /work/xvm/
cd /work/xvm
# the VS Code builds which the VS Code extension's tests download, kept in a volume between runs
export XTC_VSCODE_TEST_CACHE=/gradle/vscode-test

# a slot's Gradle volume would keep the caches and wrapper of every Gradle version a run ever used: 20 GB of one that
# no checkout used any more filled the Podman machine's disk on 2026-11-26; keep only the checkout's wrapper version's
wrapper_version="$(sed -n 's/^distributionUrl=.*gradle-\([0-9][0-9.]*\)-\(bin\|all\)\.zip$/\1/p' gradle/wrapper/gradle-wrapper.properties)"
if [ -n "$wrapper_version" ]; then
    for stale in /gradle/caches/[0-9]*.[0-9]* /gradle/wrapper/dists/gradle-[0-9]*; do
        [ -e "$stale" ] || continue
        case "$stale" in
            /gradle/caches/"$wrapper_version" | /gradle/wrapper/dists/gradle-"$wrapper_version"-*) ;;
            *)
                echo "gradle: removing $stale, of a version this checkout does not use ($wrapper_version)"
                rm -rf "$stale"
                ;;
        esac
    done
fi

# container-local Gradle settings: modest heaps for an 8 GiB VM, no file watching on overlay storage
cat > /gradle/gradle.properties <<'EOF'
org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8
kotlin.daemon.jvmargs=-Xmx1g
org.gradle.workers.max=4
org.gradle.vfs.watch=false
org.gradle.console=plain
EOF

start_display() {
    export DISPLAY=:99
    rm -f /tmp/.X99-lock
    Xvfb :99 -screen 0 1920x1200x24 -nolisten tcp +extension RANDR > /out/xvfb.log 2>&1 &
    for _ in $(seq 1 50); do
        xdpyinfo > /dev/null 2>&1 && break
        sleep 0.2
    done
    if [ "${PLAYBOOK_WM:-openbox}" = "openbox" ]; then
        openbox > /out/openbox.log 2>&1 &
        sleep 1
    fi
    start_recording
}

# with XTC_RECORD_VIDEO=1, the whole display as a video, with the pointer, which a tour composes into its steps'
# clips: /out/screen.mp4, and /out/screen.start, the wall-clock time in epoch milliseconds that its first frame shows,
# the clock that tour.json's step times use; the run's reports get both
recorder=""
start_recording() {
    # /out outlives a run: a run that records nothing must not take an earlier run's screen for its own
    rm -f /out/screen.mp4 /out/screen.start
    [ "${XTC_RECORD_VIDEO:-}" = "1" ] || return 0
    date +%s%3N > /out/screen.start
    ffmpeg -nostdin -loglevel error -f x11grab -draw_mouse 1 -framerate 15 -video_size 1920x1200 -i :99 \
        -c:v libx264 -preset veryfast -crf 30 -pix_fmt yuv420p /out/screen.mp4 > /out/ffmpeg.log 2>&1 &
    recorder=$!
    export XTC_RECORDING_START_FILE=/out/screen.start
}

# ends the recording, which an interrupt lets ffmpeg finish as a playable file
stop_recording() {
    [ -n "$recorder" ] || return 0
    kill -INT "$recorder" 2> /dev/null || true
    wait "$recorder" 2> /dev/null || true
    recorder=""
}

# the XDK in the work and m2 volumes, which cases such as X303, X336 and X337 run and build with, and which neither
# playbook builds: its marker holds the fingerprint of the sources it was built from, which playbook.sh passes in as
# XTC_XDK_FINGERPRINT. A run that tests builds it first when the marker is missing or names other sources, or when
# the XDK changed after the marker was written, as a build of an older playbook.sh, which writes none, changes it
xdk_marker=/work/xvm/xdk/build/xdk-fingerprint
xdk_outputs=(/work/xvm/xdk/build/install/xdk /home/playbook/.m2/repository/org/xtclang/xdk
    /home/playbook/.m2/repository/org/xtclang/xtc-plugin)
build_xdk() {
    rm -f "$xdk_marker"
    ./gradlew xdk:installDist publishLocal "$@"
    echo "$XTC_XDK_FINGERPRINT" > "$xdk_marker"
}
ensure_xdk() {
    local built output reason=""
    built="$(cat "$xdk_marker" 2> /dev/null || true)"
    for output in "${xdk_outputs[@]}"; do
        [ -d "$output" ] || reason="the volumes have no $output"
    done
    if [ -z "$built" ]; then
        reason="the volumes have no XDK marked with the sources it was built from"
    elif [ "$built" != "$XTC_XDK_FINGERPRINT" ]; then
        reason="the volumes' XDK was built from other sources (${built:0:12}, these are ${XTC_XDK_FINGERPRINT:0:12})"
    elif [ -z "$reason" ] && [ -n "$(find "${xdk_outputs[@]}" -newer "$xdk_marker" -print -quit)" ]; then
        reason="the volumes' XDK changed after its marker was written (a build of an older playbook.sh writes none)"
    fi
    if [ -z "$reason" ]; then
        echo "xdk: not rebuilt, as the volumes' XDK was built from these sources (${XTC_XDK_FINGERPRINT:0:12})"
        return 0
    fi
    echo "xdk: rebuilding, as $reason"
    build_xdk
}

reports=/work/xvm/lang/intellij-plugin/build/reports/compiler-playbook

# the runs the work volume already has, from earlier containers, which copy_runs leaves alone
earlier="$(mktemp)"
find "$reports" -maxdepth 1 -name 'run-*' -exec basename {} \; > "$earlier" 2>/dev/null || true

copy_runs() {
    [ -d "$reports" ] || return 0
    mkdir -p /out/runs
    local copied=false
    # copy each run directory of this container with a results.json (payload only, not the IDE cache)
    for dir in "$reports"/run-*; do
        [ -f "$dir/results.json" ] || continue
        local name
        name="$(basename "$dir")"
        if ! grep -qx "$name" "$earlier" && [ ! -f "/out/runs/$name/results.json" ]; then
            # into a directory that the retention passes over, renamed once it is whole: a run of this checkout in
            # another slot may prune /out/runs meanwhile, and must not take a run copied in part for a whole one
            local staged="/out/runs/.copying-$name"
            rm -rf "$staged"
            mkdir -p "$staged"
            rsync -a "$dir/" "$staged/"
            if [ -f /out/screen.mp4 ]; then
                cp /out/screen.mp4 /out/screen.start "$staged/"
            fi
            local ide="$reports/out/ide-tests/tests"
            # Starter 262 keeps the IDE's log in the run's folder, Starter 263 in a folder of the test below it
            for folder in "$ide"/*/"XtcCompilerPlaybook-$name"; do
                [ -d "$folder" ] || continue
                find "$folder" -maxdepth 3 -type d -name log -print0 | while IFS= read -r -d '' logs; do
                    rsync -a --exclude 'indexing-diagnostic/' "$logs/" "$staged/ide-log/"
                done
            done
            # the run's own IDE folder, its config, system and logs, once its logs are copied: about 240 MB a run,
            # which filled the machine's disk; the IDE build cache beside them stays
            for folder in "$ide"/*/"XtcCompilerPlaybook-$name"; do
                [ -d "$folder" ] && rm -rf "$folder"
            done
            rm -rf "/out/runs/$name"
            mv "$staged" "/out/runs/$name"
            echo "copied $name"
            copied=true
        fi
    done
    # the copies, in the checkout's reports, get the report retention of a native run (PruneTestReportsTask): their
    # traces compressed, and those of older passing runs and the payloads of old runs dropped, but not the newest failures'
    if [ "$copied" = true ]; then
        ./gradlew -q :lang:intellij-plugin:pruneCompilerPlaybookReports -PincludeBuildLang=true \
            -PincludeBuildAttachLang=true -PplaybookReports=/out/runs || echo "pruning the copied runs failed"
    fi
}

case "$mode" in
    build)
        echo "xdk: building, as the build mode asks (${XTC_XDK_FINGERPRINT:0:12})"
        build_xdk "$@"
        ;;
    playbook)
        cases="${1:?case ids, or all}"
        shift || true
        # all cases is the playbook's default, without a selection
        selection=()
        [ "$cases" = "all" ] || selection=("-PintellijPlaybookCases=$cases")
        ensure_xdk
        start_display
        status=0
        ./gradlew :lang:intellij-plugin:testCompilerPlaybook \
            -PincludeBuildLang=true -PincludeBuildAttachLang=true \
            --tests '*CompilerPlaybookTest.compilerPlaybook' \
            "${selection[@]}" "$@" || status=$?
        stop_recording
        copy_runs
        exit "$status"
        ;;
    gradle)
        ensure_xdk
        start_display
        status=0
        # the lang build flags, as the header says, so that :lang:* tasks resolve; passing them again is harmless
        ./gradlew -PincludeBuildLang=true -PincludeBuildAttachLang=true "$@" || status=$?
        stop_recording
        copy_runs
        # the VS Code extension's reports (its suite, playbook, lifecycle and explorer runs), when there are any
        vscode_reports=/work/xvm/lang/vscode-extension/build/reports
        if [ -d "$vscode_reports" ]; then
            mkdir -p /out/vscode-reports
            rsync -a "$vscode_reports/" /out/vscode-reports/
        fi
        # the screen of a recorded VS Code tour, whose tour.json names this recording by its start: into its run's
        # directory, where the composer cuts the steps' clips from it
        if [ -f /out/screen.mp4 ] && [ -d /out/vscode-reports/vscode-tour ]; then
            recorded=$(cat /out/screen.start)
            for run in /out/vscode-reports/vscode-tour/run-*/; do
                if [ -f "$run/tour.json" ] && grep -q '"file": "screen.mp4"' "$run/tour.json" \
                    && grep -q "\"start\": $recorded," "$run/tour.json"; then
                    cp /out/screen.mp4 /out/screen.start "$run"
                fi
            done
        fi
        exit "$status"
        ;;
    *)
        echo "unknown mode $mode" >&2
        exit 2
        ;;
esac
