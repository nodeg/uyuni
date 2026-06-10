# Copyright (c) 2026 SUSE LLC
# Licensed under the terms of the MIT license.
#
# This feature tests the Liberate Formula to convert Oracle Linux 10 to SUSE Liberty Linux

@skip_if_github_validation
@scope_formulas
@oracle10_minion
Feature: Liberate Oracle Linux 10 using the Liberate Formula
  In order to migrate Oracle Linux 10 systems to SUSE Liberty Linux
  As an authorized user
  I want to use the Liberate formula to convert Oracle Linux 10

  Scenario: Log in as org admin user
    Given I am authorized

  Scenario: Create system group for Oracle Linux 10 liberate formula
    When I follow the left menu "Systems > System Groups"
    And I follow "Create Group"
    And I enter "liberate-oracle10-group" as "name"
    And I enter "System group for Oracle Linux 10 liberation testing" as "description"
    And I click on "Create Group"
    Then I should see a "System group liberate-oracle10-group created." text

  Scenario: Enable liberate formula on the Oracle Linux 10 system group
    When I follow the left menu "Systems > System Groups"
    And I follow "liberate-oracle10-group"
    And I follow "Formulas" in the content area
    Then I should see a "Choose formulas:" text
    When I check the "liberate" formula
    And I click on "Save"
    And I wait until I see "Formula saved." text
    Then the "liberate" formula should be checked

  Scenario: Configure liberate formula with full package reinstall for Oracle Linux 10
    When I follow "Formulas" in the content area
    And I follow first "Liberate" in the content area
    Then I should see a "Liberate" text
    When I check "reinstall_all_packages" if not checked
    And I click on "Save Formula"
    Then I should see a "Formula saved" text

  Scenario: Verify Oracle Linux 10 system is registered before liberation
    Given I am on the Systems overview page of this "oracle10_minion"
    Then I should see a "System Status" text
    And I should see a "Oracle Linux" text in the content area

  Scenario: Add Oracle Linux 10 system to liberate group
    When I follow the left menu "Systems > System Groups"
    And I follow "liberate-oracle10-group"
    And I follow "Target Systems"
    And I check the "oracle10_minion" client
    And I click on "Add Systems"
    Then I should see a "1 systems were added to liberate-oracle10-group server group." text

  Scenario: Verify Oracle Linux 10 is part of the liberate group
    Given I am on the Systems overview page of this "oracle10_minion"
    When I follow "Groups" in the content area
    Then I should see a "liberate-oracle10-group" text

  Scenario: Check pillar data for liberate formula on Oracle Linux 10
    When I refresh the pillar data
    Then the pillar data for "formulas" should contain "liberate" on "oracle10_minion"

  Scenario: Apply liberate formula to Oracle Linux 10 via highstate
    Given I am on the Systems overview page of this "oracle10_minion"
    When I follow "States" in the content area
    And I click on "Apply Highstate"
    Then I should see a "Applying the highstate has been scheduled." text
    And I wait at most 600 seconds until event "Apply highstate scheduled" is completed

  Scenario: Verify Oracle Linux 10 has been converted to Liberty Linux
    Given I am on the Systems overview page of this "oracle10_minion"
    When I follow "Details" in the content area
    Then I should see a "SUSE Liberty Linux" text in the content area

  Scenario: Verify packages are reinstalled with SUSE signatures on Oracle Linux 10
    When I run "rpm -qa --qf '%{name}-%{version}-%{release} %{packager}\n' | grep -i suse | head -5" on "oracle10_minion"
    Then the command should succeed

  Scenario: Verify system repositories after liberation on Oracle Linux 10
    When I run "dnf repolist" on "oracle10_minion"
    Then the command should succeed

  Scenario: Verify liberate formula is idempotent on Oracle Linux 10
    Given I am on the Systems overview page of this "oracle10_minion"
    When I follow "States" in the content area
    And I click on "Apply Highstate"
    Then I should see a "Applying the highstate has been scheduled." text
    And I wait at most 300 seconds until event "Apply highstate scheduled" is completed
