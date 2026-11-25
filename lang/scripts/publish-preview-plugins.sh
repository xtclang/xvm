#!/usr/bin/env bash
#
# Publishes a preview of the XTC IntelliJ plugin and VS Code extension, built from a pull request's
# head commit (#693 by default), to a GitHub pre-release of xtclang/xvm. No JetBrains Marketplace.
#
# IntelliJ: add the release's updatePlugins.xml as a custom plugin repository, once, in
# Settings | Plugins | gear icon | Manage Plugin Repositories. IntelliJ then lists the plugin under
# Marketplace and offers every newer preview as an update.
# VS Code: download the .vsix from the release and run "Extensions: Install from VSIX...".
#
# Usage: lang/scripts/publish-preview-plugins.sh [--pr N] [--keep N] [--name NAME] [--dry-run]
#   --pr N     the pull request to build (default 693)
#   --name     the preview's name in IntelliJ (default "Ecstasy Language Support (bleeding edge)"), so
#              that it isn't mistaken for the Marketplace's builds; its plugin id stays the same
#   --keep N   how many previews the release keeps (default 10); older ones are deleted
#   --dry-run  build and check the preview, but upload nothing
# Or, inside any clone of xtclang/xvm, whatever it has checked out, with the script as #693 has it:
#   git fetch -q origin errs/full-testable-lsp-20261009 && bash <(git show FETCH_HEAD:lang/scripts/publish-preview-plugins.sh)
#
# It builds in a worktree of its own, $XTC_PREVIEW_WORKTREE (default ~/.cache/xtc-preview/xvm-prN),
# checked out at the pull request's head, so the checkout it runs from is not touched. The build is
# the one CI makes for the Marketplace (-PenablePublish with a unique suffix), so each preview has
# its own version, 0.4.4-SNAPSHOT.<UTC timestamp>.prN, which sorts after every earlier build.
# It needs git, gh (logged in, with write access to xtclang/xvm), unzip, python3 and a JDK.

set -euo pipefail

REPO=xtclang/xvm
PLUGIN_ID=org.xtclang.idea
PR=693
KEEP=10
DRY_RUN=false
PREVIEW_NAME="Ecstasy Language Support (bleeding edge)"

die() { echo "error: $*" >&2; exit 1; }

while [ $# -gt 0 ]; do
    case "$1" in
        --pr) PR="${2:?--pr needs a number}"; shift 2 ;;
        --keep) KEEP="${2:?--keep needs a number}"; shift 2 ;;
        --name) PREVIEW_NAME="${2:?--name needs a name}"; shift 2 ;;
        --dry-run) DRY_RUN=true; shift ;;
        -h|--help) echo "Usage: publish-preview-plugins.sh [--pr N] [--keep N] [--name NAME] [--dry-run]; see the comment at its top"; exit 0 ;;
        *) die "unknown argument: $1" ;;
    esac
done
[[ "$PR" =~ ^[0-9]+$ && "$KEEP" =~ ^[0-9]+$ && "$KEEP" -ge 1 ]] || die "--pr and --keep take positive numbers"

for command in git gh unzip python3; do
    command -v "$command" >/dev/null || die "$command is required"
done

TAG="intellij-plugin-pr${PR}"
WORKTREE="${XTC_PREVIEW_WORKTREE:-$HOME/.cache/xtc-preview/xvm-pr${PR}}"
# The xvm clone to work with: the one the current directory is in, else the one this script is in
SOURCE=$(git rev-parse --show-toplevel 2>/dev/null || git -C "$(dirname "$0")" rev-parse --show-toplevel 2>/dev/null) \
    || die "run this inside a clone of $REPO"
git -C "$SOURCE" remote get-url origin | grep -q -i "xtclang/xvm" || die "$SOURCE is not a clone of $REPO"

# The pull request's head, as GitHub has it now
read -r HEAD_SHA HEAD_REF < <(gh pr view "$PR" -R "$REPO" --json headRefOid,headRefName --jq '"\(.headRefOid) \(.headRefName)"')
echo "Pull request #$PR: $HEAD_REF at ${HEAD_SHA:0:10}"
git -C "$SOURCE" fetch --quiet origin "$HEAD_REF"
[ "$(git -C "$SOURCE" rev-parse FETCH_HEAD)" = "$HEAD_SHA" ] || die "origin/$HEAD_REF isn't at the pull request's head yet; try again"

# A worktree of its own, at that commit
if [ -d "$WORKTREE" ]; then
    [ -z "$(git -C "$WORKTREE" status --porcelain)" ] || die "$WORKTREE has local changes"
    git -C "$WORKTREE" fetch --quiet origin "$HEAD_REF"
    git -C "$WORKTREE" checkout --quiet --detach "$HEAD_SHA"
else
    mkdir -p "$(dirname "$WORKTREE")"
    git -C "$SOURCE" worktree add --quiet --detach "$WORKTREE" "$HEAD_SHA"
fi

# Build as CI does for the Marketplace, with a suffix of our own
SUFFIX="$(date -u +%Y%m%d%H%M%S).pr${PR}"
DISTRIBUTIONS="$WORKTREE/lang/intellij-plugin/build/distributions"
VSIX_DIR="$WORKTREE/lang/vscode-extension/build/distributions"
rm -f "$DISTRIBUTIONS"/*.zip "$VSIX_DIR"/*.vsix
echo "Building the plugin and the extension in $WORKTREE (suffix $SUFFIX)..."
(cd "$WORKTREE" && ./gradlew --console=plain --quiet \
    :lang:intellij-plugin:buildPlugin :lang:vscode-extension:packageExtension \
    -PincludeBuildLang=true -PincludeBuildAttachLang=true \
    -PenablePublish=true -Pjetbrains.publish.suffix="$SUFFIX" -Plsp.buildSearchableOptions=true)

ZIP=$(find "$DISTRIBUTIONS" -maxdepth 1 -name '*.zip' | head -1)
VSIX=$(find "$VSIX_DIR" -maxdepth 1 -name '*.vsix' | head -1)
[ -f "$ZIP" ] || die "no plugin ZIP in $DISTRIBUTIONS"
[ -f "$VSIX" ] || die "no VSIX in $VSIX_DIR"

OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT

# The version, name and since-build as the plugin's own plugin.xml has them
cat > "$OUT/plugin_info.py" <<'EOF'
import io, re, sys, zipfile
zip_path, plugin_id = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(zip_path) as plugin:
    for jar in (n for n in plugin.namelist() if n.endswith('.jar')):
        with zipfile.ZipFile(io.BytesIO(plugin.read(jar))) as entries:
            if 'META-INF/plugin.xml' not in entries.namelist():
                continue
            xml = entries.read('META-INF/plugin.xml').decode('utf-8')
            if f'<id>{plugin_id}</id>' not in xml:
                continue
            version = re.search(r'<version>([^<]+)</version>', xml).group(1).strip()
            since = re.search(r'<idea-version[^>]*since-build="([^"]+)"', xml).group(1)
            name = re.search(r'<name>([^<]+)</name>', xml).group(1).strip()
            print(version, since, name)
            sys.exit(0)
sys.exit(f'no plugin.xml with <id>{plugin_id}</id> in {zip_path}')
EOF
read -r VERSION SINCE_BUILD BUILT_NAME < <(python3 "$OUT/plugin_info.py" "$ZIP" "$PLUGIN_ID")
[[ "$VERSION" == *".$SUFFIX" ]] || die "the plugin's version is $VERSION, not one ending with .$SUFFIX"
echo "Built $BUILT_NAME $VERSION (since-build $SINCE_BUILD)"

ZIP_ASSET="xtc-intellij-plugin-${VERSION}.zip"
VSIX_ASSET="xtc-vscode-extension-${VERSION}.vsix"

# The preview's own name in its plugin.xml, so that IntelliJ shows it apart from the Marketplace's builds
cat > "$OUT/rename_plugin.py" <<'EOF'
import io, re, sys, zipfile
from xml.sax.saxutils import escape
source, target, plugin_id, name = sys.argv[1:5]
renamed = False
with zipfile.ZipFile(source) as plugin, zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as out:
    for entry in plugin.infolist():
        data = plugin.read(entry.filename)
        if not renamed and entry.filename.endswith('.jar'):
            with zipfile.ZipFile(io.BytesIO(data)) as jar:
                names = jar.namelist()
                xml = jar.read('META-INF/plugin.xml').decode('utf-8') if 'META-INF/plugin.xml' in names else ''
                if f'<id>{plugin_id}</id>' in xml:
                    xml = re.sub(r'<name>[^<]*</name>', f'<name>{escape(name)}</name>', xml, count=1)
                    buffer = io.BytesIO()
                    with zipfile.ZipFile(buffer, 'w', zipfile.ZIP_DEFLATED) as renamed_jar:
                        for jar_entry in jar.infolist():
                            content = xml.encode('utf-8') if jar_entry.filename == 'META-INF/plugin.xml' else jar.read(jar_entry.filename)
                            renamed_jar.writestr(jar_entry, content)
                    data = buffer.getvalue()
                    renamed = True
        out.writestr(entry, data)
sys.exit(0 if renamed else f'no plugin.xml with <id>{plugin_id}</id> in {source}')
EOF
python3 "$OUT/rename_plugin.py" "$ZIP" "$OUT/$ZIP_ASSET" "$PLUGIN_ID" "$PREVIEW_NAME"
read -r _ _ NAME < <(python3 "$OUT/plugin_info.py" "$OUT/$ZIP_ASSET" "$PLUGIN_ID")
[ "$NAME" = "$PREVIEW_NAME" ] || die "the preview is named \"$NAME\", not \"$PREVIEW_NAME\""
echo "Named it $NAME"
cp "$VSIX" "$OUT/$VSIX_ASSET"
(cd "$OUT" && { command -v sha256sum >/dev/null && sha256sum "$ZIP_ASSET" || shasum -a 256 "$ZIP_ASSET"; } > "$ZIP_ASSET.sha256")

DOWNLOAD="https://github.com/$REPO/releases/download/$TAG"
BUILT=$(date -u +'%Y-%m-%d %H:%M UTC')
cat > "$OUT/updatePlugins.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<plugins>
  <plugin id="$PLUGIN_ID" url="$DOWNLOAD/$ZIP_ASSET" version="$VERSION">
    <idea-version since-build="$SINCE_BUILD"/>
    <name>$NAME</name>
    <vendor>xtclang</vendor>
    <description><![CDATA[Preview of <a href="https://github.com/$REPO/pull/$PR">xtclang/xvm#$PR</a>, built from ${HEAD_SHA:0:10} on $BUILT.]]></description>
  </plugin>
</plugins>
EOF
python3 -c "import sys, xml.dom.minidom as m; m.parse(sys.argv[1])" "$OUT/updatePlugins.xml" || die "updatePlugins.xml isn't well-formed"

cat > "$OUT/notes.md" <<EOF
Previews of the XTC IntelliJ plugin and VS Code extension built from the head of [#$PR](https://github.com/$REPO/pull/$PR), before it is merged. They are not on the JetBrains Marketplace.

**Latest:** \`$VERSION\`, from \`${HEAD_SHA:0:10}\`, built $BUILT.

**IntelliJ IDEA:** add this plugin repository once, in Settings | Plugins | ⚙️ | Manage Plugin Repositories:

\`\`\`
$DOWNLOAD/updatePlugins.xml
\`\`\`

Paste it without a trailing space: IntelliJ keeps the space and then fails to download the plugin.

Then search for "$NAME" under Marketplace. IntelliJ offers every newer preview as an update. If you also follow the Marketplace's alpha channel, IntelliJ installs whichever build is newest, from either.

**VS Code:** download \`$VSIX_ASSET\` below and run "Extensions: Install from VSIX...".

The release keeps the last $KEEP previews.
EOF

if $DRY_RUN; then
    echo "Dry run: nothing uploaded. The files are:"
    ls -l "$OUT"
    trap - EXIT
    echo "(kept in $OUT)"
    exit 0
fi

if ! gh release view "$TAG" -R "$REPO" >/dev/null 2>&1; then
    gh release create "$TAG" -R "$REPO" --prerelease --target "$HEAD_SHA" \
        --title "XTC IntelliJ plugin previews from #$PR" --notes-file "$OUT/notes.md"
fi
gh release upload "$TAG" -R "$REPO" "$OUT/$ZIP_ASSET" "$OUT/$ZIP_ASSET.sha256" "$OUT/$VSIX_ASSET"
gh release upload "$TAG" -R "$REPO" --clobber "$OUT/updatePlugins.xml"
gh release edit "$TAG" -R "$REPO" --notes-file "$OUT/notes.md" >/dev/null

# Keep the newest previews: a preview is its ZIP, the ZIP's checksum and its VSIX
OLD=$(gh api "repos/$REPO/releases/tags/$TAG" --jq '.assets[] | select(.name | test("^xtc-intellij-plugin-.*\\.zip$")) | .name' \
    | sed 's/^xtc-intellij-plugin-\(.*\)\.zip$/\1/' | sort -r | tail -n +$((KEEP + 1)))
for old in $OLD; do
    for asset in "xtc-intellij-plugin-$old.zip" "xtc-intellij-plugin-$old.zip.sha256" "xtc-vscode-extension-$old.vsix"; do
        gh release delete-asset "$TAG" "$asset" -R "$REPO" --yes >/dev/null 2>&1 || true
    done
    echo "Deleted the preview $old"
done

echo "Published $VERSION to https://github.com/$REPO/releases/tag/$TAG"
echo "Plugin repository: $DOWNLOAD/updatePlugins.xml"
