#!/bin/bash
# Fix IntelliJ settings that get overwritten when changing preferences
# Run this after IntelliJ overwrites your settings to Java 6

set -e

echo "🔧 Fixing IntelliJ IDEA settings..."

# Fix compiler.xml
if [ -f ".idea/compiler.xml" ]; then
    if grep -q 'target="1\.6"' .idea/compiler.xml; then
        echo "❌ Found Java 6 bytecode target, fixing to 25..."
        sed -i.bak 's/target="1\.6"/target="25"/' .idea/compiler.xml
        rm .idea/compiler.xml.bak
        echo "✅ Fixed compiler.xml"
    else
        echo "✓ compiler.xml is correct"
    fi
fi

# Fix gradle.xml - delegatedBuild
if [ -f ".idea/gradle.xml" ]; then
    CHANGED=0

    if grep -q '<option name="delegatedBuild" value="false" />' .idea/gradle.xml; then
        echo "❌ Found IDEA build mode, switching to Gradle mode..."
        sed -i.bak 's/<option name="delegatedBuild" value="false" \/>/<option name="delegatedBuild" value="true" \/>/' .idea/gradle.xml
        CHANGED=1
    fi

    if grep -q '<option name="testRunner" value="PLATFORM" />' .idea/gradle.xml; then
        echo "❌ Found IDEA test runner, switching to Gradle..."
        sed -i.bak 's/<option name="testRunner" value="PLATFORM" \/>/<option name="testRunner" value="GRADLE" \/>/' .idea/gradle.xml
        CHANGED=1
    fi

    if [ $CHANGED -eq 1 ]; then
        rm .idea/gradle.xml.bak 2>/dev/null || true
        echo "✅ Fixed gradle.xml"
        echo "⚠️  RESTART IntelliJ for changes to take effect!"
    else
        echo "✓ gradle.xml is correct"
    fi
fi

echo ""
echo "✅ Done! IntelliJ settings have been fixed."
echo ""
echo "If you need to do this again after changing preferences:"
echo "  ./fix-idea-settings.sh"
