#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura/test'
include SlingUsers
include SlingFile

class TC_FindContentTest < Test::Unit::TestCase
  include SlingTest

  def setup
    super
    @um = SlingUsers::UserManager.new(@s)
    @fm = FileManager.new(@s)
    m = uniqueness()
    @qs = m.to_i.to_s(36)
    @tagqs = "directory/#{@qs.reverse}"
    res = @s.create_node("/tags/#{@tagqs}", {
        "sakai:tag-name" => @tagqs,
        "sling:resourceType" => "sakai/tag",
        "_charset_" => "utf-8"
    })
    assert_equal('201', res.code, "Should be able to create tag: #{res}\n#{res.body}")
    # Create pooled content which matches on description.
    @id_description = "descriptionmatch#{m}"
    res = @fm.upload_pooled_file(@id_description, "A beautiful #{@qs}", 'text/plain')
    assert_equal("201",res.code,res.body)
    # Create widget data which matches.
    @id_widget = "widgetmatch#{m}"
    create_content_with_widgetdata(@id_widget, "A beautiful widget", @qs)
    # Create pooled content and widget data which does not match.
    @id_nomatch = "nomatch#{m}"
    create_content_with_widgetdata(@id_nomatch, "An ugly widget", "ugly")
    # Create pooled content which matches on tag.
    @id_tag = "tagmatch#{m}"
    res = @fm.upload_pooled_file(@id_tag, "taggable", 'text/plain')
    assert_equal("201",res.code,res.body)
    json = JSON.parse(res.body)
    poolid = json[@id_tag]["poolId"]
    set_tag(poolid, @tagqs)
    # Create pooled content which matches on both description and tag.
    @id_descriptionandtag = "descriptiontagmatch#{m}"
    res = @fm.upload_pooled_file(@id_descriptionandtag, "Tag and a #{@qs}", 'text/plain')
    assert_equal("201",res.code,res.body)
    json = JSON.parse(res.body)
    poolid = json[@id_descriptionandtag]["poolId"]
    set_tag(poolid, @tagqs)
    # Create pooled content which matches on both description and widget.
    @id_descriptionandwidget = "descriptionwidgetmatch#{m}"
    create_content_with_widgetdata(@id_descriptionandwidget, "A widget and a #{@qs}", @qs)
    wait_for_indexer()
  end
  def create_content_with_widgetdata(filename, description, widgetstring)
    res = @fm.upload_pooled_file(filename, description, 'text/plain')
    @log.debug(res.body)
    assert_equal("201",res.code,res.body)
    json = JSON.parse(res.body)
    poolid = json[filename]["poolId"]
    res = @s.create_node("p/#{poolid}/widgettish", {
        ":operation" => "import",
        ":contentType" => "json",
        ":merge" => "true",
        ":replace" => "true",
        ":replaceProperties" => "true",
        "_charset_" => "utf-8",
        ":content" => '{"content":"Widget has ' + widgetstring + ' somewhere","sakai:indexed-fields":"content","sling:resourceType":"sakai/widget-data"}'
     })
    assert_equal("201",res.code,res.body)
    res
  end
  def set_tag(id, tag)
    res = @s.execute_post(@s.url_for("p/#{id}"), {
        ":operation" => "tag",
        "key" => "/tags/#{tag}",
        "_charset_" => "utf-8"
    })
    assert_equal('200', res.code, "Should be able to tag successfully: #{res}\n#{res.body}")
  end
  def get_content_ids(searchjson)
    ids = searchjson["results"].map { |e| e["sakai:pooled-content-file-name"] }
  end
  def get_tag_facets(searchjson)
    tags = []
    @log.debug(searchjson["facet_fields"])
    @log.debug(searchjson["facet_fields"][0])
    @log.debug(searchjson["facet_fields"][0]["tagname"])
    searchjson["facet_fields"][0]["tagname"].map { |e|
        @log.debug("#{e}, keys = #{e.keys}")
        tags.concat(e.keys)
    }
    tags
  end

  # Because indexing new user accounts and profile changes can take a while,
  # we do all tests in a single run.
  def test_content_searches
    do_content_all
    do_simple_query
    do_tag_query
  end

  def do_content_all
    # WARNING: This won't work if the test system has hundreds of matches.
    res = @s.execute_get(@s.url_for("/var/search/pool/all.tidy.json"), {
        "q" => "",
        "page" => "0",
        "items" => "400"
    })
    assert_equal('200', res.code, "Find all with wildcard: #{res}\n#{res.body}")
    @log.debug("wildcard: #{res}\n#{res.body}")
    json = JSON.parse(res.body)
    allids = [@id_description, @id_widget, @id_nomatch, @id_tag, @id_descriptionandtag, @id_descriptionandwidget]
    foundids = get_content_ids(json)
    assert((allids - foundids).empty?, "Expected #{foundids} to include #{allids}")
    tags = get_tag_facets(json)
    assert(tags.include?(@tagqs), "Expected #{tags} to include #{@tagqs}")
  end

  def do_simple_query
    res = @s.execute_get(@s.url_for("/var/search/pool/all.tidy.json"), {
        "q" => @qs,
        "page" => "0",
        "items" => "400"
    })
    assert_equal('200', res.code, "Find all with simple query: #{res}\n#{res.body}")
    @log.debug("simple query: #{res.body}")
    json = JSON.parse(res.body)
    allids = [@id_description, @id_widget, @id_descriptionandtag, @id_descriptionandwidget]
    foundids = get_content_ids(json)
    assert_equal(allids.sort, foundids.sort, "Expected #{foundids} to equal #{allids}")
    tags = get_tag_facets(json)
    assert(tags.include?(@tagqs), "Expected #{tags} to include #{@tagqs}")
  end

  def do_tag_query
    res = @s.execute_get(@s.url_for("/var/search/pool/all.tidy.json"), {
        "q" => "*",
        "tags" => @tagqs,
        "page" => "0",
        "items" => "400"
    })
    assert_equal('200', res.code, "Find all with tag query: #{res}\n#{res.body}")
    @log.debug("tag query: #{res.body}")
    json = JSON.parse(res.body)
    allids = [@id_tag, @id_descriptionandtag]
    foundids = get_content_ids(json)
    assert_equal(allids.sort, foundids.sort, "Expected #{foundids} to equal #{allids}")
    tags = get_tag_facets(json)
    assert(tags.include?(@tagqs), "Expected #{tags} to include #{@tagqs}")
  end

end

