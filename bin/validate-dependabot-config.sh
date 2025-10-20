#!/bin/bash
# Validate dependabot.yml configuration locally
# Checks that team references are valid and have access to the repository

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🔍 Validating Dependabot Configuration${NC}"
echo ""

DEPENDABOT_FILE=".github/dependabot.yml"

if [ ! -f "$DEPENDABOT_FILE" ]; then
    echo -e "${RED}❌ File not found: $DEPENDABOT_FILE${NC}"
    exit 1
fi

echo -e "${GREEN}✅ File exists: $DEPENDABOT_FILE${NC}"
echo ""

# Extract team references from dependabot.yml
echo -e "${BLUE}Extracting team references...${NC}"
TEAMS=$(grep -E '^\s+- ".*"' "$DEPENDABOT_FILE" | sed 's/.*"\(.*\)".*/\1/' | sort -u)

if [ -z "$TEAMS" ]; then
    echo -e "${YELLOW}⚠️  No team references found in $DEPENDABOT_FILE${NC}"
    exit 0
fi

echo "Found team references:"
echo "$TEAMS"
echo ""

ALL_VALID=true

for TEAM_REF in $TEAMS; do
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo -e "${BLUE}Validating: $TEAM_REF${NC}"
    echo ""

    # Parse org/team format
    if [[ $TEAM_REF == *"/"* ]]; then
        ORG=$(echo "$TEAM_REF" | cut -d'/' -f1)
        TEAM_SLUG=$(echo "$TEAM_REF" | cut -d'/' -f2)

        echo "  Organization: $ORG"
        echo "  Team slug: $TEAM_SLUG"

        # Check if team exists
        if gh api "orgs/$ORG/teams/$TEAM_SLUG" > /dev/null 2>&1; then
            echo -e "  ${GREEN}✅ Team exists${NC}"

            # Get team details
            TEAM_NAME=$(gh api "orgs/$ORG/teams/$TEAM_SLUG" --jq '.name')
            TEAM_PRIVACY=$(gh api "orgs/$ORG/teams/$TEAM_SLUG" --jq '.privacy')
            echo "  Team name: $TEAM_NAME"
            echo "  Privacy: $TEAM_PRIVACY"

            # Check team members
            MEMBER_COUNT=$(gh api "orgs/$ORG/teams/$TEAM_SLUG/members" --jq 'length')
            echo "  Members: $MEMBER_COUNT"

            if [ "$MEMBER_COUNT" -eq 0 ]; then
                echo -e "  ${YELLOW}⚠️  WARNING: Team has no members!${NC}"
                ALL_VALID=false
            else
                echo "  Team members:"
                gh api "orgs/$ORG/teams/$TEAM_SLUG/members" --jq '.[] | "    - " + .login'
            fi

            # Check if team has access to this repo
            REPO_OWNER=$(gh repo view --json owner --jq '.owner.login')
            REPO_NAME=$(gh repo view --json name --jq '.name')

            if gh api "orgs/$ORG/teams/$TEAM_SLUG/repos/$REPO_OWNER/$REPO_NAME" > /dev/null 2>&1; then
                PERMISSION=$(gh api "orgs/$ORG/teams/$TEAM_SLUG/repos/$REPO_OWNER/$REPO_NAME" --jq '.permissions | to_entries | map(select(.value == true)) | .[0].key')
                echo -e "  ${GREEN}✅ Team has access to this repository${NC}"
                echo "     Permission: $PERMISSION"
            else
                echo -e "  ${RED}❌ ERROR: Team does NOT have access to this repository!${NC}"
                echo -e "  ${RED}This will cause Dependabot to fail!${NC}"
                ALL_VALID=false
            fi

        else
            echo -e "  ${RED}❌ ERROR: Team does not exist!${NC}"
            echo -e "  ${RED}Dependabot will fail with this configuration!${NC}"
            ALL_VALID=false
        fi
    else
        echo -e "  ${RED}❌ ERROR: Invalid team format '$TEAM_REF'${NC}"
        echo "  Team references must be in format 'org/team-slug'"
        echo "  Example: 'xtclang/maintainers'"
        ALL_VALID=false
    fi
    echo ""
done

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if [ "$ALL_VALID" = true ]; then
    echo -e "${GREEN}✅ All team references are valid!${NC}"
    echo ""
    echo -e "${BLUE}ℹ️  Note about Dependabot behavior:${NC}"
    echo "   When you specify 'xtclang/maintainers', Dependabot will:"
    echo "   1. Request review from the team (visible in PR sidebar)"
    echo "   2. Assign individual team members to the PR"
    echo "   3. Send notifications to all team members"
    exit 0
else
    echo -e "${RED}❌ Some team references are invalid!${NC}"
    echo "   Please fix the issues above before Dependabot PRs will work correctly."
    exit 1
fi