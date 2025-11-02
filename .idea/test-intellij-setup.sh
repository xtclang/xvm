#!/bin/bash
# Automated test to verify IntelliJ setup works out of the box
# This script proves that with correct configuration, no manual steps are needed

set -e

echo "=================================="
echo "IntelliJ Setup Verification Test"
echo "=================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

function check_pass() {
    echo -e "${GREEN}✅ PASS${NC}: $1"
}

function check_fail() {
    echo -e "${RED}❌ FAIL${NC}: $1"
    exit 1
}

function check_warn() {
    echo -e "${YELLOW}⚠️  WARN${NC}: $1"
}

# Step 1: Verify Java 25
echo "Step 1: Checking Java version..."
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -eq 25 ]; then
    check_pass "Java 25 detected"
else
    check_fail "Java $JAVA_VERSION detected, need Java 25"
fi
echo ""

# Step 2: Verify JAVA_HOME
echo "Step 2: Checking JAVA_HOME..."
if [ -n "$JAVA_HOME" ]; then
    check_pass "JAVA_HOME is set: $JAVA_HOME"
else
    check_warn "JAVA_HOME not set (may cause issues)"
fi
echo ""

# Step 3: Verify Gradle wrapper works
echo "Step 3: Testing Gradle wrapper..."
if ./gradlew --version > /dev/null 2>&1; then
    check_pass "Gradle wrapper executable"
else
    check_fail "Gradle wrapper failed"
fi
echo ""

# Step 4: Test Gradle projects resolution
echo "Step 4: Testing Gradle project structure..."
if ./gradlew projects --quiet > /dev/null 2>&1; then
    check_pass "Gradle project structure resolves"
else
    check_fail "Gradle projects command failed"
fi
echo ""

# Step 5: Test plugin build (requires Java 25)
echo "Step 5: Testing plugin build (Java 25 requirement)..."
if ./gradlew :plugin:jar --quiet > /dev/null 2>&1; then
    check_pass "Plugin builds successfully with Java 25"
else
    check_fail "Plugin build failed (likely Java version issue)"
fi
echo ""

# Step 6: Test javatools resource generation
echo "Step 6: Testing javatools resource generation..."
./gradlew :javatools:clean > /dev/null 2>&1
./gradlew :javatools:processResources --quiet > /dev/null 2>&1

if [ -f "javatools/build/resources/main/implicit.x" ]; then
    check_pass "implicit.x generated"
else
    check_fail "implicit.x NOT generated"
fi

if [ -f "javatools/build/resources/main/build-info.properties" ]; then
    check_pass "build-info.properties generated"
else
    check_fail "build-info.properties NOT generated"
fi

if [ -f "javatools/build/resources/main/errors.properties" ]; then
    check_pass "errors.properties present"
else
    check_fail "errors.properties NOT present"
fi
echo ""

# Step 7: Test javatools jar packaging
echo "Step 7: Testing javatools jar packaging..."
./gradlew :javatools:jar --quiet > /dev/null 2>&1

JAR_FILE=$(find javatools/build/libs -name "javatools-*.jar" | head -n 1)
if [ -f "$JAR_FILE" ]; then
    check_pass "javatools jar created: $JAR_FILE"

    # Verify resources in jar
    if jar tf "$JAR_FILE" | grep -q "implicit.x"; then
        check_pass "implicit.x packaged in jar"
    else
        check_fail "implicit.x NOT in jar"
    fi

    if jar tf "$JAR_FILE" | grep -q "build-info.properties"; then
        check_pass "build-info.properties packaged in jar"
    else
        check_fail "build-info.properties NOT in jar"
    fi
else
    check_fail "javatools jar NOT created"
fi
echo ""

# Step 8: Test IntelliJ Gradle configuration (if .idea exists)
echo "Step 8: Checking IntelliJ configuration..."
if [ -f ".idea/gradle.xml" ]; then
    if grep -q 'delegatedBuild.*false' .idea/gradle.xml; then
        check_warn "IntelliJ using IDEA build mode (should use Gradle)"
        echo "   Fix: Preferences → Gradle → Build and run using: Gradle"
    else
        check_pass "IntelliJ configured to use Gradle build mode"
    fi

    if grep -q 'testRunner.*PLATFORM' .idea/gradle.xml; then
        check_warn "IntelliJ using IDEA test runner (should use Gradle)"
        echo "   Fix: Preferences → Gradle → Run tests using: Gradle"
    elif grep -q 'testRunner.*GRADLE' .idea/gradle.xml; then
        check_pass "IntelliJ configured to use Gradle test runner"
    fi
else
    check_warn ".idea/gradle.xml not found (project not yet opened in IntelliJ)"
fi
echo ""

# Step 9: Check for common issues
echo "Step 9: Checking for common issues..."

# Check for broken symlinks
if find . -type l -! -exec test -e {} \; -print 2>/dev/null | grep -q gradle.properties; then
    check_warn "Found broken symlink to gradle.properties"
else
    check_pass "No broken symlinks detected"
fi

# Check for leftover out/ directories from IntelliJ IDEA mode
if [ -d "javatools/out" ]; then
    check_warn "IntelliJ 'out/' directory exists (may indicate IDEA mode was used)"
    echo "   These can be deleted: rm -rf */out/"
else
    check_pass "No IntelliJ 'out/' directories (clean state)"
fi
echo ""

# Final summary
echo "=================================="
echo "Test Summary"
echo "=================================="
echo ""
echo "If all tests passed:"
echo "  ✅ Gradle build works correctly"
echo "  ✅ Resources are generated automatically"
echo "  ✅ Java 25 requirement is satisfied"
echo ""
echo "For IntelliJ to work correctly:"
echo "  1. Open project in IntelliJ"
echo "  2. Preferences → Build Tools → Gradle"
echo "  3. Gradle JVM: Java 25"
echo "  4. Build and run using: Gradle"
echo "  5. Run tests using: Gradle"
echo "  6. Reload Gradle project"
echo ""
echo "No manual resource copying needed!"
echo "=================================="
