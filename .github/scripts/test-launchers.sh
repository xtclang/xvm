#!/usr/bin/env bash
#
# Test the xtc, xcc and xec launcher scripts of an XDK distribution zip. On Windows (Git Bash)
# this runs the .bat launchers; everywhere else, the POSIX scripts. The XDK is unpacked into a
# path with spaces, and every check runs the real launchers with a Java runtime from JAVA_HOME
# or the PATH.
#
# Usage: test-launchers.sh <xdk-distribution.zip>

set -uo pipefail

zip=${1:?usage: test-launchers.sh <xdk-distribution.zip>}

case "$(uname -s)" in
    MINGW* | MSYS* | CYGWIN*) windows=true;  ext=.bat ;;
    *)                        windows=false; ext=    ;;
esac

# The launchers on Windows need native paths in arguments and environment variables.
native() {
    if $windows; then cygpath -w "$1"; else printf '%s\n' "$1"; fi
}

# Bound each launch, so a launcher that loops (for example, when delegating to XDK_HOME) fails.
bounded() {
    if command -v timeout > /dev/null; then timeout 300 "$@"; else "$@"; fi
}

# launch [VAR=value...] -- <launcher> [arguments...]
#
# Runs a launcher with the given environment variables. On Windows, the .bat runs as if typed at
# a cmd.exe prompt: other programs start a batch file through "cmd /c", which drops the quotes of a
# quoted batch path when quoted arguments follow, so a path with spaces would not even start.
launch() {
    local vars=()
    while [[ $1 != -- ]]; do
        vars+=("$1")
        shift
    done
    shift
    if ! $windows; then
        bounded env ${vars[@]+"${vars[@]}"} "$@"
        return
    fi
    local script="$root/launch.cmd" line var arg
    line="@call \"$(native "$1")\""
    shift
    for arg in "$@"; do
        line+=" \"$arg\""
    done
    {
        for var in ${vars[@]+"${vars[@]}"}; do
            printf '@set "%s"\r\n' "$var"
        done
        printf '%s\r\n' "$line" '@exit /b %ERRORLEVEL%'
    } > "$script"
    bounded "$script"
}

failures=0

# expect <exit code|nonzero> <expected output, or ''> <description> -- <command...>
expect() {
    local want=$1 text=$2 name=$3
    shift 4
    local output code
    output=$("$@" 2>&1)
    code=$?
    if [[ $want == nonzero && $code -eq 0 ]] || [[ $want != nonzero && $code -ne $want ]]; then
        echo "FAIL: $name (exit code $code)"
    elif [[ -n $text ]] && ! grep -qF -- "$text" <<< "$output"; then
        echo "FAIL: $name (output lacks '$text')"
    else
        echo "PASS: $name"
        return
    fi
    failures=$((failures + 1))
    printf '%s\n' "$output" | tail -n 20 | sed 's/^/    /'
}

root=$(mktemp -d)
work="$root/launcher test"
trap 'rm -rf "$root"' EXIT
mkdir -p "$work/xdk home" "$work/src dir" "$work/not an xdk"

if command -v unzip > /dev/null; then
    unzip -q "$zip" -d "$work/xdk home"
else
    powershell -NoProfile -Command \
        "Expand-Archive -LiteralPath '$(native "$zip")' -DestinationPath '$(native "$work/xdk home")'"
fi || { echo "FAIL: cannot unpack $zip"; exit 1; }
xdk=$(echo "$work/xdk home"/xdk-*)
xtc="$xdk/bin/xtc$ext"
xcc="$xdk/bin/xcc$ext"
xec="$xdk/bin/xec$ext"
echo "Testing $(basename "$xtc") launchers in: $xdk"

cat > "$work/src dir/hello.x" << 'EOF'
module LaunchCheck {
    void run(String[] args = []) {
        @Inject Console console;
        console.print($"launch-check args={args.size}");
        for (String arg : args) {
            console.print($"arg=[{arg}]");
        }
        if (args.size > 0 && args[0] == "fail") {
            throw new IllegalState("requested failure");
        }
    }
}
EOF
printf 'module Broken { void run() { Int x = "s"; } }\n' > "$work/src dir/broken.x"

src=$(native "$work/src dir/hello.x")
out=$(native "$work/out dir")

# ----- the launchers start ------------------------------------------------------------------------

expect 0 "Ecstasy"     "xtc --version"     -- launch -- "$xtc" --version
expect 0 "xdk version" "xcc --version"     -- launch -- "$xcc" --version
expect 0 "xdk version" "xec --version"     -- launch -- "$xec" --version
expect 0 "Usage"       "xtc --help"        -- launch -- "$xtc" --help

# ----- compile and run ----------------------------------------------------------------------------

expect 0 ""                  "xcc compiles a module"                -- launch -- "$xcc" -o "$out" "$src"
expect 0 ""                  "xtc build compiles a module"          -- launch -- "$xtc" build -o "$(native "$work/build dir")" "$src"
expect 0 "arg=[two words]"   "xec passes arguments intact"          -- launch -- "$xec" -L "$out" LaunchCheck one "two words"
expect 0 "launch-check args" "xec runs a compiled module file"      -- launch -- "$xec" "$(native "$work/out dir/hello.xtc")"
expect 0 "arg=[x]"           "xtc run runs a module"                -- launch -- "$xtc" run -L "$out" LaunchCheck x

# ----- failures reach the exit code ---------------------------------------------------------------

expect nonzero "requested failure" "a failing module exits non-zero"        -- launch -- "$xec" -L "$out" LaunchCheck fail
expect nonzero "NoSuchModule"      "a missing module exits non-zero"        -- launch -- "$xec" -L "$out" NoSuchModule
expect nonzero "COMPILER-"         "a compile error exits non-zero"         -- launch -- "$xcc" -o "$out" "$(native "$work/src dir/broken.x")"

# ----- the JVM options ------------------------------------------------------------------------------

expect 0 "Property settings" "JAVA_OPTS reach the JVM" -- launch JAVA_OPTS=-XshowSettings:properties -- "$xec" --version
expect 0 "Property settings" "XTC_OPTS reach the JVM"  -- launch XTC_OPTS=-XshowSettings:properties -- "$xec" --version

# ----- where the launchers are started from -------------------------------------------------------

pushd "$xdk" > /dev/null || exit 1
expect 0 "Ecstasy" "a relative launcher path" -- launch -- "bin/xtc$ext" --version
popd > /dev/null || exit 1

if ! $windows; then
    mkdir -p "$work/links"
    ln -s "$xtc" "$work/links/xtc"
    expect 0 "Ecstasy" "a symlinked launcher" -- launch -- "$work/links/xtc" --version
fi

# ----- XDK_HOME -----------------------------------------------------------------------------------

expect 0 "launch-check args" "XDK_HOME set to this XDK" -- \
    launch XDK_HOME="$(native "$xdk")" -- "$xec" -L "$out" LaunchCheck
expect nonzero "Unable to locate a valid XDK" "XDK_HOME set to a directory without an XDK" -- \
    launch XDK_HOME="$(native "$work/not an xdk")" -- "$xec" --version
expect nonzero "Unable to locate a valid XDK" "XDK_HOME set to a missing directory" -- \
    launch XDK_HOME="$(native "$work/missing")" -- "$xec" --version

# Break this XDK, so only another XDK named by XDK_HOME can run the module.
other="$work/other xdk"
cp -R "$xdk" "$other"
mv "$xdk/javatools/javatools.jar" "$xdk/javatools/javatools.jar.off"
expect nonzero "Unable to locate a valid XDK" "a broken XDK" -- launch -- "$xec" --version
expect 0 "launch-check args" "XDK_HOME set to another XDK" -- \
    launch XDK_HOME="$(native "$other")" -- "$xec" -L "$out" LaunchCheck
mv "$xdk/javatools/javatools.jar.off" "$xdk/javatools/javatools.jar"

if [[ $failures -ne 0 ]]; then
    echo "❌ $failures launcher check(s) failed"
    exit 1
fi
echo "✅ All launcher checks passed"
