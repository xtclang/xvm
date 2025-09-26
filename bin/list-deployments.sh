#!/bin/bash
#
# List Maven Central deployments using the Sonatype Central API
#

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Get credentials from gradle.properties
GRADLE_PROPS="$HOME/.gradle/gradle.properties"

if [ ! -f "$GRADLE_PROPS" ]; then
    echo -e "${RED}Error: Cannot find $GRADLE_PROPS${NC}"
    echo "Please ensure your Maven Central credentials are configured."
    exit 1
fi

# Extract credentials
USERNAME=$(grep "^mavenCentralUsername=" "$GRADLE_PROPS" 2>/dev/null | cut -d'=' -f2)
PASSWORD=$(grep "^mavenCentralPassword=" "$GRADLE_PROPS" 2>/dev/null | cut -d'=' -f2)

if [ -z "$USERNAME" ] || [ -z "$PASSWORD" ]; then
    echo -e "${RED}Error: Maven Central credentials not found in $GRADLE_PROPS${NC}"
    echo "Please ensure mavenCentralUsername and mavenCentralPassword are set."
    exit 1
fi

echo -e "${BLUE}Fetching deployments from Maven Central...${NC}"
echo

# The new Central Portal uses Bearer token authentication
# The password is actually the token for the new API
RESPONSE=$(curl -s -H "Authorization: Bearer $PASSWORD" \
    "https://central.sonatype.com/api/v1/publisher/deployments" 2>/dev/null)

# Check if we got an HTML response (indicates wrong API or auth issue)
if echo "$RESPONSE" | grep -q "<!DOCTYPE html"; then
    echo -e "${YELLOW}Note: The API returned HTML instead of JSON.${NC}"
    echo -e "${YELLOW}This usually means you need to check deployments manually at:${NC}"
    echo -e "${GREEN}https://central.sonatype.com${NC}"
    echo
    echo "Login with:"
    echo "  Username: $USERNAME"
    echo "  Password: [your-token]"
    exit 0
fi

# Check if we have jq installed for JSON parsing
if ! command -v jq &> /dev/null; then
    echo -e "${YELLOW}Warning: 'jq' is not installed. Showing raw response:${NC}"
    echo "$RESPONSE"
    echo
    echo -e "${YELLOW}Install jq for better formatting: brew install jq${NC}"
    exit 0
fi

# Parse and display deployments
DEPLOYMENT_COUNT=$(echo "$RESPONSE" | jq '.deployments | length' 2>/dev/null || echo "0")

if [ "$DEPLOYMENT_COUNT" = "0" ] || [ "$DEPLOYMENT_COUNT" = "null" ]; then
    echo -e "${GREEN}No active deployments found.${NC}"
    echo
    echo "This means either:"
    echo "  1. You haven't published anything to staging yet"
    echo "  2. All staged deployments have been released or dropped"
    echo "  3. The API format has changed"
    echo
    echo -e "${YELLOW}To create a new deployment:${NC}"
    echo "  ./gradlew publishToMavenCentral -Pversion=X.X.X"
else
    echo -e "${GREEN}Found $DEPLOYMENT_COUNT deployment(s):${NC}"
    echo

    # Parse each deployment
    echo "$RESPONSE" | jq -r '.deployments[] |
        "Deployment ID: \(.deploymentId // "unknown")
        Name: \(.name // "unknown")
        State: \(.deploymentState // "unknown")
        Created: \(.createdAt // "unknown")
        ----------------------------------------"'
fi

echo
echo -e "${BLUE}To drop a deployment, use:${NC}"
echo "  ./gradlew dropMavenCentralDeployment -PdeploymentId=<deployment-id>"
echo
echo -e "${BLUE}To view in web portal:${NC}"
echo "  https://central.sonatype.com"