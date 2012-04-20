#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura/test'
include SlingUsers

class TC_Kern1025Test < Test::Unit::TestCase
  include SlingTest

  def test_me_servlet_from_group_manager
    m = uniqueness()
    manager = create_user("user-manager-#{m}")
    group = Group.new("g-test-#{m}")
    @s.switch_user(User.admin_user())
    res = @s.execute_post(@s.url_for("#{$GROUP_URI}"), {
      ":name" => group.name,
      ":sakai:manager" => manager.name,
      "_charset_" => "UTF-8"
    })
    assert_equal("200", res.code, "Should have created group as admin")
    @s.switch_user(manager)
    res = @s.execute_get(@s.url_for("/system/me.json"))
    assert_equal("200", res.code, "Me servlet should return successfully")
  end

end
