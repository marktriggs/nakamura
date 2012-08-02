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

import org.apache.sling.api.SlingHttpServletRequest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.search.solr.DomainObjectSearchQueryHandler.DEFAULT_REQUEST_PARAMS;
import org.sakaiproject.nakamura.api.search.solr.DomainObjectSearchQueryHandler.TEMPLATE_PROPS;
import org.sakaiproject.nakamura.api.search.solr.MissingParameterException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.files.search.AbstractContentSearchQueryHandler.REQUEST_PARAMETERS;
import org.sakaiproject.nakamura.testutils.MockUtils;

import java.util.HashMap;
import java.util.Map;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class LibraryContentQueryHandlerTest {

  @Mock
  Repository repository;
  
  @Mock
  SolrSearchServiceFactory searchServiceFactory;

  @Test(expected=MissingParameterException.class)
  public void testAnonUserFailed() {
    LibraryContentQueryHandler handler = new LibraryContentQueryHandler(searchServiceFactory, repository);
    Map<String, String> params = new HashMap<String, String>();
    SlingHttpServletRequest request = Mockito.mock(SlingHttpServletRequest.class);
    mockRequestForUserId(request, User.ANON_USER);
    handler.loadUserProperties(request, params);
  }
  
  @Test
  public void testUser() throws AccessDeniedException, StorageClientException {
    LibraryContentQueryHandler handler = new LibraryContentQueryHandler(searchServiceFactory, repository);
    Map<String, String> params = new HashMap<String, String>();
    Session session = Mockito.mock(Session.class);
    SlingHttpServletRequest request = MockUtils.mockRequestWithSession(session);

    mockRequestForUserId(request, "mrvisser");
    mockQuery(params, "blah\\-query");
    mockMime(params, "sakai/x\\-collection");
    mockRequestForLevels(request, "0");
    
    AuthorizableManager am = Mockito.mock(AuthorizableManager.class);
    Authorizable mrvisser = Mockito.mock(Authorizable.class);
    Authorizable mrvisserGroup = Mockito.mock(Authorizable.class);
    Mockito.when(repository.loginAdministrative()).thenReturn(session);
    Mockito.when(session.getAuthorizableManager()).thenReturn(am);
    Mockito.when(am.findAuthorizable("mrvisser")).thenReturn(mrvisser);
    Mockito.when(am.findAuthorizable("mrvisser-group")).thenReturn(mrvisserGroup);
    Mockito.when(mrvisser.getPrincipals()).thenReturn(new String[] { "mrvisser-group", "everyone" });
    Mockito.when(mrvisserGroup.getPrincipals()).thenReturn(new String[0]);
    
    handler.loadUserProperties(request, params);
    
    String q = params.get(TEMPLATE_PROPS._q.toString());
    
    // verify the integrity of the resulting query string.
    Assert.assertEquals("(" +
    		"(content:(blah\\-query) OR filename:(blah\\-query) OR tag:(blah\\-query) OR description:(blah\\-query) OR ngram:(blah\\-query) OR edgengram:(blah\\-query) OR widgetdata:(blah\\-query)) AND " +
    		"mimeType:sakai/x\\-collection AND " +
    		"((manager:(mrvisser) OR editor:(mrvisser) OR viewer:(mrvisser)) OR (showalways:true AND (manager:(mrvisser OR mrvisser\\-group) OR editor:(mrvisser OR mrvisser\\-group) OR viewer:(mrvisser OR mrvisser\\-group)))))",
    		q);
  }
  
  private void mockRequestForUserId(SlingHttpServletRequest request, String userId) {
    Mockito.when(request.getRemoteUser()).thenReturn(userId);
  }
  
  private void mockQuery(Map<String, String> params, String query) {
    params.put(DEFAULT_REQUEST_PARAMS.q.toString(), query);
  }
  
  private void mockMime(Map<String, String> params, String mime) {
    params.put(REQUEST_PARAMETERS.mimetype.toString(), mime);
  }
  
  private void mockRequestForLevels(SlingHttpServletRequest request, String levels) {
    Mockito.when(request.getParameter(REQUEST_PARAMETERS.levels.toString())).thenReturn(levels);
  }
}
