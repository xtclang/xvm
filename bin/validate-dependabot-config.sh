#!/bin/bash
# Validate .github/dependabot.yml against the published Dependabot configuration schema
# (SchemaStore's vendor.dependabot, bundled with check-jsonschema). Run it locally or through
# the "Validate Dependabot Configuration" workflow, which calls this script.
#
# GitHub removed the dependabot.yml `reviewers` option on 2025-08-08 (pull request reviewers
# come from CODEOWNERS), so there are no team references left to check.

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

CHECK_JSONSCHEMA_VERSION="0.38.2"
DEPENDABOT_FILE=".github/dependabot.yml"

cd "$(git rev-parse --show-toplevel)"

if [ ! -f "$DEPENDABOT_FILE" ]; then
    echo -e "${RED}❌ File not found: $DEPENDABOT_FILE${NC}"
    exit 1
fi

if command -v uvx >/dev/null 2>&1; then
    CHECK_JSONSCHEMA=(uvx --quiet "check-jsonschema==$CHECK_JSONSCHEMA_VERSION")
elif command -v pipx >/dev/null 2>&1; then
    CHECK_JSONSCHEMA=(pipx run --spec "check-jsonschema==$CHECK_JSONSCHEMA_VERSION" check-jsonschema)
else
    echo -e "${RED}❌ uvx or pipx is required to run check-jsonschema${NC}"
    exit 1
fi

echo -e "${BLUE}🔍 Validating $DEPENDABOT_FILE against the Dependabot schema (check-jsonschema $CHECK_JSONSCHEMA_VERSION)${NC}"
"${CHECK_JSONSCHEMA[@]}" --builtin-schema vendor.dependabot "$DEPENDABOT_FILE"
echo -e "${GREEN}✅ $DEPENDABOT_FILE is a valid Dependabot configuration${NC}"
