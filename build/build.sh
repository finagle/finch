#!/usr/bin/env bash
set -e

SBT_CMD="sbt +validate"

if [[ "${GITHUB_EVENT_NAME}" == "push" ]]; then
    SBT_CMD+=" +coverageOff +publish"

    for FILE in \
        secring.gpg \
        pubring.gpg \
        credentials.sbt \
        deploy_key.pem \
        ; do
        openssl aes-256-cbc -salt -pbkdf2 -d \
            -in "./build/${FILE}.enc" \
            -out "local.${FILE}" \
            -pass env:ENCRYPTION_PASSWORD
    done

    # exit early to test openssl bits
    exit 0

    if [[ "${GITHUB_REF_NAME}" == "master" && $(cat version.sbt) != *"SNAPSHOT"* ]]; then
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
    elif [[ "${GITHUB_REF_NAME}" == "master" ]]; then
        echo 'Master build'
        ${SBT_CMD}
    else
        echo 'Branch build'
        printf 'version in ThisBuild := "%s-SNAPSHOT"' "${GITHUB_REF_NAME}" > version.sbt
        ${SBT_CMD}
    fi
else
    echo "${GITHUB_EVENT_NAME} build"
    ${SBT_CMD}
fi
