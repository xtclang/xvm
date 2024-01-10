#!/bin/sh

# Helper script that prints the privileges associated with a GitHub token.
# This can be useful if you want to expose a token to a non-private group, so that
# it can be programmatically or manually double checked whether it only had the
# intended privileges. For example: no write privileges, and read:package only.

token=$1
if [ -z $token ]; then
    echo "Missing argument: GitHub token to inspect."
else
    curl -sS -f -I -H "Authorization: token $token" https://api.github.com | grep -i x-oauth-scopes
fi
