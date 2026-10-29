#!/usr/bin/env bash
# Runs the IntelliJ compiler playbook in a Linux container under Podman, on a virtual display (Xvfb),
# so that no IDE window opens on the desktop and nothing typed or focused there reaches the test IDE.
#
#   playbook.sh image                       build the image, localhost/xvm-playbook
#   playbook.sh build                       xdk:installDist and publishLocal in the container (X336 runs the XDK's xec,
#                                           X303 builds with the plugin and XDK from mavenLocal)
#   playbook.sh playbook X259,X324 [args]   the features playbook for those cases, or all, with Gradle args such as
#                                           -Plsp4ijChannel=nightly -Plsp4ijVersion=<pinned version>
#   playbook.sh gradle <args...>            any Gradle invocation in the container, on the virtual display
#
# Each run exports this checkout as it is, uncommitted changes included, and copies the playbook's run
# directories to lang/intellij-plugin/build/reports/compiler-playbook, where a native run puts them. The
# volumes xvm-playbook-gradle, xvm-playbook-work and xvm-playbook-m2 keep Gradle's caches, the build and
# mavenLocal between runs.
# The container is aarch64 Linux on an Apple machine: its fonts and timing are not macOS's. The export
# has no .git, so the build's version information says it found none.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
root="$(git -C "$here" rev-parse --show-toplevel)"
container="$root/lang/intellij-plugin/build/container"
image=localhost/xvm-playbook:latest
mode="${1:?mode: image, build, playbook or gradle}"

if [ "$mode" = "image" ]; then
    exec podman build -t "$image" -f "$here/Containerfile" "$here"
fi

# the checkout's files, tracked or not, without what git ignores, such as build outputs, and without
# tracked files deleted from the working tree
rm -rf "$container/src.new"
mkdir -p "$container/src.new" "$container/out"
(
    cd "$root"
    git ls-files -z --cached --others --exclude-standard \
        | while IFS= read -r -d '' file; do if [ -e "$file" ]; then printf '%s\0' "$file"; fi; done \
        | tar --null -T - -cf -
) | tar -x -C "$container/src.new"
rm -rf "$container/src"
mv "$container/src.new" "$container/src"

stamp="$(date +%Y%m%d-%H%M%S)"
status=0
# --init makes PID 1 reap orphans: without it, a Gradle daemon that exits on a cancel stays a zombie, which Java
# reports alive (X260 waits for the canceled daemon to exit)
podman run --rm --init --name "xvm-playbook-$stamp" \
    --memory 7g --shm-size 1g --security-opt label=disable \
    -v xvm-playbook-gradle:/gradle \
    -v xvm-playbook-work:/work \
    -v xvm-playbook-m2:/home/playbook/.m2 \
    -v "$container/src:/src:ro" \
    -v "$container/out:/out" \
    -v "$here/in-container.sh:/in-container.sh:ro" \
    "$image" \
    bash /in-container.sh "$@" 2>&1 | tee "$container/out/$mode-$stamp.log" || status=$?

# where a native run's reports are, so that the same tools read them
reports="$root/lang/intellij-plugin/build/reports/compiler-playbook"
if [ -d "$container/out/runs" ]; then
    mkdir -p "$reports"
    rsync -a "$container/out/runs/" "$reports/"
fi
exit "$status"
