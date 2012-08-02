#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura/test'
require 'nakamura/file'
require 'nakamura/users'
require 'test/unit.rb'
include SlingUsers
include SlingFile

##
# Verify that direct associations to content are found when searching by role.
##
class TC_FindByRoleAll < Test::Unit::TestCase
  include SlingTest

  def setup
    super
    @fm = FileManager.new(@s)
    @um = UserManager.new(@s)
  end

  def test_search_me
    m = uniqueness()

    # Create some users
    owner = create_user("creator2-#{m}")
    viewer = create_user("manager2-#{m}")
    groupuser = create_user("groupuser2-#{m}")

    @s.switch_user(owner)
    content = uniqueness()
    name = "random-#{content}.txt"
    fileBody = "Add the time to make it sort of random #{Time.now.to_f}."
    res = @fm.upload_pooled_file(name, fileBody, 'text/plain')
    json = JSON.parse(res.body)
    id = json[name]['poolId']

	add_viewers_res = @fm.manage_members(id, [viewer.name], nil, nil, nil) 

    assert_equal("200",add_viewers_res.code, "manage_members add viewers POST should have succeeded")
    members_res = @fm.get_members id
    json = JSON.parse(members_res.body)
    assert_equal("200",members_res.code, "get_members GET should have succeeded")
    viewers = json["viewers"]
    assert_equal(1, viewers.length, "should be 1 viewers added")

    sleep(5)

    # Search the files that I manage .. should be 1
    res = @s.execute_get(@s.url_for("/var/search/pool/me/role.tidy.json?role=manager"))
    assert_equal("200",res.code,res.body)
    json = JSON.parse(res.body)
    assert_equal(1, json["results"].length)
    assert_equal(fileBody.length, json["results"][0]["_length"])

	@s.switch_user(viewer)

	# Search the file by viewer
	res = @s.execute_get(@s.url_for("/var/search/pool/me/role.tidy.json?role=viewer"))
    assert_equal("200",res.code,res.body)
    json = JSON.parse(res.body)
    assert_equal(1, json["results"].length)
    assert_equal(fileBody.length, json["results"][0]["_length"])
  end

end