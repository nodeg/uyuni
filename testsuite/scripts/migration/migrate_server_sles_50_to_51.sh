#!/bin/bash
# Copyright (c) 2026 SUSE LLC
# Licensed under the terms of the MIT license.
#
# Migrate MLM 5.0 server to 5.1 on SLES 15 SP6 → SP7
# Based on: https://documentation.suse.com/multi-linux-manager/5.1/en/docs/installation-and-upgrade/container-deployment/mlm/migrations/server/server-mlm-50-51.html

set -euo pipefail

HOSTNAME="${1:?Missing hostname}"
REGISTRY_HOST="${REGISTRY_HOST:-}"

verify_status() {
    echo "Verifying current status..."
    ssh root@"${HOSTNAME}" "SUSEConnect --status-text"
}

apply_patches() {
    echo "Applying patches..."
    ssh root@"${HOSTNAME}" "zypper patch -y"
}

stop_and_reboot() {
    echo "Stopping server and rebooting..."
    ssh root@"${HOSTNAME}" "mgradm stop && reboot" || true
    sleep 60
}

migrate_os() {
    echo "Migrating OS to SLES 15 SP7..."
    ssh root@"${HOSTNAME}" "zypper migration --auto-agree-with-licenses --gpg-auto-import-keys"
}

verify_os_upgrade() {
    echo "Verifying OS upgrade..."
    ssh root@"${HOSTNAME}" "cat /etc/os-release | grep 'VERSION=\"15-SP7\"'"
    ssh root@"${HOSTNAME}" "SUSEConnect --status-text | grep 'SUSE Multi-Linux Manager Server 5.1'"
    ssh root@"${HOSTNAME}" "mgradm --version | grep '5.1'"
}

upgrade_containers() {
    echo "Upgrading containers..."
    local upgrade_cmd="mgradm upgrade podman"
    if [[ -n "${REGISTRY_HOST}" ]]; then
        upgrade_cmd="${upgrade_cmd} --registry-host ${REGISTRY_HOST}"
    fi

    ssh root@"${HOSTNAME}" "mgradm start && ${upgrade_cmd}"
}

verify_containers() {
    echo "Verifying containers..."
    ssh root@"${HOSTNAME}" "podman ps | grep 'server:5.1'"
    ssh root@"${HOSTNAME}" "podman ps | grep 'server-postgresql'"
}

main() {
    echo "==> Migrating MLM 5.0 server to 5.1 on SLES"

    verify_status
    apply_patches
    stop_and_reboot
    migrate_os
    stop_and_reboot
    verify_os_upgrade
    upgrade_containers
    verify_containers

    echo "SUCCESS: Server migrated to MLM 5.1"
}

main "$@"
