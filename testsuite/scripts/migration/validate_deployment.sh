#!/bin/bash
# Copyright (c) 2026 SUSE LLC
# Licensed under the terms of the MIT license.
#
# Validate MLM deployment (server or proxy)

set -euo pipefail

HOSTNAME="${1:?Missing hostname}"
COMPONENT="${2:?Missing component (server|proxy)}"

check_containers_running() {
    echo "Checking containers..."
    ssh root@"${HOSTNAME}" "podman ps" | grep -q "${COMPONENT}"
}

check_mgr_tool() {
    echo "Checking mgr tool..."
    if [[ "${COMPONENT}" == "server" ]]; then
        ssh root@"${HOSTNAME}" "mgradm --version"
    else
        ssh root@"${HOSTNAME}" "mgrpxy --version"
    fi
}

check_api_accessible() {
    [[ "${COMPONENT}" != "server" ]] && return 0

    echo "Checking API..."
    ssh root@"${HOSTNAME}" "curl -sSk https://localhost/rpc/api | grep -q 'XML-RPC server'"
}

check_ui_accessible() {
    [[ "${COMPONENT}" != "server" ]] && return 0

    echo "Checking UI..."
    ssh root@"${HOSTNAME}" "curl -sSk https://localhost | grep -q 'SUSE'"
}

main() {
    echo "==> Validating ${COMPONENT} deployment on ${HOSTNAME}"

    check_containers_running
    check_mgr_tool
    check_api_accessible
    check_ui_accessible

    echo "SUCCESS: ${COMPONENT} deployment validated"
}

main "$@"
