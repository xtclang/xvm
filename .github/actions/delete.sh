#!/bin/bash

org="$1"
repo="$2"

# TODO: Package house keeping
#   works: curl -L -H "Accept: application/vnd.github+json"  -H "Authorization: Bearer $GITHUB_TOKEN"  -H "X-GitHub-Api-Version: 2022-11-28"  "https://api.github.com/orgs/xtclang/packages?package_type=maven"
#   works: gh api -H "Accept: application/vnd.github+json" -H "X-GitHub-Api-Version: 2022-11-28" "/orgs/xtclang/packages?package_type=maven"
#   also works:  gh api "/orgs/xtclang/packages?package_type=maven"
#  Package names.
#  gh api "/orgs/xtclang/packages?package_type=maven" | jq -r '.[] | .name'
# README:
#  How to use the snapshots.
#  Get tag, compare with latest tag, if different then add a new tag.
#  Check if packages last version is different from the current version.
#
# SEMVER
# https://github.com/swiftzer/semver/blob/main/.github/workflows/check.yml
#

echo "Deleting workflow runs for $org/$repo"

workflows_temp=$(mktemp) # Creates a temporary file to store workflow data.
gh api repos/$org/$repo/actions/workflows | jq -r '.workflows[] | [.id, .path] | @tsv' > $workflows_temp # Lookup workflow
cat "$workflows_temp"
workflows_names=$(awk '{print $2}' $workflows_temp | grep -v "main" | grep -v "master")
echo $workflows_names

if [ -z "$workflows_names" ]; then
    echo "All workflows are either successful or failed. Nothing to remove"
else
    echo "Removing all the workflows that are not successful or failed"
    for workflow_name in $workflows_names; do
        workflow_filename=$(basename "$workflow_name")
        echo "Deleting |$workflow_filename|, please wait..."
        gh run list --limit 500 --workflow $workflow_filename --json databaseId |
            jq -r '.[] | .databaseId' |
            xargs -I{} gh run delete {} # Delete all workflow runs for workflow name
    done
fi

rm -rf $workflows_temp

echo "Done."
