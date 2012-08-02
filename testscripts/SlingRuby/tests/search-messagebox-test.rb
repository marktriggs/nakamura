#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura'
require 'nakamura/test'
require 'nakamura/message'
require 'test/unit.rb'
include SlingInterface
include SlingUsers
include SlingMessage

class TC_MyMessageTest < Test::Unit::TestCase
  include SlingTest

  def setup
    super
    @mm = MessageManager.new(@s)
  end

  def test_search_message_with_query
    m = uniqueness()
    
    ztId = "zach-#{m}"
    bvId = "branden-#{m}"
    
    @log.info("Creating user #{bvId}")
    bv = create_user(bvId)
    @log.info("Creating user #{ztId}")
    zt = create_user(ztId)
    @s.switch_user(zt)
    @log.info("Sending a message to Branden")
    res = @mm.create(bvId, "internal", "outbox")
    assert_equal("200", res.code, "Expected to create a message ")
    
	wait_for_indexer()

	@log.info("Verifying boxcategory search with a query string")
	res = @s.execute_get("http://localhost:8080/var/message/boxcategory.tidy.json?box=outbox&q=#{ztId}&category=message")
	assert_equal("200", res.code, "Expected to successfully search for zach's outbox.")	
	json = JSON.parse(res.body)
	assert_equal(1, json["total"], "Expected to have exactly one message sent from #{ztId}.")
	
	@log.info("Verifying boxcategory search without a query string (boxcategory-all)")
	res = @s.execute_get("http://localhost:8080/var/message/boxcategory-all.tidy.json?box=outbox&category=message")
	assert_equal("200", res.code, "Expected to successfully search for zach's outbox.")
	json = JSON.parse(res.body)
	assert_equal(1, json["total"], "Expected to have exactly one message sent from #{ztId}.")
	
  end
  
end