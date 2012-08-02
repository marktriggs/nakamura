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
package org.sakaiproject.nakamura.user.lite.servlet;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.SessionAdaptable;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.user.UserFinder;
import org.sakaiproject.nakamura.user.lite.resource.RepositoryHelper;
import org.sakaiproject.nakamura.util.parameters.ContainerRequestParameter;

public class LiteUserExistsServletTest {

  @Mock
  private SlingHttpServletRequest request;
  
  @Mock
  private ResourceResolver resourceResolver;

  @Mock
  private SlingHttpServletResponse httpResponse;

  @Mock
  private UserFinder userFinder;

  private Repository repository;
  private Session session;

  private LiteUserExistsServlet servlet;

  public LiteUserExistsServletTest() throws ClientPoolException, StorageClientException, AccessDeniedException, ClassNotFoundException, IOException {
    repository = RepositoryHelper.getRepository(new String[]{ "ieb","jeff","joe"}, new String[]{"g-course101", } );
    MockitoAnnotations.initMocks(this);
  }

  @Before
  public void before() throws ClientPoolException, StorageClientException, AccessDeniedException {
    
    javax.jcr.Session jcrSession = Mockito.mock(javax.jcr.Session.class, Mockito.withSettings().extraInterfaces(SessionAdaptable.class));
    session = repository.loginAdministrative("ieb");
    Mockito.when(((SessionAdaptable)jcrSession).getSession()).thenReturn(session);
    Mockito.when(resourceResolver.adaptTo(javax.jcr.Session.class)).thenReturn(jcrSession);

    when(request.getRemoteUser()).thenReturn("ieb");
    when(request.getResourceResolver()).thenReturn(resourceResolver);
    
    servlet = new LiteUserExistsServlet();
    servlet.userFinder = userFinder;
    servlet.repository = repository;
    servlet.restrictedUsernamePattern = Pattern.compile(LiteUserExistsServlet.RESTRICTED_USERNAME_REGEX_DEFAULT, Pattern.CASE_INSENSITIVE);

  }  
  @Test
  public void testEmptyUseridParam() throws Exception {
    when(request.getParameter("userid")).thenReturn("");
    servlet.doGet(request, httpResponse);
    verify(httpResponse).sendError(eq(400), anyString());
  }
  
  @Test
  public void testUserExistsFinder() throws Exception {
    RequestParameter reqParam = new ContainerRequestParameter("foo", "utf-8");
    when(request.getRequestParameter("userid")).thenReturn(reqParam);
    when(userFinder.userExists("foo")).thenReturn(true);
    servlet.doGet(request, httpResponse);
    verify(httpResponse).setStatus(eq(HttpServletResponse.SC_NO_CONTENT));
  }

  @Test
  public void testUserExistsRepository() throws Exception {
	RequestParameter reqParam = new ContainerRequestParameter("ieb", "utf-8");
    when(request.getRequestParameter("userid")).thenReturn(reqParam);
    when(userFinder.userExists("admin")).thenReturn(false);
    servlet.doGet(request, httpResponse);
    verify(httpResponse).setStatus(eq(HttpServletResponse.SC_NO_CONTENT));
  }

  @Test
  public void testUserDoesNotExist() throws Exception {
    RequestParameter reqParam = new ContainerRequestParameter("foo", "utf-8");
    when(request.getRequestParameter("userid")).thenReturn(reqParam);
    when(userFinder.userExists("foo")).thenReturn(false);
    servlet.doGet(request, httpResponse);
    verify(httpResponse).sendError(eq(HttpServletResponse.SC_NOT_FOUND)); 

    reqParam = new ContainerRequestParameter("Admin", "utf-8");
    when(request.getRequestParameter("userid")).thenReturn(reqParam);
    when(userFinder.userExists("Admin")).thenReturn(false);
    servlet.doGet(request, httpResponse);
    verify(httpResponse).sendError(eq(HttpServletResponse.SC_CONFLICT));
  }
}
