#!/bin/bash

function list_pubs() {  
    curl -L -H "Accept: application/vnd.github+json"  -H "Authorization: Bearer $GITHUB_TOKEN" -H "X-GitHub-Api-Version: 2022-11-28" https://api.github.com/orgs/xtclang/packages?package_type=maven
}

list_pubs

