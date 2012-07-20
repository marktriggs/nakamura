require 'rubygems'
require 'bundler'
Bundler.setup(:acceptance_tests)
require 'nakamura'
require 'nakamura/users'
require 'nakamura/file'
require 'capybara/cucumber'