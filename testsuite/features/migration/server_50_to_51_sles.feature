# Copyright (c) 2026 SUSE LLC
# Licensed under the terms of the MIT license.

@migration
Feature: Migrate MLM 50 server to 51 on SLES 15 SP6

  Scenario: Deploy MLM 50 server on SLES 15 SP6
    When I deploy MLM 50 server on SLES using podman
    Then the server deployment should be successful

  Scenario: Validate MLM 50 server deployment
    When I validate the server deployment

  Scenario: Migrate server from MLM 50 to 51
    When I migrate the server from 50 to 51 on SLES
    Then the migration should complete successfully

  Scenario: Validate MLM 51 server migration
    When I validate the server migration
    Then the server should be running version 51
    And the database schema should be version 51

  Scenario: Verify core server functionality
    Given I am authorized for the "Admin" section
    When I follow the left menu "Software > Manage > Channels"
    And I follow "Create Channel"
    And I enter "Test Channel" as "Channel Name"
    And I enter "test_channel" as "Channel Label"
    And I click on "Create Channel"
    Then I should see a "Channel Test Channel created" text
