#!/usr/bin/env bash
# Runs inside the xvm-playbook container; playbook.sh starts it.
#   in-container.sh build                      sync /src into /work/xvm and run xdk:installDist
#   in-container.sh playbook <ids> [gradle args] sync, then run the features playbook for <ids> on Xvfb
#   in-container.sh gradle <args...>           sync, then run ./gradlew <args> on Xvfb
# /src is the read-only exported checkout, /out receives the run reports, /work and /gradle are volumes.
set -euo pipefail

mode="${1:?mode}"
shift || true

mkdir -p /work/xvm /out
# keep build outputs, caches and the IDE Starter cache (under lang/intellij-plugin/build) between runs
rsync -a --delete \
    --exclude 'build/' --exclude '.gradle/' --exclude '.kotlin/' --exclude '.intellijPlatform/' \
    --exclude 'node_modules/' --exclude 'out/' \
    /src/ /work/xvm/
cd /work/xvm

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
}

reports=/work/xvm/lang/intellij-plugin/build/reports/compiler-playbook

# the runs the work volume already has, from earlier containers, which copy_runs leaves alone
earlier="$(mktemp)"
find "$reports" -maxdepth 1 -name 'run-*' -exec basename {} \; > "$earlier" 2>/dev/null || true

copy_runs() {
    [ -d "$reports" ] || return 0
    mkdir -p /out/runs
    # copy each run directory of this container with a results.json (payload only, not the IDE cache)
    for dir in "$reports"/run-*; do
        [ -f "$dir/results.json" ] || continue
        local name
        name="$(basename "$dir")"
        if ! grep -qx "$name" "$earlier" && [ ! -f "/out/runs/$name/results.json" ]; then
            mkdir -p "/out/runs/$name"
            rsync -a "$dir/" "/out/runs/$name/"
            local ide="$reports/out/ide-tests/tests"
            for logs in "$ide"/*/"XtcCompilerPlaybook-$name"/log; do
                [ -d "$logs" ] && rsync -a --exclude 'indexing-diagnostic/' "$logs/" "/out/runs/$name/ide-log/"
            done
            echo "copied $name"
        fi
    done
}

case "$mode" in
    build)
        ./gradlew xdk:installDist "$@"
        ;;
    playbook)
        cases="${1:?case ids}"
        shift || true
        start_display
        status=0
        ./gradlew :lang:intellij-plugin:testCompilerPlaybook \
            -PincludeBuildLang=true -PincludeBuildAttachLang=true \
            --tests '*CompilerPlaybookTest.compilerPlaybook' \
            "-PintellijPlaybookCases=$cases" "$@" || status=$?
        copy_runs
        exit "$status"
        ;;
    gradle)
        start_display
        status=0
        ./gradlew "$@" || status=$?
        copy_runs
        exit "$status"
        ;;
    *)
        echo "unknown mode $mode" >&2
        exit 2
        ;;
esac
