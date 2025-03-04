# Copyright (c) 2023 SUSE LLC
# Licensed under the terms of the MIT license.

Feature: Very first settings
  In order to use the product
  As the admin user
  I want to create the organisation, the first users and set the HTTP proxy

  Scenario: Create admin user and first organization
    Given I access the host the first time
    And I run "rm -Rf /srv/salt/*" on "server"
    When I go to the home page
    And I enter "SUSE Test" as "orgName"
    And I enter "admin" as "login"
    And I enter "admin" as "desiredpassword"
    And I enter "admin" as "desiredpasswordConfirm"
    And I select "Mr." from "prefix"
    And I enter "Admin" as "firstNames"
    And I enter "Admin" as "lastName"
    And I enter "galaxy-noise@localhost" as "email"
    And I click on "Create Organization"
    Then I am logged in

  Scenario: Log in as admin user
    Given I am authorized for the "Admin" section

  Scenario: Create testing username
    When I follow the left menu "Users > User List > Active"
    And I follow "Create User"
    And I enter "testing" as "login"
    And I enter "testing" as "desiredpassword"
    And I enter "testing" as "desiredpasswordConfirm"
    And I select "Mr." from "prefix"
    And I enter "Test" as "firstNames"
    And I enter "User" as "lastName"
    And I enter "galaxy-noise@localhost" as "email"
    And I click on "Create Login"
    Then I should see a "Account testing created, login information sent to galaxy-noise@localhost" text
    And I should see a "testing" link

  Scenario: Grant testing user administrative priviledges
    When I follow the left menu "Users > User List > Active"
    And I follow "testing"
    And I check "role_org_admin"
    And I check "role_system_group_admin"
    And I check "role_channel_admin"
    And I check "role_activation_key_admin"
    And I check "role_config_admin"
    And I click on "Update"
    Then I should see a "User information updated" text
    And I should see a "testing" text
