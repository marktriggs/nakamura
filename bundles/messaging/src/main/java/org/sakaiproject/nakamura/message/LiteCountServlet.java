/**
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.nakamura.message;

import com.google.common.collect.ImmutableMap;

import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.PARAMS_ITEMS_PER_PAGE;

import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.GroupParams;
import org.apache.solr.common.util.NamedList;
import org.sakaiproject.nakamura.api.doc.BindingType;
import org.sakaiproject.nakamura.api.doc.ServiceBinding;
import org.sakaiproject.nakamura.api.doc.ServiceDocumentation;
import org.sakaiproject.nakamura.api.doc.ServiceMethod;
import org.sakaiproject.nakamura.api.doc.ServiceParameter;
import org.sakaiproject.nakamura.api.doc.ServiceResponse;
import org.sakaiproject.nakamura.api.doc.ServiceSelector;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.message.LiteMessagingService;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.SolrQueryResponseWrapper;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Will count all the messages under the current user's message store. The user can
 * specify what messages should be counted by specifying parameters in comma
 * separated values. ex:
 * /~joe/message.count.json?filters=sakai:messagebox,sakai:read&values=inbox,false&groupedby=sakai:category
 *
 * The following are optional:
 *  - filters: only nodes with the properties in filters and the values in values
 *    get traversed
 *  - groupedby: group the results by the values of this parameter.
 */
@SlingServlet(methods = {"GET"}, resourceTypes = {"sakai/messagestore"}, selectors = {"count"}, generateComponent = true, generateService = true)
@Properties(value = {
    @Property(name = "service.vendor", value = "The Sakai Foundation"),
    @Property(name = "service.description", value = "Endpoint to count messages in a messagestore.") })
@ServiceDocumentation(
    name = "LiteCountServlet documentation", okForVersion = "1.2",
    shortDescription = "Count all the internal messages a user has.",
    description = "Counts all the internal messages a user has.",
    bindings = @ServiceBinding(type = BindingType.TYPE, 
        bindings = "sakai/messagestore", 
        selectors = @ServiceSelector(name = "count")), 
    methods = @ServiceMethod(name = "GET",
        description = "Count all the internal messages a user has. <br />" +
        "Example:<br />" +
        "curl -u zach:zach http://localhost:8080/~zach/message.count.json?groupedby=sakai:category" +
        "<pre>{\"count\":[{\"group\":\"message\",\"count\":2}]}</pre>",
        response = {
          @ServiceResponse(code = 200, description = "The request returned successfully."),
          @ServiceResponse(code = 400, description = "The request did not contain all the (correct) parameters."),
          @ServiceResponse(code = 500, description = "The server was unable to count the messages.")},
        parameters = {
          @ServiceParameter(name = "filters", description = "Optional. Comma separated list of properties that should be matched"),
          @ServiceParameter(name = "values", description = "Optional. Comma separated list of values for each property."),
          @ServiceParameter(name = "groupedby", description = "Optional. A property name on what to group by, e.g. sakai:category will return separate counts for each message category.") }))

public class LiteCountServlet extends SlingSafeMethodsServlet {

  /**
   * 
   */
  private static final long serialVersionUID = -5714446506015596037L;
  private static final Logger LOGGER = LoggerFactory.getLogger(LiteCountServlet.class);

  @Reference
  protected transient LiteMessagingService messagingService;
  
  @Reference
  SolrSearchServiceFactory searchServiceFactory;

  @Override
  protected void doGet(SlingHttpServletRequest request,
      SlingHttpServletResponse response) throws ServletException, IOException {
    LOGGER.debug("In count servlet" );

    Session session = StorageClientUtils.adaptToSession(request.getResourceResolver().adaptTo(javax.jcr.Session.class));

    try {
      // We do the query on the user's messageStore.
      String messageStorePath = ClientUtils.escapeQueryChars(messagingService.getFullPathToStore(request.getRemoteUser(), session));
      // q=(messagestore:a\:208861/message/
      // &fq=resourceType:sakai/message AND type:internal AND messagebox:"inbox"
      // AND read:"false")&group=true&group.field=category&group.limit=0&fl=category
      String queryString = "messagestore:" + messageStorePath;

      StringBuilder filterQuery = new StringBuilder("resourceType:sakai/message AND type:internal");

      // Get the filters
      if (request.getRequestParameter("filters") != null
          && request.getRequestParameter("values") != null) {
        // The user wants to filter some things.
        String[] filters = request.getRequestParameter("filters").getString()
            .split(",");
        String[] values = request.getRequestParameter("values").getString()
            .split(",");
        if (filters.length != values.length) {
          response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
              "The amount of values doesn't match the amount of keys.");
        }

        for (int i = 0; i < filters.length; i++) {
          String filterName = filters[i].replaceFirst("^sakai:", "");
          filterQuery.append(" AND " + filterName + ":\"" + values[i] + "\"");
        }
      }

      // The "groupedby" clause forces a categorized count. If not
      // specified, all we need is the total count.
      String groupedBy;
      final Map<String, Object> queryOptions;
      if (request.getRequestParameter("groupedby") == null) {
        groupedBy = null;
        queryOptions = ImmutableMap.of(
            PARAMS_ITEMS_PER_PAGE, (Object) "0",
            CommonParams.START, "0",
            CommonParams.FQ, filterQuery.toString()
        );
      } else {
        groupedBy = request.getRequestParameter("groupedby").getString();
        groupedBy = groupedBy.replaceFirst("^sakai:", "");
        // group=true&group.field=category&group.limit=0&fl=category
        queryOptions = new ImmutableMap.Builder<String, Object>().
            put(PARAMS_ITEMS_PER_PAGE, "50").
            put(CommonParams.START, "0").
            put(CommonParams.FL, groupedBy).
            put(GroupParams.GROUP, "true").
            put(GroupParams.GROUP_FIELD, groupedBy).
            put(GroupParams.GROUP_LIMIT, "0").
            put(CommonParams.FQ, filterQuery.toString()).
            build();
      }

      Query query = new Query(queryString, queryOptions);
      LOGGER.info("Submitting Query {} ", query);
      SolrSearchResultSet resultSet = searchServiceFactory.getSearchResultSet(
          request, query, false);

      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");

      JSONWriter write = new JSONWriter(response.getWriter());

      if (groupedBy == null) {
        write.object();
        write.key("count");
        write.value(resultSet.getSize());
        write.endObject();
      } else {
        // The user want to group the count by a specified set.
        Map<String, Long> mapCount = getMapCount((SolrQueryResponseWrapper) resultSet, groupedBy);
        write.object();
        write.key("count");
        write.array();
        for (Entry<String, Long> e : mapCount.entrySet()) {
          write.object();

          write.key("group");
          write.value(e.getKey());
          write.key("count");
          write.value(e.getValue());

          write.endObject();
        }
        write.endArray();
        write.endObject();

      }

    } catch (JSONException e) {
      LOGGER.error("JSON issue from query " + request.getQueryString(), e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getLocalizedMessage());
    } catch (Exception e) {
      LOGGER.error("Unexpected exception for query " + request.getQueryString(), e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getLocalizedMessage());
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Long> getMapCount(SolrQueryResponseWrapper resultSet, String groupedBy) {
    Map<String, Long> mapCount = new HashMap<String, Long>();
    QueryResponse solrQueryResponse = ((SolrQueryResponseWrapper) resultSet).getQueryResponse();
    NamedList<Object> solrResponse = solrQueryResponse.getResponse();
    NamedList<Object> groupedList = (NamedList<Object>) solrResponse.get("grouped");
    NamedList<Object> categoryResponse = (NamedList<Object>) groupedList.get(groupedBy);
    List<NamedList<Object>> groupsResponse = (List<NamedList<Object>>) categoryResponse.get("groups");
    for (NamedList<Object> groupingResult : groupsResponse) {
      final String category = (String) groupingResult.get("groupValue");
      final SolrDocumentList documentList = (SolrDocumentList) groupingResult.get("doclist");
      final long categoryCount = documentList.getNumFound();
      LOGGER.debug("category = {}, count = {}", category, categoryCount);
      mapCount.put(category, categoryCount);
    }
    return mapCount;
  }
}
