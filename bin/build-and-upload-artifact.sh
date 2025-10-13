#!/bin/bash
#
# Build XDK snapshot and optionally trigger CI to upload as GitHub artifact
#
# Usage:
#   ./bin/build-and-upload-artifact.sh              # Build locally only
#   ./bin/build-and-upload-artifact.sh --upload     # Build locally then trigger CI
#   ./bin/build-and-upload-artifact.sh --ci-only    # Only trigger CI (no local build)
#

set -euo pipefail

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Get current branch and commit
BRANCH=$(git rev-parse --abbrev-ref HEAD)
COMMIT=$(git rev-parse HEAD)
SHORT_COMMIT=$(git rev-parse --short HEAD)

echo -e "${BLUE}=== XDK Build and Upload ===${NC}"
echo -e "Branch: ${GREEN}$BRANCH${NC}"
echo -e "Commit: ${GREEN}$SHORT_COMMIT${NC} ($COMMIT)"
echo ""

# Parse arguments
UPLOAD=false
CI_ONLY=false
CHECK_STATUS=false

for arg in "$@"; do
    case $arg in
        --upload)
            UPLOAD=true
            shift
            ;;
        --ci-only)
            CI_ONLY=true
            shift
            ;;
        --check|--status)
            CHECK_STATUS=true
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --check      Check if CI has run for current commit and show artifact info"
            echo "  --upload     Build locally and trigger CI to upload artifact"
            echo "  --ci-only    Skip local build, only trigger CI"
            echo "  --help       Show this help message"
            echo ""
            echo "Examples:"
            echo "  $0                  # Build locally only"
            echo "  $0 --check          # Check for existing CI runs and artifacts"
            echo "  $0 --upload         # Build locally then trigger CI"
            echo "  $0 --ci-only        # Only trigger CI"
            exit 0
            ;;
    esac
done

# Check status mode
if [ "$CHECK_STATUS" = true ]; then
    echo -e "${BLUE}Checking for CI runs for commit $SHORT_COMMIT...${NC}"
    echo ""

    # Check if gh CLI is available
    if ! command -v gh &> /dev/null; then
        echo -e "${RED}Error: gh CLI not found. Install with: brew install gh${NC}"
        exit 1
    fi

    # Query for workflow runs matching this commit
    RUN_JSON=$(gh run list --branch="$BRANCH" --limit 10 --json databaseId,status,conclusion,headSha,name,createdAt,workflowName 2>/dev/null)

    if [ -z "$RUN_JSON" ]; then
        echo -e "${RED}❌ No workflow runs found${NC}"
        exit 1
    fi

    # Filter for runs matching current commit
    MATCHING_RUN=$(echo "$RUN_JSON" | jq --arg commit "$COMMIT" '.[] | select(.headSha == $commit and .workflowName == "VerifyCommit") | {databaseId, status, conclusion, createdAt}' | jq -s 'sort_by(.createdAt) | reverse | .[0]')

    if [ "$MATCHING_RUN" = "null" ] || [ -z "$MATCHING_RUN" ]; then
        echo -e "${YELLOW}⚠️  No CI runs found for commit $SHORT_COMMIT${NC}"
        echo ""
        echo "Trigger a CI run with:"
        echo -e "  ${BLUE}$0 --ci-only${NC}"
        exit 0
    fi

    # Extract run details
    RUN_ID=$(echo "$MATCHING_RUN" | jq -r '.databaseId')
    STATUS=$(echo "$MATCHING_RUN" | jq -r '.status')
    CONCLUSION=$(echo "$MATCHING_RUN" | jq -r '.conclusion')
    CREATED_AT=$(echo "$MATCHING_RUN" | jq -r '.createdAt')

    echo -e "${GREEN}✅ Found CI run for this commit${NC}"
    echo ""
    echo -e "Run ID:     ${GREEN}$RUN_ID${NC}"
    echo -e "Status:     ${GREEN}$STATUS${NC}"
    echo -e "Conclusion: ${GREEN}$CONCLUSION${NC}"
    echo -e "Created:    $CREATED_AT"
    echo ""

    # Check for artifacts if run completed successfully
    if [ "$STATUS" = "completed" ] && [ "$CONCLUSION" = "success" ]; then
        echo -e "${BLUE}Checking for artifacts...${NC}"

        ARTIFACT_JSON=$(gh api "/repos/{owner}/{repo}/actions/runs/$RUN_ID/artifacts" 2>/dev/null)
        ARTIFACT_NAME=$(echo "$ARTIFACT_JSON" | jq -r --arg commit "$COMMIT" '.artifacts[] | select(.name == "xdk-dist-\($commit)") | .name')
        ARTIFACT_SIZE=$(echo "$ARTIFACT_JSON" | jq -r --arg commit "$COMMIT" '.artifacts[] | select(.name == "xdk-dist-\($commit)") | .size_in_bytes')

        if [ -n "$ARTIFACT_NAME" ] && [ "$ARTIFACT_NAME" != "null" ]; then
            SIZE_MB=$(echo "scale=2; $ARTIFACT_SIZE / 1048576" | bc)
            echo -e "${GREEN}✅ Artifact available${NC}"
            echo ""
            echo -e "Artifact:   ${GREEN}$ARTIFACT_NAME${NC}"
            echo -e "Size:       ${GREEN}${SIZE_MB} MB${NC}"
            echo ""
            echo -e "${BLUE}You can test publishing workflows with:${NC}"
            echo ""
            echo -e "${YELLOW}# Publish Maven snapshots + GitHub snapshot release${NC}"
            echo -e "  gh workflow run publish-snapshot.yml --ref $BRANCH -f ci-run-id=$RUN_ID"
            echo ""
            echo -e "${YELLOW}# Build and publish Docker images${NC}"
            echo -e "  gh workflow run publish-docker.yml --ref $BRANCH -f ci-run-id=$RUN_ID"
            echo ""
            echo -e "${YELLOW}# Update Homebrew tap formula${NC}"
            echo -e "  gh workflow run homebrew-update.yml --ref $BRANCH -f ci-run-id=$RUN_ID"
        else
            echo -e "${YELLOW}⚠️  No artifact found (may still be uploading)${NC}"
            echo ""
            echo "Wait a moment and check again, or monitor with:"
            echo -e "  ${BLUE}gh run watch $RUN_ID${NC}"
        fi
    elif [ "$STATUS" = "in_progress" ] || [ "$STATUS" = "queued" ]; then
        echo -e "${YELLOW}⏳ CI run is still in progress...${NC}"
        echo ""
        echo "Monitor progress with:"
        echo -e "  ${BLUE}gh run watch $RUN_ID${NC}"
    else
        echo -e "${RED}❌ CI run did not complete successfully${NC}"
        echo ""
        echo "View logs with:"
        echo -e "  ${BLUE}gh run view $RUN_ID --log-failed${NC}"
    fi

    exit 0
fi

# Build locally unless --ci-only
if [ "$CI_ONLY" = false ]; then
    echo -e "${BLUE}Building XDK locally...${NC}"

    # Check if gradle wrapper exists
    if [ ! -f "./gradlew" ]; then
        echo -e "${RED}Error: gradlew not found. Run from repository root.${NC}"
        exit 1
    fi

    # Read Gradle options from version setup (simplified)
    GRADLE_OPTIONS="--stacktrace --console=plain"

    echo -e "${YELLOW}Step 1/3: Clean${NC}"
    ./gradlew $GRADLE_OPTIONS clean

    echo -e "${YELLOW}Step 2/3: Check (tests)${NC}"
    ./gradlew $GRADLE_OPTIONS check \
        -Porg.xtclang.java.lint=true \
        -Porg.xtclang.java.warningsAsErrors=false \
        -Porg.xtclang.java.test.stdout=true

    echo -e "${YELLOW}Step 3/3: Build distribution${NC}"
    ./gradlew $GRADLE_OPTIONS :xdk:distZip

    # Find the built zip
    XDK_ZIP=$(find xdk/build/distributions -name "xdk-*.zip" | head -1)

    if [ -n "$XDK_ZIP" ]; then
        ZIP_SIZE=$(du -h "$XDK_ZIP" | cut -f1)
        echo -e "${GREEN}✅ Build complete!${NC}"
        echo -e "Distribution: ${GREEN}$XDK_ZIP${NC} ($ZIP_SIZE)"
        echo ""
    else
        echo -e "${RED}❌ Error: Distribution ZIP not found${NC}"
        exit 1
    fi
fi

# Upload via CI if requested
if [ "$UPLOAD" = true ] || [ "$CI_ONLY" = true ]; then
    echo -e "${BLUE}Triggering CI workflow to upload artifact...${NC}"
    echo ""
    echo -e "${YELLOW}Note: GitHub artifacts can only be uploaded during workflow runs.${NC}"
    echo -e "${YELLOW}This will trigger VerifyCommit which will build and upload.${NC}"
    echo ""

    # Check if gh CLI is available
    if ! command -v gh &> /dev/null; then
        echo -e "${RED}Error: gh CLI not found. Install with: brew install gh${NC}"
        exit 1
    fi

    # Trigger CI workflow
    echo "Running: gh workflow run commit.yml --ref $BRANCH -f platforms=ubuntu-latest"
    gh workflow run commit.yml --ref "$BRANCH" -f platforms=ubuntu-latest

    echo ""
    echo -e "${GREEN}✅ CI workflow triggered!${NC}"
    echo ""
    echo "Monitor progress with:"
    echo -e "  ${BLUE}gh run watch${NC}"
    echo ""
    echo "Once complete, artifact will be available as:"
    echo -e "  ${BLUE}xdk-dist-$COMMIT${NC}"
    echo ""
    echo "You can then test publishing workflows with:"
    echo -e "  ${BLUE}gh workflow run publish-snapshot.yml --ref $BRANCH -f ci-run-id=<RUN_ID>${NC}"
    echo -e "  ${BLUE}gh workflow run publish-docker.yml --ref $BRANCH -f ci-run-id=<RUN_ID>${NC}"
    echo -e "  ${BLUE}gh workflow run homebrew-update.yml --ref $BRANCH -f ci-run-id=<RUN_ID>${NC}"
    echo ""
    echo "Get RUN_ID with: gh run list --limit 1 --json databaseId --jq '.[0].databaseId'"
fi

echo -e "${GREEN}Done!${NC}"
