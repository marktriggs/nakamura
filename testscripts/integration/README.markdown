# Integration testing with cucumber

See more information at [the cucumber website](http://cukes.info).

Cucumber is a testing tool with native implementations in both ruby and java, which allows us to express a test in plain English (or other human languages!) like this:

    Feature: Me Service

    Scenario: I want my me
      Given I have a user called "zach"
      And I have logged in as "zach"
      When I request the Me feed
      Then I get the Me feed

To run these tests, change into the `integration` directory, and do:

    bundle install
    bundle exec cucumber

The install part should only be necessary once. If all the gems are in place, you will then be able to run the tests. Nakamura must be running for the tests to work. By default, it assumes you're testing at `http://localhost:8080`.

## Philosophy

The idea behind specification-based testing is that the test scenarios should be written in terms of _the value to the end user_. In other words, you don't write about clicking buttons and filling forms. These are implementation details, and can be safely abstracted out of the specification. Another way to put this is that the tests must be written _in the language of the domain_. For OAE, that means we're testing Contacts, Groups, Content, Messages, etc.