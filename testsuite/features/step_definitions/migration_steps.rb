# Copyright (c) 2026 SUSE LLC
# Licensed under the terms of the MIT license.

### Migration test steps

# Server deployment and migration
When(/^I deploy MLM 50 server on (SLES|Micro) using podman$/) do |os_type|
  os = os_type.downcase
  script = "testsuite/scripts/migration/deploy_mlm_50_server_#{os}.sh"
  node = get_target('server')

  result, code = node.run("bash #{script} #{node.hostname}", check_errors: false)
  raise ScriptError, "Server deployment failed: #{result}" unless code.zero?
end

When(/^I migrate the server from 50 to 51 on (SLES|Micro)$/) do |os_type|
  os = os_type.downcase
  script = "testsuite/scripts/migration/migrate_server_#{os}_50_to_51.sh"
  node = get_target('server')

  result, code = node.run("bash #{script} #{node.hostname}", check_errors: false)
  raise ScriptError, "Server migration failed: #{result}" unless code.zero?
end

# Proxy post-migration (after Web UI product migration)
When(/^I complete the proxy migration from 50 to 51 on (SLES|Micro)$/) do |os_type|
  os = os_type.downcase
  script = "testsuite/scripts/migration/migrate_proxy_#{os}_50_to_51.sh"
  node = get_target('proxy')

  result, code = node.run("bash #{script} #{node.hostname}", check_errors: false)
  raise ScriptError, "Proxy migration failed: #{result}" unless code.zero?
end

# Validation
When(/^I validate the (server|proxy) deployment$/) do |component|
  script = "testsuite/scripts/migration/validate_deployment.sh"
  node = get_target(component)

  result, code = node.run("bash #{script} #{node.hostname} #{component}", check_errors: false)
  raise ScriptError, "Deployment validation failed: #{result}" unless code.zero?
end

When(/^I validate the (server|proxy) migration$/) do |component|
  script = "testsuite/scripts/migration/validate_migration.sh"
  node = get_target(component)

  result, code = node.run("bash #{script} #{node.hostname} #{component}", check_errors: false)
  raise ScriptError, "Migration validation failed: #{result}" unless code.zero?
end

# Assertions
Then(/^the (server|proxy) deployment should be successful$/) do |_component|
  # Verified by script exit code
end

Then(/^the migration should complete successfully$/) do
  # Verified by script exit code
end

Then(/^the (server|proxy) migration should complete successfully$/) do |_component|
  # Verified by script exit code
end

Then(/^the (server|proxy) should be running version 51$/) do |component|
  node = get_target(component)
  cmd = component == 'server' ? 'mgradm' : 'mgrpxy'

  result, code = node.run("#{cmd} --version | grep '5.1'", check_errors: false)
  raise ScriptError, 'Version check failed' unless code.zero?
end

Then(/^the database schema should be version 51$/) do
  node = get_target('server')

  result, code = node.run("podman exec $(podman ps -q -f name=server) rpm -q susemanager-schema | grep '5.1'", check_errors: false)
  raise ScriptError, 'Schema version check failed' unless code.zero?
end

Then(/^all proxy containers should be running$/) do
  node = get_target('proxy')

  %w[proxy-httpd proxy-salt-broker proxy-squid proxy-ssh proxy-tftpd].each do |container|
    result, code = node.run("podman ps | grep #{container}", check_errors: false)
    raise ScriptError, "Container #{container} not running" unless code.zero?
  end
end

When(/^I wait until all proxy services are active$/) do
  node = get_target('proxy')

  %w[uyuni-proxy-pod uyuni-proxy-httpd uyuni-proxy-salt-broker uyuni-proxy-squid uyuni-proxy-ssh uyuni-proxy-tftpd].each do |service|
    repeat_until_timeout(timeout: 300, message: "Service #{service} not active") do
      result, code = node.run("systemctl is-active #{service}", check_errors: false)
      break if code.zero?

      sleep 5
    end
  end
end
