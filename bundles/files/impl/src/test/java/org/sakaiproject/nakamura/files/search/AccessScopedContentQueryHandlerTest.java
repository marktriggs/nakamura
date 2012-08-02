/*
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
package org.sakaiproject.nakamura.files.search;

import junit.framework.Assert;

import org.apache.sling.api.SlingHttpServletRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.search.solr.DomainObjectSearchQueryHandler.TEMPLATE_PROPS;
import org.sakaiproject.nakamura.api.search.solr.MissingParameterException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.files.search.AbstractContentSearchQueryHandler.REQUEST_PARAMETERS;
import org.sakaiproject.nakamura.files.search.AccessScopedContentQueryHandler.SearchableRole;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests the functionality of the AccessScopedContentQueryHandlerTest. The AccessScopedContentQueryHandler
 * currently handles functionality for the following search entry-points:
 * 
 * * /var/search/pool/me/role.json
 * * /var/search/pool/me/manager.json (DEPRECATED)
 * * /var/search/pool/me/manager-all.json (DEPRECATED)
 * * /var/search/pool/me/viewer.json (DEPRECATED)
 * * /var/search/pool/me/viewer-all.json (DEPRECATED)
 * 
 */
@RunWith(MockitoJUnitRunner.class)
public class AccessScopedContentQueryHandlerTest {
  
  @Mock
  Repository repository;
  
  @Mock
  SolrSearchServiceFactory searchServiceFactory;

  /**
   * Verify that if no role can be determined, an IllegalArgumentException is thrown
   * from the handler.
   */
  @Test(expected=MissingParameterException.class)
  public void testNoRole() {
    AccessScopedContentQueryHandler meQueryHandler = new AccessScopedContentQueryHandler(searchServiceFactory, repository);
    Map<String, String> parameterMap = new HashMap<String, String>();
    
    SlingHttpServletRequest request = Mockito.mock(SlingHttpServletRequest.class);
    mockRequestForUserId(request, "mrvisser");
    
    meQueryHandler.loadUserProperties(request, parameterMap);
  }
  
  /**
   * Verify that if an invalid role is specified, an IllegalArgumentException is thrown
   * from the handler.
   */
  @Test(expected=IllegalArgumentException.class)
  public void testInvalidRole() {
    AccessScopedContentQueryHandler meQueryHandler = new AccessScopedContentQueryHandler(searchServiceFactory, repository);
    Map<String, String> parameterMap = new HashMap<String, String>();
    
    SlingHttpServletRequest request = Mockito.mock(SlingHttpServletRequest.class);
    mockRequestForUserId(request, "mrvisser");
    mockForRole(parameterMap, null);
    
    meQueryHandler.loadUserProperties(request, parameterMap);
  }
  
  /**
   * Verify that all relevant search properties are honoured when generating the lucene
   * query string.
   * 
   * @throws ClientPoolException
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  @Test
  public void testValidRequest() throws ClientPoolException, StorageClientException,
      AccessDeniedException {
    AccessScopedContentQueryHandler meQueryHandler = new AccessScopedContentQueryHandler(searchServiceFactory, repository);
    Map<String, String> parameterMap = new HashMap<String, String>();
    
    //mock the repository to generate the lucene query
    Session adminSession = Mockito.mock(Session.class);
    AuthorizableManager am = Mockito.mock(AuthorizableManager.class);
    Authorizable mrvisser = Mockito.mock(Authorizable.class);
    Group myGroup = Mockito.mock(Group.class);
    Group everyone = Mockito.mock(Group.class);
    Mockito.when(repository.loginAdministrative()).thenReturn(adminSession);
    Mockito.when(adminSession.getAuthorizableManager()).thenReturn(am);
    Mockito.when(am.findAuthorizable("mrvisser-user")).thenReturn(mrvisser);
    Mockito.when(mrvisser.getId()).thenReturn("mrvisser-user");
    Mockito.when(mrvisser.memberOf(am)).thenReturn(Arrays.asList(myGroup, everyone).iterator());
    Mockito.when(myGroup.getId()).thenReturn("mrvisser-group");
    Mockito.when(myGroup.isGroup()).thenReturn(true);
    Mockito.when(myGroup.getProperty(UserConstants.GROUP_TITLE_PROPERTY)).thenReturn("mrvisser group");
    
    // 'everyone' should be ignored in the groups listing.
    Mockito.when(everyone.getId()).thenReturn("everyone");
    Mockito.when(everyone.isGroup()).thenReturn(true);
    Mockito.when(everyone.getProperty(UserConstants.GROUP_TITLE_PROPERTY)).thenReturn("everyone");
    
    // mock the search input parameters
    SlingHttpServletRequest request = Mockito.mock(SlingHttpServletRequest.class);
    mockRequestForUserId(request, "mrvisser-user");
    mockForRole(parameterMap, SearchableRole.manager);
    // parameters are pre-escaped from the request. Ensure the handler does not re-escape the parameters
    mockForMime(parameterMap, "sakai/x\\-collection");
    mockForQuery(parameterMap, "blah\\-query");
    
    meQueryHandler.loadUserProperties(request, parameterMap);
    
    String q = parameterMap.get(TEMPLATE_PROPS._q.toString());
    
    Assert.assertEquals("((manager:(mrvisser\\-group OR mrvisser\\-user)) AND (content:(blah\\-query) OR filename:(blah\\-query) OR tag:(blah\\-query) OR description:(blah\\-query) OR ngram:(blah\\-query) OR edgengram:(blah\\-query) OR widgetdata:(blah\\-query)) AND mimeType:sakai/x\\-collection)", q);
    Assert.assertEquals(parameterMap.get(REQUEST_PARAMETERS.userid.toString()), "mrvisser-user");
    Assert.assertEquals(parameterMap.get(REQUEST_PARAMETERS.role.toString()), "manager");
  }
  
  private void mockRequestForUserId(SlingHttpServletRequest request, String userId) {
    Mockito.when(request.getRemoteUser()).thenReturn(userId);
  }

  private void mockForRole(Map<String, String> parameterMap, SearchableRole role) {
    if (role == null) {
      parameterMap.put(REQUEST_PARAMETERS.role.toString(), "invalid role");
    } else {
      parameterMap.put(REQUEST_PARAMETERS.role.toString(), role.toString());
    }
  }
  
  private void mockForMime(Map<String, String> properties, String mime) {
    properties.put(REQUEST_PARAMETERS.mimetype.toString(), mime);
  }
  
  private void mockForQuery(Map<String, String> properties, String query) {
    properties.put(REQUEST_PARAMETERS.q.toString(), query);
  }
}
