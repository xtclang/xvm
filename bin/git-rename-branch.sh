#!/bin/bash

resolve_branch() {
  local _branch
  if ! _branch=$(git branch --show-current); then
    echo "ERROR: Failed to resolve current branch."
    exit 1
  fi
  echo "$_branch"
  return 0
}

rename_branch() {
  branch=$1
  branch_new=$2
  echo "Renaming $branch to $branch_new"
  echo git branch -m "$branch" "$branch_new"
  git branch -m "$branch" "$branch_new"
  echo git push origin "$branch_new"
  git push origin "$branch_new"
  echo git push origin --delete "$branch"
  git push origin --delete "$branch"  
  echo git push --set-upstream origin "$branch_new"
  git push --set-upstream origin "$branch_new" 
}

nargs=$#
if [ "$nargs" -eq 1 ]; then
    branch="$(resolve_branch)"
    branch_new="$1"
    echo "WARNING: No original branch name was given, will assume current branch."
elif [ "$nargs" -eq 2 ]; then
    branch="$1"
    branch_new="$2"
    echo "Two branch names given: $branch and $branch_new"
else
    echo "ERROR: Invalid number of arguments. Usage: git-rename-branch.sh [branch] <new_branch_name>"
    exit 1
fi

echo "HELLO $branch $branch_new"
if [ -z "$branch" ] || [ -z "$branch_new" ]; then
    echo "ERROR: Branch name and new branch name cannot be resolved."
    exit 1
fi

# Function to prompt for y/n input with default 'n'
ask_yn() {
    local prompt="$1"
    local response
    read -r -p "$prompt [y/N]: " response
    case "$response" in
        [yY][eE][sS]|[yY])
            return 0  # User input is 'yes'
            ;;
        *)
            return 1  # Default or user input is 'no'
            ;;
    esac
}


branch_current=$(resolve_branch)
if [ "$branch_current" != "$branch" ]; then
  if ! git checkout "$branch"; then
    echo "ERROR: Tried to check out branch $branch, but failed. Make sure to stash or commit any changes in $branch_current."
    exit 1
  fi
  branch_current="$branch"
fi

if ask_yn "Are you sure you want to rename branch: $branch -> $branch_new"; then
    rename_branch "$branch" "$branch_new"
fi

echo "Finished."
echo "Current branch is: $branch_current"
