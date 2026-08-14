#!/bin/bash
# Copyright (c) 2026 SUSE LLC
# Licensed under the terms of the MIT license.
#
# Deploy MLM 5.0 server on SLES 15 SP6 using podman

set -euo pipefail

HOSTNAME="${1:?Missing hostname}"
SCC_USER="${SCC_USER:?Missing SCC username}"
SCC_PASS="${SCC_PASS:?Missing SCC password}"

install_server() {
    echo "Installing MLM 5.0 server..."
    ssh root@"${HOSTNAME}" "mgradm install podman --mirror SCC --scc-user '${SCC_USER}' --scc-password '${SCC_PASS}'"
}

verify_deployment() {
    echo "Verifying deployment..."
    ssh root@"${HOSTNAME}" "mgradm status | grep -q running"
    ssh root@"${HOSTNAME}" "podman ps | grep -q 'server:5.0'"
}

main() {
    echo "==> Deploying MLM 5.0 server on SLES 15 SP6"

    install_server
    verify_deployment

    echo "SUCCESS: MLM 5.0 server deployed"
}

main "$@"
