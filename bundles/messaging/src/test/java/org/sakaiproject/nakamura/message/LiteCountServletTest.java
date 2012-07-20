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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONObject;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.util.NamedList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.SessionAdaptable;
import org.sakaiproject.nakamura.api.message.LiteMessagingService;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.SolrQueryResponseWrapper;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;

/**
 *
 */
public class LiteCountServletTest {

  private LiteCountServlet servlet;
  private LiteMessagingService messagingService;
  private SolrSearchServiceFactory searchFactory;

  @Before
  public void setUp() {
    servlet = new LiteCountServlet();
    messagingService = mock(LiteMessagingService.class);
    searchFactory = mock(SolrSearchServiceFactory.class);

    servlet.messagingService = messagingService;
    servlet.searchServiceFactory = searchFactory;
  }

  @After
  public void tearDown() {
    servlet.messagingService = messagingService;
  }

  @Test
  public void testParams() throws Exception {
    SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
    SlingHttpServletResponse response = mock(SlingHttpServletResponse.class);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintWriter write = new PrintWriter(baos);
    when(response.getWriter()).thenReturn(write);

    // Request stuff
    RequestParameter groupParam = mock(RequestParameter.class);
    when(groupParam.getString()).thenReturn("foo");
    when(request.getRemoteUser()).thenReturn("admin");
    when(request.getRequestParameter("groupedby")).thenReturn(groupParam);

    // Session & search
    ResourceResolver rr = mock(ResourceResolver.class);
    when(request.getResourceResolver()).thenReturn(rr);
    javax.jcr.Session jcrSession = mock(javax.jcr.Session.class, withSettings().extraInterfaces(SessionAdaptable.class));
    when(rr.adaptTo(javax.jcr.Session.class)).thenReturn(jcrSession);
    Session session = mock(Session.class);
    when(((SessionAdaptable) jcrSession).getSession()).thenReturn(session);
    
    // Node
    when(messagingService.getFullPathToStore("admin", session)).thenReturn(
        "/path/to/store");

    List<NamedList<Object>> groupsResponse = new ArrayList<NamedList<Object>>();

    SolrDocumentList documentListA = mock(SolrDocumentList.class);
    when(documentListA.getNumFound()).thenReturn(2L);
    NamedList<Object> groupingResultA = new NamedList<Object>();
    groupingResultA.add("groupValue", "a");
    groupingResultA.add("doclist", documentListA);
    groupsResponse.add(groupingResultA);

    SolrDocumentList documentListC = mock(SolrDocumentList.class);
    when(documentListC.getNumFound()).thenReturn(1L);
    NamedList<Object> groupingResultC = new NamedList<Object>();
    groupingResultC.add("groupValue", "c");
    groupingResultC.add("doclist", documentListC);
    groupsResponse.add(groupingResultC);

    NamedList<Object> categoryResponse = new NamedList<Object>();
    categoryResponse.add("groups", groupsResponse);
    NamedList<Object> groupedList = new NamedList<Object>();
    groupedList.add("foo", categoryResponse);
    NamedList<Object> solrResponse = new NamedList<Object>();
    solrResponse.add("grouped", groupedList);
    QueryResponse solrQueryResponse = mock(QueryResponse.class);
    when(solrQueryResponse.getResponse()).thenReturn(solrResponse);

    SolrSearchResultSet resultSet = mock(SolrSearchResultSet.class, withSettings().extraInterfaces(SolrQueryResponseWrapper.class));
    when(searchFactory.getSearchResultSet(isA(SlingHttpServletRequest.class), isA(Query.class), anyBoolean())).thenReturn(resultSet);
    when(((SolrQueryResponseWrapper)resultSet).getQueryResponse()).thenReturn(solrQueryResponse);
    servlet.doGet(request, response);

    write.flush();
    String s = baos.toString("UTF-8");
    JSONObject o = new JSONObject(s);

    JSONArray arr = o.getJSONArray("count");
    assertEquals(2, arr.length());
    for (int i = 0; i < arr.length(); i++) {
      JSONObject obj = arr.getJSONObject(i);
      String group = obj.getString("group");
      if (group.equals("a")) {
        assertEquals("2", obj.getString("count"));
      } else if (group.equals("c")) {
        assertEquals("1", obj.getString("count"));
      } else {
        fail("Unexpected group name " + group);
      }
    }
  }
}
