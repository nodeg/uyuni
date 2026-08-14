# Copyright (c) 2026 SUSE LLC
# Licensed under the terms of the MIT license.

@migration
@requires_server
Feature: Migrate MLM 50 proxy to 51 on SL Micro 55

  Scenario: Clean up sumaform leftovers on proxy
    When I perform a full salt minion cleanup on "proxy"

  Scenario: Log in as admin user
    Given I am authorized for the "Admin" section

  Scenario: Bootstrap the proxy host as a salt minion
    When I follow the left menu "Systems > Bootstrapping"
    Then I should see a "Bootstrap Minions" text
    When I enter the hostname of "proxy" as "hostname"
    And I enter "22" as "port"
    And I enter "root" as "user"
    And I enter "linux" as "password"
    And I select "1-PROXY-KEY-x86_64" from "activationKeys"
    And I click on "Bootstrap"
    And I wait until I see "Bootstrap process initiated." text

  Scenario: Wait until the proxy host appears
    When I wait until onboarding is completed for "proxy"

  Scenario: Deploy MLM 50 proxy using podman
    When I generate the configuration "/tmp/proxy_container_config.tar.gz" of containerized proxy on the server
    And I copy the configuration "/tmp/proxy_container_config.tar.gz" of containerized proxy from the server to the proxy
    And I run "mgrpxy install podman /tmp/proxy_container_config.tar.gz" on "proxy"

  Scenario: Wait until proxy is running
    When I wait until all proxy services are active

  Scenario: Validate proxy deployment
    When I validate the proxy deployment

  Scenario: Migrate server from 50 to 51
    When I migrate the server from 50 to 51 on Micro
    Then the migration should complete successfully

  Scenario: Refresh product list
    When I follow the left menu "Admin > Setup Wizard > Products"
    And I click on "Refresh"
    And I wait until I do not see "Loading" text

  Scenario: Migrate proxy OS and extension via Web UI
    Given I am on the Systems overview page of this "proxy"
    When I follow "Software" in the content area
    And I follow "Product Migration" in the content area
    And I wait until I see "Target Products:" text, refreshing the page
    And I wait until I see "SL Micro 61" text
    And I click on "Select Channels"
    And I click on "Schedule Migration"
    Then I should see a "Product Migration - Confirm" text
    When I click on "Confirm"
    Then I should see a "This system is scheduled to be migrated to" text
    And I wait at most 600 seconds until event "Product Migration" is completed

  Scenario: Complete proxy migration on host
    When I complete the proxy migration from 50 to 51 on Micro
    Then the proxy migration should complete successfully

  Scenario: Validate proxy migration
    When I validate the proxy migration
    Then the proxy should be running version 51
    And all proxy containers should be running
