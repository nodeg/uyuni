#!/bin/bash
# Copyright (c) 2026 SUSE LLC
# Licensed under the terms of the MIT license.
#
# Complete proxy migration on SLES after Web UI product migration
# This runs AFTER the Web UI product migration completes
# Based on: https://documentation.suse.com/multi-linux-manager/5.1/en/docs/installation-and-upgrade/container-deployment/mlm/migrations/proxy/proxy-mlm-50-51.html

set -euo pipefail

HOSTNAME="${1:?Missing hostname}"
REGISTRY_HOST="${REGISTRY_HOST:-}"

reboot_and_stop() {
    echo "Rebooting and stopping proxy..."
    ssh root@"${HOSTNAME}" "reboot" || true
    sleep 60
    ssh root@"${HOSTNAME}" "mgrpxy stop"
}

verify_upgrade() {
    echo "Verifying OS upgrade..."
    ssh root@"${HOSTNAME}" "cat /etc/os-release | grep 'VERSION=\"15-SP7\"'"
    ssh root@"${HOSTNAME}" "SUSEConnect --status-text | grep 'SUSE Multi-Linux Manager Proxy Extension for SLE 5.1'"
    ssh root@"${HOSTNAME}" "mgrpxy --version | grep '5.1'"
}

install_new_images() {
    echo "Installing new proxy images..."
    ssh root@"${HOSTNAME}" "zypper install -y suse-multi-linux-manager-5.1-x86_64-proxy*"
}

upgrade_containers() {
    echo "Upgrading containers..."
    local upgrade_cmd="mgrpxy upgrade podman"
    if [[ -n "${REGISTRY_HOST}" ]]; then
        upgrade_cmd="${upgrade_cmd} --registry-host ${REGISTRY_HOST}"
    fi

    ssh root@"${HOSTNAME}" "mgrpxy start && ${upgrade_cmd}"
}

verify_containers() {
    echo "Verifying containers..."
    for container in httpd salt-broker squid ssh tftpd; do
        ssh root@"${HOSTNAME}" "podman ps | grep proxy-${container}"
    done
}

main() {
    echo "==> Completing proxy migration on SLES"

    reboot_and_stop
    verify_upgrade
    install_new_images
    upgrade_containers
    verify_containers

    echo "SUCCESS: Proxy containers upgraded to MLM 5.1"
}

main "$@"
