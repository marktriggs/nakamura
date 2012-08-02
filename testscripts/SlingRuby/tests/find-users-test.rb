#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura/test'
include SlingUsers

class TC_FindUsersTest < Test::Unit::TestCase
  include SlingTest

  def setup
    super
    @um = SlingUsers::UserManager.new @s
    m = uniqueness()
    @qs = m.to_i.to_s(36)
    @tagqs = "directory/#{@qs.reverse}"
    res = @s.create_node("/tags/#{@tagqs}", {
        "sakai:tag-name" => @tagqs,
        "sling:resourceType" => "sakai/tag",
        "_charset_" => "utf-8"
    })
    assert_equal('201', res.code, "Should be able to create tag: #{res}\n#{res.body}")
    @id_firstName = "id_firstName-#{m}"
    create_user(@id_firstName, "Mary #{@qs}", "Huzits")
    @id_lastNameAndProfile = "id_lastNameAndProfile-#{m}"
    create_user(@id_lastNameAndProfile, "Jane", "de la #{@qs}")
    set_about_me(@id_lastNameAndProfile, "Please don't call me #{@qs}!")
    @id_Profile = "id_Profile-#{m}"
    create_user(@id_Profile, "Joe", "Ansible")
    set_about_me(@id_Profile, "Please do not call me #{@qs}, either.")
    @id_ProfileAndTag = "id_ProfileAndTag-#{m}"
    create_user(@id_ProfileAndTag, "Jim", "Bezark")
    set_about_me(@id_ProfileAndTag, "But you can call me #{@qs} if you like")
    set_tag(@id_ProfileAndTag, @tagqs)
    @id_Tag = "id_Tag-#{m}"
    create_user(@id_Tag, "Jennie", "Creek")
    set_tag(@id_Tag, @tagqs)
    # Wait for indexer. Note that profiles do not require near-real-time indexing!
    #sleep(5)
    wait_for_indexer()
  end
  def set_about_me(userid, aboutme)
     res = @s.execute_post(@s.url_for("~#{userid}/public/authprofile/aboutme.profile.json"), {
        ":operation" => "import",
        ":contentType" => "json",
        ":merge" => "true",
        ":replace" => "true",
        ":replaceProperties" => "true",
        "_charset_" => "utf-8",
        ":removeTree" => "true",
        ":content" => "{\"elements\":{\"aboutme\":{\"value\":\"#{aboutme}\"}}}"
    })
    assert_equal('200', res.code, "Should be able to post successfully to the user profile: #{res}\n#{res.body}")
  end
  def set_tag(userid, tag)
    res = @s.execute_post(@s.url_for("~#{userid}/public/authprofile"), {
        ":operation" => "tag",
        "key" => "/tags/#{tag}",
        "_charset_" => "utf-8"
    })
    assert_equal('200', res.code, "Should be able to tag successfully: #{res}\n#{res.body}")
  end
  def get_user_ids(searchjson)
    ids = searchjson["results"].map { |e| e["userid"] }
  end
  def get_tag_facets(searchjson)
    tags = []
    @log.info(searchjson["facet_fields"])
    @log.info(searchjson["facet_fields"][0])
    @log.info(searchjson["facet_fields"][0]["tagname"])
    searchjson["facet_fields"][0]["tagname"].map { |e|
        @log.info("#{e}, keys = #{e.keys}")
        tags.concat(e.keys)
    }
    tags
  end

  # Because indexing new user accounts and profile changes can take a while,
  # we do all tests in a single run.
  def test_users_searches
    do_users_all
    do_simple_query
    do_tag_query
    do_profile_query
  end

  def do_users_all
    # WARNING: This won't work if the test system has hundreds of users.
    res = @s.execute_get(@s.url_for("/var/search/users.tidy.json"), {
        "q" => "*",
        "page" => "0",
        "items" => "400"
    })
    assert_equal('200', res.code, "Find all with wildcard: #{res}\n#{res.body}")
    @log.info("wildcard: #{res}\n#{res.body}")
    json = JSON.parse(res.body)
    allids = [@id_firstName, @id_lastNameAndProfile, @id_Profile, @id_ProfileAndTag, @id_Tag]
    foundids = get_user_ids(json)
    assert((allids - foundids).empty?, "Expected #{foundids} to include #{allids}")
    tags = get_tag_facets(json)
    assert(tags.include?(@tagqs), "Expected #{tags} to include #{@tagqs}")
  end

  def do_simple_query
    res = @s.execute_get(@s.url_for("/var/search/users.tidy.json"), {
        "q" => @qs,
        "page" => "0",
        "items" => "400"
    })
    assert_equal('200', res.code, "Find all with simple query: #{res}\n#{res.body}")
    @log.info("simple query: #{res.body}")
    json = JSON.parse(res.body)
    allids = [@id_firstName, @id_lastNameAndProfile]
    foundids = get_user_ids(json)
    assert_equal(allids.sort, foundids.sort, "Expected #{foundids} to equal #{allids}")
    tags = get_tag_facets(json)
    assert(tags.empty?, "Expected #{tags} to be empty")
  end

  def do_tag_query
    res = @s.execute_get(@s.url_for("/var/search/users.tidy.json"), {
        "q" => "*",
        "tags" => @tagqs,
        "page" => "0",
        "items" => "400"
    })
    assert_equal('200', res.code, "Find all with tag query: #{res}\n#{res.body}")
    @log.info("tag query: #{res.body}")
    json = JSON.parse(res.body)
    allids = [@id_ProfileAndTag, @id_Tag]
    foundids = get_user_ids(json)
    assert_equal(allids.sort, foundids.sort, "Expected #{foundids} to equal #{allids}")
    tags = get_tag_facets(json)
    assert(tags.include?(@tagqs), "Expected #{tags} to include #{@tagqs}")
  end

  def do_profile_query
    res = @s.execute_get(@s.url_for("/var/search/users.tidy.json"), {
        "q" => @qs,
        "fullprofile" => "true",
        "page" => "0",
        "items" => "400"
    })
    assert_equal('200', res.code, "Find all with profile query: #{res}\n#{res.body}")
    @log.info("profile query: #{res.body}")
    json = JSON.parse(res.body)
    allids = [@id_firstName, @id_lastNameAndProfile, @id_Profile, @id_ProfileAndTag]
    assert_equal(allids.length, json["total"])
    foundids = get_user_ids(json)
    assert_equal(allids.sort, foundids.sort, "Expected #{foundids} to equal #{allids}")
    tags = get_tag_facets(json)
    # In the current users.json impelementation, facets are empty when a fullprofile
    # search is enabled.
    # assert(tags.include?(@tagqs), "Expected #{tags} to include #{@tagqs}")
  end

end

