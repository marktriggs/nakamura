Feature: Me Service

Scenario: I want my me
  Given I have a user called "zach"
  And I have logged in as "zach"
  When I request the Me feed
  Then I get the Me feed

