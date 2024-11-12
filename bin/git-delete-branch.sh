#!/bin/bash

branch=$1
if [ -z "$branch" ]; then
  echo "ERROR: No branch name given."
  exit 1
fi

# Force delete if not merged
if ! git branch -D "$branch"; then
  echo "ERROR: Failed to delete local branch $branch with -D flag."
  exit 1
fi

# Delete remote branch
if ! git push origin --delete "$branch"; then
  echo "ERROR: Failed to delete remote branch $branch"
  exit 1
fi

echo "Branch $branch deleted."
