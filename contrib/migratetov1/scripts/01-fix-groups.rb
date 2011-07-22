#!/usr/bin/env ruby

require 'net/http'
require 'net/https'
require 'rubygems'
require 'json'

@url = URI.parse("http://localhost:8080")
@user = "admin"
@pass = "admin"


def get_http()
    http = Net::HTTP.new(@url.host, @url.port)

    if (@url.scheme == "https")
        http.use_ssl = true
        http.verify_mode = OpenSSL::SSL::VERIFY_NONE
    end

    return http
end


def get_json(path, raw = false)
    http = get_http()

    req = Net::HTTP::Get.new(path)
    req.basic_auth @user, @pass
    response = http.request(req)

    if raw
        return response.body()
    else
        return JSON.parse(response.body())
    end
end


def do_post(path, params)
    http = get_http()

    req = Net::HTTP::Post.new(path)
    req.add_field("Referer", "#{@url.to_s}/dev")
    req.basic_auth @user, @pass
    req.set_form_data(params)

    return http.request(req)
end


def post_json(path, json)
    http = get_http()

    req = Net::HTTP::Post.new(path)
    req.add_field("Referer", "#{@url.to_s}/dev")
    req.basic_auth @user, @pass
    req.set_form_data({
                          ":content" => json,
                          ":operation" => "import",
                          ":replace" => "true",
                          ":replaceProperties" => "true",
                          ":contentType" => "json'",
                      })

    return http.request(req)
end


def all_groups()
    page = 0
    while true
        result = get_json("/var/search/groups-all.json?page=#{page}")

        if result['results'].empty?
            break
        end

        result['results'].each do |result|
            yield result['groupid']
        end
        page += 1
    end
end


def id_to_sparseid(tree)
    if tree.is_a?(Hash)
        result = {}
        tree.each do |key, val|
            if key == "_id"
                result["_sparseId"] = id_to_sparseid(val)
                result["_id@Delete"] = 1
            else
                result[key] = id_to_sparseid(val)
            end
        end

        return result
    else
        return tree
    end
end


def set_readable(poolid)
    ["everyone", "anonymous"].each do |user|
        do_post("/p/#{poolid}.members.html",{":viewer" => user})
        do_post("/p/#{poolid}.modifyAce.html",{"principalId" => user, "privilege@jcr:read" => "granted"})
    end
end


def create_library(group)

    library = {"sakai:copyright" => "creativecommons",
        "sakai:custom-mimetype" => "x-sakai/document",
        "sakai:description" => "",
        "sakai:permissions" => "public",
        "sakai:pool-content-created-for" => "admin",
        "sakai:pooled-content-file-name" => "Library",
        "sakai:pooled-content-manager" => [
            "#{group}-manager",
            "admin"
        ],
        "sakai:pooled-content-viewer" => [
            "anonymous",
            "#{group}-member",
            "everyone"
        ],

        "activity" => {
            "sling:resourceType" => "sakai/activityStore"
        },

        "id1367865652332" => {
            "mylibrary" => {
                "groupid" => "#{group}"
            }
        },
        "id9867543247" => {
            "page" => "<img id='widget_mylibrary_id1367865652332' class='widget_inline' style='display: block; padding: 10px; margin: 4px;' src='/devwidgets/mylibrary/images/mylibrary.png' data-mce-src='/devwidgets/mylibrary/images/mylibrary.png' data-mce-style='display: block; padding: 10px; margin: 4px;' border='1'><br></p>"
        },

        "sling:resourceType" => "sakai/pooled-content",
        "structure0" => "{\"library\":{\"_ref\":\"id9867543247\",\"_order\":0,\"_nonEditable\":true,\"_title\":\"Library\",\"main\":{\"_ref\":\"id9867543247\",\"_order\":0,\"_nonEditable\":true,\"_title\":\"Library\"}}}"
    }

    resp = do_post("/system/pool/createfile", {})
    content = JSON.parse(resp.body)

    library_poolid = content['_contentItem']['poolId']

    post_json("/p/#{library_poolid}", JSON(library))

    set_readable(library_poolid)

    return library_poolid
end


def create_participants(group)

    participants = {
        "sakai:copyright" => "creativecommons",
        "sakai:custom-mimetype" => "x-sakai/document",
        "sakai:description" => "",
        "sakai:permissions" => "public",
        "sakai:pool-content-created-for" => "admin",
        "sakai:pooled-content-file-name" => "Participants",
        "sakai:pooled-content-manager" => [
            "admin",
            "#{group}-manager"
        ],

        "activity" => {
            "sling:resourceType" => "sakai/activityStore"
        },
        "id439704665" => {
            "participants" => {
                "groupid" => "#{group}"
            }
        },
        "id6573920372" => {
            "page" => "<img id='widget_participants_id439704665' class='widget_inline' style='display: block; padding: 10px; margin: 4px;' src='/devwidgets/participants/images/participants.png' data-mce-src='/devwidgets/participants/images/participants.png' data-mce-style='display: block; padding: 10px; margin: 4px;' border='1'><br></p>"
        },

        "sakai:pooled-content-viewer" => [
            "anonymous",
            "#{group}-member",
            "everyone"
        ],
        "sling:resourceType" => "sakai/pooled-content",
        "structure0" => "{\"participants\":{\"_ref\":\"id6573920372\",\"_order\":0,\"_title\":\"Participants\",\"_nonEditable\":true,\"main\":{\"_ref\":\"id6573920372\",\"_order\":0,\"_nonEditable\":true,\"_title\":\"Participants\"}}}"
    }

    resp = do_post("/system/pool/createfile", {})
    content = JSON.parse(resp.body)

    participants_poolid = content['_contentItem']['poolId']

    post_json("/p/#{participants_poolid}", JSON(participants))

    set_readable(participants_poolid)

    return participants_poolid
end

def create_docstructure(group)
    library_poolid = create_library(group)
    participants_poolid = create_participants(group)

    # Now update the group's docstructure to link them in.
    post_json("/~#{group}/docstructure", JSON({"structure0" => "{\"library\":{\"_title\":\"Library\",\"_order\":0,\"_nonEditable\":true,\"_view\":\"[\\\"everyone\\\",\\\"anonymous\\\",\\\"-member\\\"]\",\"_edit\":\"[\\\"-manager\\\"]\",\"_pid\":\"#{library_poolid}\"},\"participants\":{\"_title\":\"Participants\",\"_order\":1,\"_nonEditable\":true,\"_view\":\"[\\\"everyone\\\",\\\"anonymous\\\",\\\"-member\\\"]\",\"_edit\":\"[\\\"-manager\\\"]\",\"_pid\":\"#{participants_poolid}\"}}"}))
end


def fix_group_membership(group)
    group_data = get_json("/system/userManager/group/#{group}.json")

    [["manager", group_data['properties']['rep:group-managers']], ["member", group_data["members"]]].each do |role,userlist|

        do_post("/system/userManager/group.create.json",
                {
                    ":name" => "#{group}-#{role}",
                    "sakai:excludeSearch" => true,
                    "sakai:pseudoGroup" => "true",
                    "sakai:pseudogroupparent" => "#{group}",
                    "sakai:group-joinable" => group_data['properties']['sakai:group-joinable'],
                    "sakai:group-visible" => group_data['properties']['sakai:group-visible'],
                    "sakai:group-description" => "",
                    "sakai:group-id" => group,
                    "sakai:group-title" => "#{group} #{role}s"
                })


        do_post("/system/userManager/group/#{group}-#{role}.update.json",
                {
                    ":manager" => "#{group}-#{role}"
                })

        userlist.each do |user|
            if (user !~ /-managers$/)
                print "Adding #{user} to #{group} as #{role}\n"

                do_post("/system/userManager/group/#{group}-#{role}.update.json",
                        {
                            ":member" => user
                        })
            end
        end
    end
end


def main()
    all_groups() do |group|
        print "Fixing group #{group}...\n"

        print "_id -> _sparseId...\n"
        tree = get_json("/~#{group}.infinity.json")
        fixed = id_to_sparseid(tree)
        post_json("/~#{group}", JSON(fixed))

        print "Setting up members and managers...\n"
        fix_group_membership(group)

        print "Creating docstructure for Library/Participants...\n"
        create_docstructure(group)
    end
end


main()
