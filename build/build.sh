#!/usr/bin/env bash
set -e

SBT_CMD="sbt +validate"

if [[ "${TRAVIS_PULL_REQUEST}" == "false" ]]; then
    SBT_CMD+=" +coverageOff +publish"
    openssl aes-256-cbc -pass env:ENCRYPTION_PASSWORD -in ./build/secring.gpg.enc -out local.secring.gpg -d
    openssl aes-256-cbc -pass env:ENCRYPTION_PASSWORD -in ./build/pubring.gpg.enc -out local.pubring.gpg -d
    openssl aes-256-cbc -pass env:ENCRYPTION_PASSWORD -in ./build/credentials.sbt.enc -out local.credentials.sbt -d
    openssl aes-256-cbc -pass env:ENCRYPTION_PASSWORD -in ./build/deploy_key.pem.enc -out local.deploy_key.pem -d

    if [[ "${TRAVIS_BRANCH}" == "master" && $(cat version.sbt) != *"SNAPSHOT"* ]]; then
        eval "$(ssh-agent -s)"
        chmod 600 local.deploy_key.pem
        ssh-add local.deploy_key.pem
        git config --global user.name "Finch CI"
        git config --global user.email "ci@kostyukov.net"
        git remote set-url origin git@github.com:finagle/finch.git
        git checkout master || git checkout -b master
        git reset --hard origin/master

        echo 'Performing a release'
        sbt 'release cross with-defaults'
    elif [[ "${TRAVIS_BRANCH}" == "master" ]]; then
        echo 'Master build'
        ${SBT_CMD}
    else
        echo 'Branch build'
        printf 'version in ThisBuild := "%s-SNAPSHOT"' "${TRAVIS_BRANCH}" > version.sbt
        ${SBT_CMD}
    fi
else
    echo 'PR build'
    ${SBT_CMD}
fi
