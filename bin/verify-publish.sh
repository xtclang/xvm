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

echo -e "${BLUE}🚀 Triggering 'Verify Commit' workflow with publish-snapshots=true...${NC}"

# Get the current branch
CURRENT_BRANCH=$(git branch --show-current)
echo -e "${BLUE}Branch: ${CURRENT_BRANCH}${NC}"

# Trigger the workflow
gh workflow run "Verify Commit" \
    --ref "$CURRENT_BRANCH" \
    --field publish-snapshots=true

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
    echo -e "${BLUE}📦 The following publishing workflows should now be triggered:${NC}"
    echo "  • Publish Snapshots"
    echo "  • Build Docker Images"
    echo "  • Update Homebrew"
    echo ""
    echo -e "${BLUE}🔗 Check status: https://github.com/$(gh repo view --json nameWithOwner -q .nameWithOwner)/actions${NC}"
    exit 0
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
