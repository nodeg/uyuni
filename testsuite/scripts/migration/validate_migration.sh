#!/bin/bash
# Copyright (c) 2026 SUSE LLC
# Licensed under the terms of the MIT license.
#
# Validate MLM migration (server or proxy)

set -euo pipefail

HOSTNAME="${1:?Missing hostname}"
COMPONENT="${2:?Missing component (server|proxy)}"

check_version() {
    echo "Checking version..."
    if [[ "${COMPONENT}" == "server" ]]; then
        ssh root@"${HOSTNAME}" "mgradm --version" | grep -q "5.1"
    else
        ssh root@"${HOSTNAME}" "mgrpxy --version" | grep -q "5.1"
    fi
}

check_containers() {
    echo "Checking containers..."

    if [[ "${COMPONENT}" == "server" ]]; then
        ssh root@"${HOSTNAME}" "podman ps" | grep -q "server:5.1"
        ssh root@"${HOSTNAME}" "podman ps" | grep -q "server-postgresql"
    else
        for container in httpd salt-broker squid ssh tftpd; do
            ssh root@"${HOSTNAME}" "podman ps" | grep -q "proxy-${container}"
        done
    fi
}

check_database_schema() {
    [[ "${COMPONENT}" != "server" ]] && return 0

    echo "Checking database schema..."
    ssh root@"${HOSTNAME}" "podman exec \$(podman ps -q -f name=server) rpm -q susemanager-schema" | grep -q "5.1"
}

check_api_functional() {
    [[ "${COMPONENT}" != "server" ]] && return 0

    echo "Checking API functionality..."
    ssh root@"${HOSTNAME}" "curl -sSk https://localhost/rpc/api | grep -q 'XML-RPC server'"
}

check_ui_functional() {
    [[ "${COMPONENT}" != "server" ]] && return 0

    echo "Checking UI functionality..."
    ssh root@"${HOSTNAME}" "curl -sSk https://localhost | grep -q 'SUSE'"
}

check_admin_user_exists() {
    [[ "${COMPONENT}" != "server" ]] && return 0

    echo "Checking admin user preserved..."
    ssh root@"${HOSTNAME}" "podman exec \$(podman ps -q -f name=server) spacewalk-sql -i 'SELECT login FROM rhnUser WHERE id=1'" | grep -q "admin"
}

main() {
    echo "==> Validating ${COMPONENT} migration on ${HOSTNAME}"

    check_version
    check_containers
    check_database_schema
    check_api_functional
    check_ui_functional
    check_admin_user_exists

    echo "SUCCESS: ${COMPONENT} migration validated"
}

main "$@"
