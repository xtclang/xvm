#!/bin/bash
# Trigger the commit.yml workflow with publish-snapshots=true
# and monitor its progress until completion.

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🚀 Triggering 'Verify Commit' workflow with publish-snapshots=true and skip-tests=true...${NC}"

# Get the current branch
CURRENT_BRANCH=$(git branch --show-current)
echo -e "${BLUE}Branch: ${CURRENT_BRANCH}${NC}"
echo -e "${YELLOW}⚠️  Manual tests will be skipped${NC}"

# Trigger the workflow
gh workflow run "Verify Commit" \
    --ref "$CURRENT_BRANCH" \
    --field publish-snapshots=true \
    --field skip-tests=true

echo -e "${GREEN}✅ Workflow triggered${NC}"
echo -e "${YELLOW}⏳ Waiting for workflow run to start...${NC}"

# Wait a bit for the workflow to be created
sleep 3

# Get the most recent workflow run ID for this branch
RUN_ID=$(gh run list \
    --workflow="Verify Commit" \
    --branch="$CURRENT_BRANCH" \
    --limit=1 \
    --json databaseId \
    --jq '.[0].databaseId')

if [ -z "$RUN_ID" ]; then
    echo -e "${RED}❌ Failed to get workflow run ID${NC}"
    exit 1
fi

echo -e "${BLUE}📊 Workflow Run ID: ${RUN_ID}${NC}"
echo -e "${BLUE}🔗 View in browser: $(gh run view "$RUN_ID" --json url --jq '.url')${NC}"
echo ""
echo -e "${YELLOW}👀 Monitoring workflow progress...${NC}"
echo ""

# Watch the workflow run (this will stream logs and block until completion)
gh run watch "$RUN_ID" --exit-status

# Get the final status
STATUS=$(gh run view "$RUN_ID" --json status,conclusion --jq '.conclusion')

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if [ "$STATUS" = "success" ]; then
    echo -e "${GREEN}✅ Workflow completed successfully!${NC}"
    echo ""
    echo -e "${BLUE}📦 Monitoring triggered publishing workflows...${NC}"
    echo ""

    # Wait for workflows to be created
    echo -e "${YELLOW}⏳ Waiting for publishing workflows to start...${NC}"
    sleep 8

    # Monitor each triggered workflow
    WORKFLOWS=("Publish Snapshots" "Build Docker Images" "Update Homebrew")
    FAILED_WORKFLOWS=()

    echo -e "${BLUE}Looking for workflows triggered by CI run: ${RUN_ID}${NC}"
    echo ""

    for WORKFLOW in "${WORKFLOWS[@]}"; do
        echo ""
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo -e "${BLUE}📋 Checking: ${WORKFLOW}${NC}"

        # Get recent runs for this workflow and find one with matching ci-run-id
        WORKFLOW_RUN_ID=""
        CANDIDATE_RUNS=$(gh run list \
            --workflow="$WORKFLOW" \
            --branch="$CURRENT_BRANCH" \
            --limit=5 \
            --json databaseId \
            --jq '.[].databaseId' 2>/dev/null || echo "")

        # Check each candidate run to see if it has the matching ci-run-id
        for CANDIDATE_ID in $CANDIDATE_RUNS; do
            CI_RUN_ID=$(gh run view "$CANDIDATE_ID" --json displayTitle,jobs \
                2>/dev/null | jq -r '.jobs[0].steps[] | select(.name == "Determine commit and run ID") | .conclusion' 2>/dev/null || echo "")

            # Alternative: check the workflow run for inputs (for workflow_dispatch)
            INPUT_CI_RUN_ID=$(gh api "/repos/$(gh repo view --json nameWithOwner -q .nameWithOwner)/actions/runs/$CANDIDATE_ID" \
                2>/dev/null | jq -r '.inputs."ci-run-id" // empty' 2>/dev/null || echo "")

            if [ "$INPUT_CI_RUN_ID" = "$RUN_ID" ]; then
                WORKFLOW_RUN_ID="$CANDIDATE_ID"
                echo -e "${GREEN}✓ Found matching workflow (ci-run-id: ${RUN_ID})${NC}"
                break
            fi
        done

        if [ -z "$WORKFLOW_RUN_ID" ]; then
            # Fallback: just use the most recent run
            WORKFLOW_RUN_ID=$(echo "$CANDIDATE_RUNS" | head -1)
            if [ -n "$WORKFLOW_RUN_ID" ]; then
                echo -e "${YELLOW}⚠️  Could not verify ci-run-id match, using most recent run${NC}"
            fi
        fi

        if [ -z "$WORKFLOW_RUN_ID" ]; then
            echo -e "${YELLOW}⚠️  No run found for '${WORKFLOW}' on branch '${CURRENT_BRANCH}'${NC}"
            echo -e "${YELLOW}   This workflow may not have been triggered or is still queuing${NC}"
            FAILED_WORKFLOWS+=("$WORKFLOW (not found)")
            continue
        fi

        echo -e "${BLUE}🔗 Monitoring: ${WORKFLOW} (Run ID: ${WORKFLOW_RUN_ID})${NC}"
        echo -e "${BLUE}   URL: $(gh run view "$WORKFLOW_RUN_ID" --json url --jq '.url')${NC}"
        echo ""

        # Watch the workflow (this blocks until completion)
        if gh run watch "$WORKFLOW_RUN_ID" --exit-status 2>/dev/null; then
            echo ""
            echo -e "${GREEN}✅ ${WORKFLOW} completed successfully${NC}"
        else
            echo ""
            echo -e "${RED}❌ ${WORKFLOW} failed${NC}"
            FAILED_WORKFLOWS+=("$WORKFLOW")
        fi
    done

    # Final summary
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""

    if [ ${#FAILED_WORKFLOWS[@]} -eq 0 ]; then
        echo -e "${GREEN}🎉 All publishing workflows completed successfully!${NC}"
        echo ""
        echo -e "${BLUE}Published artifacts:${NC}"
        echo "  • Maven snapshots → GitHub Packages + Maven Central Snapshots"
        echo "  • XDK distribution → GitHub Releases (xdk-snapshots)"
        echo "  • Docker images → GitHub Container Registry"
        echo "  • Homebrew formula → Updated"
        exit 0
    else
        echo -e "${RED}❌ Some publishing workflows failed:${NC}"
        for workflow in "${FAILED_WORKFLOWS[@]}"; do
            echo "  • $workflow"
        done
        echo ""
        echo -e "${YELLOW}📋 View logs: https://github.com/$(gh repo view --json nameWithOwner -q .nameWithOwner)/actions${NC}"
        exit 1
    fi
elif [ "$STATUS" = "failure" ]; then
    echo -e "${RED}❌ Workflow failed${NC}"
    echo ""
    echo -e "${YELLOW}📋 View logs:${NC} gh run view $RUN_ID --log"
    exit 1
elif [ "$STATUS" = "cancelled" ]; then
    echo -e "${YELLOW}⚠️  Workflow was cancelled${NC}"
    exit 1
else
    echo -e "${YELLOW}⚠️  Workflow completed with status: ${STATUS}${NC}"
    exit 1
fi
