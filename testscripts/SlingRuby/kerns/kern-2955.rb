#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura/test'
include SlingUsers

class TC_Kern2621 < Test::Unit::TestCase
  include SlingTest

  def setup
    super
    @um = SlingUsers::UserManager.new @s
    m = uniqueness()
    @qs = m.to_i.to_s(36)
    # Autocomplete checks first and last names.
    @id_lastName = "id_lastName-#{m}"
    create_user(@id_lastName, "Jane", "#{@qs}son")
    # Autocomplete does not check email addresses.
    @id_email = "id_email-#{m}"
    create_user(@id_email, "Marie", "Contraire")
    res = @s.execute_post(@s.url_for("~#{@id_email}/public/authprofile/basic.profile.json"), {
        ":operation" => "import",
        ":contentType" => "json",
        ":merge" => "true",
        ":replace" => "true",
        ":replaceProperties" => "true",
        "_charset_" => "utf-8",
        ":removeTree" => "true",
        ":content" => "{'elements':{'email':{'value':'#{@qs}@example.edu'}}}"
    })
    assert_equal('200', res.code, "Should be able to post successfully to the user profile: #{res}\n#{res.body}")
    # Autocomplete checks world titles.
    @id_groupTitle = "id_groupTitle-#{m}"
    create_group(@id_groupTitle, "#{@qs}room")
    # Autocomplete does not check descriptions.
    @id_groupDescription = "id_groupDescription-#{m}"
    descriptionGroup = create_group(@id_groupDescription, "Some other thing")
    descriptionGroup.update_properties(@s,{"sakai:group-description" => "#{@qs} times"})
    wait_for_indexer()
  end
  def get_user_ids(searchjson)
    ids = searchjson["results"].map { |e| e["userid"] }.compact
  end
  def get_group_ids(searchjson)
    ids = searchjson["results"].map { |e| e["groupid"] }.compact
  end

  # Because indexing can take a while, we do all tests in a single run.
  def test_autocomplete_searches
    do_find_all
    do_autocomplete_query
  end

  def do_find_all
    res = @s.execute_get(@s.url_for("/var/search/usersgroups.tidy.json"), {
        "q" => "*",
        "page" => "0",
        "items" => "10"
    })
    assert_equal('200', res.code, "Find all with wildcard: #{res}\n#{res.body}")
    json = JSON.parse(res.body)
    assert(4 <= json["total"].to_i(), "Expected 4 or more results: #{res.body}")
  end

  def do_autocomplete_query
    res = @s.execute_get(@s.url_for("/var/search/usersgroups.tidy.json"), {
        "q" => @qs,
        "page" => "0",
        "items" => "10"
    })
    @log.info("autocomplete: #{res}\n#{res.body}")
    assert_equal('200', res.code, "Find with autocomplete query: #{res}\n#{res.body}")
    json = JSON.parse(res.body)
    expectedusers = [@id_lastName]
    foundusers = get_user_ids(json)
    assert_equal(expectedusers, foundusers, "Expected #{foundusers} to equal #{expectedusers}")
    expectedgroups = [@id_groupTitle]
    foundgroups = get_group_ids(json)
    assert_equal(expectedgroups, foundgroups, "Expected #{foundgroups} to equal #{expectedgroups}")
  end

end

