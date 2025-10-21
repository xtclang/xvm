#!/bin/bash
BRANCH=$(git branch --show-current)
REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
open "https://github.com/$REPO/compare/master...$BRANCH"
