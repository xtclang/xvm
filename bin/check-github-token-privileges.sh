#!/bin/sh

# Helper script that prints the privileges associated with a GitHub token.
# This can be useful if you want to expose a token to a non-private group, so that
# it can be programmatically or manually double checked whether it only had the
# intended privileges. For example: no write privileges, and read:package only.

token="$1"
if [ -z "$token" ]; then
    echo "Missing argument: GitHub token to inspect, e.g. '$0 \$GITHUB_TOKEN'"
    exit 1
fi

output=$(curl -sS -f -I -H "Authorization: token $token" https://api.github.com)
if [ $? != 0 ]; then
  echo "ERROR: curl failed to resolve token privileges for the given token. Is it valid?"
  exit 1
fi

oauth_scopes=$(echo "$output" | grep -i x-oauth-scopes | grep -v ^access-control-expose-headers)
if [ -z "$oauth_scopes" ]; then
  echo "ERROR: Failed to resolve token privileges from curl response."
  exit 1
fi

echo "$oauth_scopes"

