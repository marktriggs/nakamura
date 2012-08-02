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
package org.sakaiproject.nakamura.basiclti;

import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.contains;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sakaiproject.nakamura.api.basiclti.BasicLTIAppConstants.LTI_ADMIN_NODE_NAME;
import static org.sakaiproject.nakamura.api.basiclti.BasicLTIAppConstants.LTI_KEY;
import static org.sakaiproject.nakamura.api.basiclti.BasicLTIAppConstants.LTI_SECRET;
import static org.sakaiproject.nakamura.api.basiclti.BasicLTIAppConstants.LTI_URL;
import static org.sakaiproject.nakamura.api.basiclti.BasicLTIAppConstants.LTI_VTOOL_ID;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.sakaiproject.nakamura.api.basiclti.LiteBasicLTIContextIdResolver;
import org.sakaiproject.nakamura.api.basiclti.VirtualToolDataProvider;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.SessionAdaptable;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.api.util.LocaleUtils;
import org.sakaiproject.nakamura.util.LocaleUtilsImpl;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;

import javax.servlet.http.HttpServletResponse;

@RunWith(MockitoJUnitRunner.class)
public class LiteBasicLTIConsumerServletTest {
  private static final String SAKAI_EXTERNAL_COURSE_ID = "sakai:external-course-id";
  private static final String GROUP_ID = "qwerty1234";
  private static final String _12345 = "12345";
  private static final String SECRET = "ourSpecialSecret";
  private static final String SAKAI_GRADEBOOK_GWT_RPC = "sakai.gradebook.gwt.rpc";
  private static final String ADMIN = "admin";
  private static final String CURRENT_USER_ID = "lance";
  LiteBasicLTIConsumerServlet liteBasicLTIConsumerServlet;
  protected transient LiteBasicLTIContextIdResolver contextIdResolver;
  protected transient VirtualToolDataProvider virtualToolDataProvider;
  protected transient LocaleUtils localeUtils;
  String[] selectors;
  String contentPath;
  String adminContentPath;
  Map<String, Object> contentProperties;
  Map<String, Object> adminContentProperties;
  boolean withinWorld;
  boolean widgetUseCase;
  boolean debugEnabled;
  boolean releasePrincipalName;
  boolean releaseNames;
  boolean releaseEmail;
  boolean externalCourseId;
  boolean hasFirstName;
  boolean hasLastName;
  boolean hasEmail;
  boolean canManageContentPool;
  boolean anonymous;

  @Mock
  protected transient Repository sparseRepository;
  @Mock
  protected transient EventAdmin eventAdmin;
  @Mock
  Content content;
  @Mock
  Content pooledContentNode;
  @Mock
  Content adminContent;
  @Mock
  SlingHttpServletRequest request;
  @Mock
  SlingHttpServletResponse response;
  @Mock
  RequestPathInfo requestPathInfo;
  @Mock
  Resource resource;
  @Mock
  ResourceResolver resourceResolver;
  @Mock(extraInterfaces = { SessionAdaptable.class })
  javax.jcr.Session jcrSession;
  @Mock
  Session userSession;
  @Mock
  Session adminSession;
  @Mock
  AccessControlManager accessControlManager;
  @Mock
  AuthorizableManager authorizableManager;
  @Mock
  Authorizable authorizable;
  @Mock
  ContentManager adminContentManager;
  @Mock
  ContentManager userContentManager;
  @Mock
  ComponentContext componentContext;
  @Mock
  Dictionary<?, ?> properties;
  @Mock
  PrintWriter writer;
  @Mock
  Group group;
  @Mock
  LiteBasicLTIContextIdResolver mockContextIdResolver;

  @Before
  public void setUp() throws Exception {
    withinWorld = true;
    widgetUseCase = false;
    debugEnabled = false;
    releasePrincipalName = true;
    releaseNames = true;
    releaseEmail = true;
    externalCourseId = true;
    hasFirstName = true;
    hasLastName = true;
    hasEmail = true;
    canManageContentPool = true;
    anonymous = false;
    virtualToolDataProvider = new CLEVirtualToolDataProvider();
    contextIdResolver = new LiteDefaultContextIdResolver();
    localeUtils = new LocaleUtilsImpl();
    liteBasicLTIConsumerServlet = new LiteBasicLTIConsumerServlet();
    liteBasicLTIConsumerServlet.sparseRepository = sparseRepository;
    liteBasicLTIConsumerServlet.contextIdResolver = contextIdResolver;
    liteBasicLTIConsumerServlet.eventAdmin = eventAdmin;
    liteBasicLTIConsumerServlet.virtualToolDataProvider = virtualToolDataProvider;
    liteBasicLTIConsumerServlet.localeUtils = localeUtils;
    when(request.getRequestPathInfo()).thenReturn(requestPathInfo);
    when(requestPathInfo.getSelectors()).thenReturn(selectors);
    when(requestPathInfo.getExtension()).thenReturn("json");
    when(request.getResource()).thenReturn(resource);
    when(resource.adaptTo(Content.class)).thenReturn(content);
    when(request.getResourceResolver()).thenReturn(resourceResolver);
    when(resourceResolver.adaptTo(javax.jcr.Session.class)).thenReturn(jcrSession);
    when(((SessionAdaptable) jcrSession).getSession()).thenReturn(userSession);
    contentPath = "mHAY1acZec/id470170/id7577541/sakai2gradebook";
    adminContentPath = contentPath + "/" + LTI_ADMIN_NODE_NAME;
    when(content.getPath()).thenReturn(contentPath);
    setupProperties();
    when(content.getProperties()).thenReturn(contentProperties);
    when(request.getRemoteUser()).thenReturn(CURRENT_USER_ID);
    when(userSession.getAccessControlManager()).thenReturn(accessControlManager);
    when(userSession.getAuthorizableManager()).thenReturn(authorizableManager);
    when(userSession.getUserId()).thenReturn(CURRENT_USER_ID);
    when(authorizableManager.findAuthorizable(anyString())).thenReturn(authorizable);
    when(userSession.getContentManager()).thenReturn(userContentManager);
    when(userContentManager.get(anyString())).thenReturn(pooledContentNode);
    when(pooledContentNode.hasProperty(eq("sling:resourceType"))).thenReturn(true);
    when(pooledContentNode.getProperty(eq("sling:resourceType"))).thenReturn(
        "sakai/whatever").thenReturn("sakai/pooled-content");
    when(pooledContentNode.getPath()).thenReturn("/p/j25KqM5SU4/id7339024").thenReturn(
        "/p/j25KqM5SU4");
    when(pooledContentNode.getProperty(eq("sakai:pooled-content-file-name"))).thenReturn(
        "pooledContentFileName");
    when(pooledContentNode.getProperty(eq("sakai:description"))).thenReturn(
        "pooledContentDescription");
    when(request.getParameter("groupid")).thenReturn(GROUP_ID);
    when(authorizable.hasProperty(eq("firstName"))).thenReturn(true);
    when(authorizable.getProperty(eq("firstName"))).thenReturn("Lance");
    when(authorizable.hasProperty(eq("lastName"))).thenReturn(true);
    when(authorizable.getProperty(eq("lastName"))).thenReturn("Speelmon");
    when(authorizable.hasProperty(eq("email"))).thenReturn(true);
    when(authorizable.getProperty(eq("email"))).thenReturn("lance@foo.org");
    when(authorizableManager.findAuthorizable(eq(GROUP_ID))).thenReturn(group);
    when(group.hasProperty(eq(SAKAI_EXTERNAL_COURSE_ID))).thenReturn(true);
    when(group.getProperty(eq(SAKAI_EXTERNAL_COURSE_ID))).thenReturn("SIS-101-001");
    when(request.getLocale()).thenReturn(Locale.ENGLISH);

    // test author case by default
    when(
        accessControlManager.can(eq(authorizable), anyString(), anyString(),
            eq(Permissions.CAN_WRITE_ACL))).thenReturn(true);
    when(
        accessControlManager.can(eq(authorizable), anyString(), anyString(),
            eq(Permissions.CAN_DELETE))).thenReturn(true);
    when(sparseRepository.loginAdministrative()).thenReturn(adminSession);
    when(adminSession.getContentManager()).thenReturn(adminContentManager);
    when(adminContentManager.get(eq(adminContentPath))).thenReturn(adminContent);
    when(adminContent.getProperties()).thenReturn(adminContentProperties);
    when(componentContext.getProperties()).thenReturn(properties);
    when(response.getWriter()).thenReturn(writer);
    liteBasicLTIConsumerServlet.activate(componentContext);
    when(authorizable.getId()).thenReturn(CURRENT_USER_ID);
    when(content.hasProperty(eq("sling:resourceType"))).thenReturn(true);
    when(content.getProperty(eq("sling:resourceType"))).thenReturn("sakai/basiclti");

    // test virtual tool use case by default (i.e. false)
    when(adminContentManager.exists(eq(adminContentPath))).thenReturn(false);
    when(content.hasProperty(eq(LTI_VTOOL_ID))).thenReturn(true);
    when(content.getProperty(eq(LTI_VTOOL_ID))).thenReturn(SAKAI_GRADEBOOK_GWT_RPC);

    when(
        mockContextIdResolver.resolveContextId(any(Content.class), anyString(),
            any(Session.class))).thenReturn(null);
  }

  /**
   * Build on {@link #setUp()} but add launch selector.
   * 
   * @throws Exception
   */
  public void setUpLaunchUseCase() throws Exception {
    selectors = new String[] { "launch" };
    when(requestPathInfo.getSelectors()).thenReturn(selectors);
    when(requestPathInfo.getExtension()).thenReturn("html");
  }

  /**
   * Re-purpose {@link #setUp()} for widget use case.
   */
  public void setupWidgetUseCase() {
    widgetUseCase = true;
    debugEnabled = true;
    when(adminContentManager.exists(eq(adminContentPath))).thenReturn(true);
    when(content.hasProperty(eq(LTI_VTOOL_ID))).thenReturn(false);

    contentProperties.remove("lti_virtual_tool_id");
    contentProperties.put(LTI_URL, "http://dr-chuck.com/ims/php-simple/tool.php");
    contentProperties.put("border_color", "cccccc");
    contentProperties.put("width_unit", "%");
    contentProperties.put("width", 100);
    contentProperties.put("debug", true);
    contentProperties.put("isSakai2Tool", false);
    contentProperties.put("release_names", true);
    contentProperties.put("frame_height", 900);
    contentProperties.put("release_email", true);
    contentProperties.put("border_size", 1);
    contentProperties.put("release_email", true);
    contentProperties.put("release_principal_name", releasePrincipalName);
  }

  /**
   * Modify {@link #setUp()} to be outside of world instead inside.
   */
  public void setupOutsideWorld() {
    withinWorld = false;
    when(request.getParameter("groupid")).thenReturn(null);
    when(group.hasProperty(eq(SAKAI_EXTERNAL_COURSE_ID))).thenReturn(false);
  }

  /**
   * Happy case for the virtual tool use case.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetVirtualToolPlacement() throws Exception {
    liteBasicLTIConsumerServlet.doGet(request, response);
    verifyRenderedJson(false);
  }

  /**
   * Happy case for the virtual tool use case where user is not author.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetVirtualToolPlacementNotAuthor() throws Exception {
    when(
        accessControlManager.can(eq(authorizable), anyString(), anyString(),
            eq(Permissions.CAN_WRITE_ACL))).thenReturn(false);

    liteBasicLTIConsumerServlet.doGet(request, response);
    verifyRenderedJson(false);
  }

  /**
   * Happy case for the widget use case.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetWidgetUseCase() throws Exception {
    setupWidgetUseCase();

    liteBasicLTIConsumerServlet.doGet(request, response);

    verifyRenderedJson(true);
  }

  /**
   * Happy case for the widget use case where user is not author.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetWidgetUseCaseNotAuthor() throws Exception {
    setupWidgetUseCase();
    when(
        accessControlManager.can(eq(authorizable), anyString(), anyString(),
            eq(Permissions.CAN_WRITE_ACL))).thenReturn(false);

    liteBasicLTIConsumerServlet.doGet(request, response);

    verifyRenderedJson(false);
  }

  /**
   * Edge case where resource cannot be found.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetNotFound() throws Exception {
    when(request.getResource()).thenReturn(null);

    liteBasicLTIConsumerServlet.doGet(request, response);

    verify(response).sendError(eq(HttpServletResponse.SC_NOT_FOUND), anyString());
  }

  /**
   * Edge case where resource is found but content is null.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetNullContent() throws Exception {
    when(resource.adaptTo(Content.class)).thenReturn(null);

    liteBasicLTIConsumerServlet.doGet(request, response);

    verify(response).sendError(eq(HttpServletResponse.SC_NOT_FOUND), anyString());
  }

  /**
   * Edge case where {@link IOException} is thrown.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetIOException() throws Exception {
    doThrow(IOException.class).when(response).getWriter();

    liteBasicLTIConsumerServlet.doGet(request, response);

    verify(response).sendError(eq(HttpServletResponse.SC_INTERNAL_SERVER_ERROR),
        anyString());
  }

  /**
   * Happy case where we are using virtual tool and launch selector inside world.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetDoLaunchInWorld() throws Exception {
    setUpLaunchUseCase();

    liteBasicLTIConsumerServlet.doGet(request, response);

    verifyHtml();
  }

  /**
   * Happy case using widget and launch selector inside world.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetDoLaunchInWorldWidgetUseCase() throws Exception {
    setupWidgetUseCase();
    setUpLaunchUseCase();

    liteBasicLTIConsumerServlet.doGet(request, response);

    verifyHtml();
  }

  /**
   * Happy launch case outside of world.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetDoLaunchOutsideWorld() throws Exception {
    setUpLaunchUseCase();
    setupOutsideWorld();

    liteBasicLTIConsumerServlet.doGet(request, response);

    verifyHtml();
  }

  /**
   * Happy launch case outside of world with a widget placement.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetDoLaunchOutsideWorldWidgetUseCase() throws Exception {
    setupWidgetUseCase();
    setUpLaunchUseCase();
    setupOutsideWorld();

    liteBasicLTIConsumerServlet.doGet(request, response);

    verifyHtml();
  }

  /**
   * Happy launch case inside world without an external course id.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetDoLaunchInWorldNoExternalCourseId() throws Exception {
    setUpLaunchUseCase();
    when(group.hasProperty(eq(SAKAI_EXTERNAL_COURSE_ID))).thenReturn(false);
    externalCourseId = false;

    liteBasicLTIConsumerServlet.doGet(request, response);

    verifyHtml();
  }

  /**
   * Happy launch case inside world do not release principal name.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetDoLaunchInWorldReleasePrincipalFalse() throws Exception {
    setupWidgetUseCase();
    setUpLaunchUseCase();
    releasePrincipalName = false;
    contentProperties.put("release_principal_name", false);

    liteBasicLTIConsumerServlet.doGet(request, response);

    verifyHtml();
  }

  /**
   * Happy launch case inside world do not release names.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetDoLaunchInWorldReleaseNamesFalse() throws Exception {
    setupWidgetUseCase();
    setUpLaunchUseCase();
    releaseNames = false;
    contentProperties.put("release_names", false);

    liteBasicLTIConsumerServlet.doGet(request, response);

    verifyHtml();
  }

  /**
   * Happy launch case inside world do not release email address.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetDoLaunchInWorldReleaseEmailFalse() throws Exception {
    setupWidgetUseCase();
    setUpLaunchUseCase();
    contentProperties.put("release_email", false);
    releaseEmail = false;

    liteBasicLTIConsumerServlet.doGet(request, response);

    verifyHtml();
  }

  /**
   * Edge case inside world where user has no first name.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetDoLaunchNoFirstName() throws Exception {
    setUpLaunchUseCase();
    when(authorizable.hasProperty(eq("firstName"))).thenReturn(false);
    hasFirstName = false;

    liteBasicLTIConsumerServlet.doGet(request, response);

    verifyHtml();
  }

  /**
   * Edge case inside world where user has no last name.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetDoLaunchNoLastName() throws Exception {
    setUpLaunchUseCase();
    when(authorizable.hasProperty(eq("lastName"))).thenReturn(false);
    hasLastName = false;

    liteBasicLTIConsumerServlet.doGet(request, response);

    verifyHtml();
  }

  /**
   * Edge case inside world where user has no email address.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetDoLaunchNoEmail() throws Exception {
    setUpLaunchUseCase();
    when(authorizable.hasProperty(eq("email"))).thenReturn(false);
    hasEmail = false;

    liteBasicLTIConsumerServlet.doGet(request, response);

    verifyHtml();
  }

  /**
   * Happy case inside world where user is a student.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetDoLaunchInWorldAsStudent() throws Exception {
    setUpLaunchUseCase();
    when(
        accessControlManager.can(eq(authorizable), anyString(), anyString(),
            eq(Permissions.CAN_WRITE_ACL))).thenReturn(false);
    canManageContentPool = false;

    liteBasicLTIConsumerServlet.doGet(request, response);

    verifyHtml();
  }

  /**
   * Happy case inside world where user is anonymous.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetDoLaunchInWorldAsAnonymous() throws Exception {
    setUpLaunchUseCase();
    when(
        accessControlManager.can(eq(authorizable), anyString(), anyString(),
            eq(Permissions.CAN_WRITE_ACL))).thenReturn(false);
    canManageContentPool = false;
    when(userSession.getUserId()).thenReturn(UserConstants.ANON_USERID);
    when(authorizable.getId()).thenReturn(UserConstants.ANON_USERID);
    when(authorizable.hasProperty(eq("firstName"))).thenReturn(false);
    hasFirstName = false;
    when(authorizable.hasProperty(eq("lastName"))).thenReturn(false);
    hasLastName = false;
    anonymous = true;

    liteBasicLTIConsumerServlet.doGet(request, response);

    verifyHtml();
  }

  /**
   * Edge case where resource is not found.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetDoLaunchResourceNotFound() throws Exception {
    setUpLaunchUseCase();
    when(request.getResource()).thenReturn(null);

    liteBasicLTIConsumerServlet.doGet(request, response);

    verify(response).sendError(eq(HttpServletResponse.SC_NOT_FOUND), anyString());
  }

  /**
   * Edge case where resource is found but content is null.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetDoLaunchNullContent() throws Exception {
    setUpLaunchUseCase();
    when(resource.adaptTo(Content.class)).thenReturn(null);

    liteBasicLTIConsumerServlet.doGet(request, response);

    verify(response).sendError(eq(HttpServletResponse.SC_NOT_FOUND), anyString());
  }

  /**
   * Edge case where LTI URL is null.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetDoLaunchNullLtiUrl() throws Exception {
    setupWidgetUseCase();
    setUpLaunchUseCase();
    contentProperties.remove(LTI_URL);

    liteBasicLTIConsumerServlet.doGet(request, response);

    verify(response).sendError(eq(HttpServletResponse.SC_INTERNAL_SERVER_ERROR),
        anyString());
  }

  /**
   * Edge case where LTI URL is empty.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetDoLaunchEmptyLtiUrl() throws Exception {
    setupWidgetUseCase();
    setUpLaunchUseCase();
    contentProperties.put(LTI_URL, "");

    liteBasicLTIConsumerServlet.doGet(request, response);

    verify(response).sendError(eq(HttpServletResponse.SC_INTERNAL_SERVER_ERROR),
        anyString());
  }

  /**
   * Edge case where LTI URL is malformed.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetDoLaunchMalformedLtiUrl() throws Exception {
    setupWidgetUseCase();
    setUpLaunchUseCase();
    contentProperties.put(LTI_URL, ".");

    liteBasicLTIConsumerServlet.doGet(request, response);

    verify(response).sendError(eq(HttpServletResponse.SC_INTERNAL_SERVER_ERROR),
        anyString());
  }

  @Test
  public void testDoGetDoLaunchIncestuousLtiUrl() throws Exception {
    setupWidgetUseCase();
    setUpLaunchUseCase();
    contentProperties.put(LTI_URL, "http://localhost:8080/p/" + contentPath
        + ".launch.html");

    liteBasicLTIConsumerServlet.doGet(request, response);

    verify(response).sendError(eq(HttpServletResponse.SC_INTERNAL_SERVER_ERROR),
        contains("cannot launch into itself"));
  }

  /**
   * Edge case where LTI key is null.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetDoLaunchNullLtiKey() throws Exception {
    setupWidgetUseCase();
    setUpLaunchUseCase();
    adminContentProperties.remove(LTI_KEY);

    liteBasicLTIConsumerServlet.doGet(request, response);

    verify(response).sendError(eq(HttpServletResponse.SC_INTERNAL_SERVER_ERROR),
        anyString());
  }

  /**
   * Edge case where LTI key is empty.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetDoLaunchEmptyLtiKey() throws Exception {
    setupWidgetUseCase();
    setUpLaunchUseCase();
    adminContentProperties.put(LTI_KEY, "");

    liteBasicLTIConsumerServlet.doGet(request, response);

    verify(response).sendError(eq(HttpServletResponse.SC_INTERNAL_SERVER_ERROR),
        anyString());
  }

  /**
   * Edge case where LTI secret is null.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetDoLaunchNullLtiSecret() throws Exception {
    setupWidgetUseCase();
    setUpLaunchUseCase();
    adminContentProperties.remove(LTI_SECRET);

    liteBasicLTIConsumerServlet.doGet(request, response);

    verify(response).sendError(eq(HttpServletResponse.SC_INTERNAL_SERVER_ERROR),
        anyString());
  }

  /**
   * Edge case where LTI secret is empty.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetDoLaunchEmptyLtiSecret() throws Exception {
    setupWidgetUseCase();
    setUpLaunchUseCase();
    adminContentProperties.put(LTI_SECRET, "");

    liteBasicLTIConsumerServlet.doGet(request, response);

    verify(response).sendError(eq(HttpServletResponse.SC_INTERNAL_SERVER_ERROR),
        anyString());
  }

  /**
   * Edge case where selector is unknown.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetDoLaunchUnknownSelector() throws Exception {
    selectors = new String[] { "unknownSelector" };
    when(requestPathInfo.getSelectors()).thenReturn(selectors);

    liteBasicLTIConsumerServlet.doGet(request, response);

    verify(response).sendError(eq(HttpServletResponse.SC_NOT_IMPLEMENTED), anyString());
  }

  /**
   * Edge case where extension is unknown.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetDoLaunchUnknownExtension() throws Exception {
    setUpLaunchUseCase();
    when(requestPathInfo.getExtension()).thenReturn("unkownExtension");

    liteBasicLTIConsumerServlet.doGet(request, response);

    verify(response).sendError(eq(HttpServletResponse.SC_NOT_IMPLEMENTED), anyString());
  }

  /**
   * Edge case where selector is null.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetNullSelector() throws Exception {
    selectors = null;
    when(requestPathInfo.getSelectors()).thenReturn(selectors);

    liteBasicLTIConsumerServlet.doGet(request, response);
    verifyRenderedJson(false);
  }

  /**
   * Edge case where extension is empty.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetEmptySelector() throws Exception {
    selectors = new String[] {};
    when(requestPathInfo.getSelectors()).thenReturn(selectors);

    liteBasicLTIConsumerServlet.doGet(request, response);
    verifyRenderedJson(false);
  }

  /**
   * Edge case where extension is unknown.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetUnknownExtension() throws Exception {
    when(requestPathInfo.getExtension()).thenReturn("unkownExtension");

    liteBasicLTIConsumerServlet.doGet(request, response);

    verify(response).sendError(eq(HttpServletResponse.SC_NOT_IMPLEMENTED), anyString());
  }

  /**
   * Edge case where extension is null.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetNullExtension() throws Exception {
    when(requestPathInfo.getExtension()).thenReturn(null);

    liteBasicLTIConsumerServlet.doGet(request, response);
    verifyRenderedJson(false);
  }

  /**
   * Edge case where pooled content node cannot be found.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetDoLaunchPooledContentNodeNotFound() throws Exception {
    setUpLaunchUseCase();
    when(userContentManager.get(anyString())).thenReturn(null);

    liteBasicLTIConsumerServlet.doGet(request, response);

    verify(response).sendError(eq(HttpServletResponse.SC_INTERNAL_SERVER_ERROR),
        anyString());
  }

  /**
   * Edge case where the context id is null.
   * {@link LiteBasicLTIConsumerServlet#doGet(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoGetDoLaunchContextIdNull() throws Exception {
    setUpLaunchUseCase();
    liteBasicLTIConsumerServlet.contextIdResolver = mockContextIdResolver;

    liteBasicLTIConsumerServlet.doGet(request, response);

    verify(response).sendError(eq(HttpServletResponse.SC_INTERNAL_SERVER_ERROR),
        anyString());
  }

  /**
   * Happy case. {@link LiteBasicLTIConsumerServlet#getLaunchSettings(Content)}
   * 
   * @throws Exception
   */
  @Test
  public void testGetLaunchSettingsWithSensitiveData() throws Exception {
    contentProperties.put(LTI_KEY, _12345);
    contentProperties.put(LTI_SECRET, SECRET);
    when(content.getProperties()).thenReturn(contentProperties);

    final Map<String, Object> properties = liteBasicLTIConsumerServlet
        .getLaunchSettings(content);
    assertFalse(properties.containsKey(LTI_KEY));
    assertFalse(properties.containsKey(LTI_SECRET));
  }

  /**
   * Happy case.
   * {@link LiteBasicLTIConsumerServlet#doPut(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoPut() throws Exception {
    liteBasicLTIConsumerServlet.doPut(request, response);
    verify(response)
        .sendError(eq(HttpServletResponse.SC_METHOD_NOT_ALLOWED), anyString());
  }

  /**
   * Happy case.
   * {@link LiteBasicLTIConsumerServlet#doDelete(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoDelete() throws Exception {
    when(adminContentManager.exists(eq(adminContentPath))).thenReturn(true);

    liteBasicLTIConsumerServlet.doDelete(request, response);

    verify(sparseRepository).loginAdministrative();
    verify(adminContentManager).exists(eq(adminContentPath));
    verify(adminContentManager).delete(eq(adminContentPath));
    verify(adminSession).logout();
    verify(userContentManager).delete(eq(contentPath));
    // TODO make this event check more specific
    verify(eventAdmin).postEvent(any(Event.class));
    verify(response).setStatus(HttpServletResponse.SC_OK);
  }

  /**
   * Negative case where permission to delete is denied.
   * {@link LiteBasicLTIConsumerServlet#doDelete(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoDeletePermissionDenied() throws Exception {
    when(
        accessControlManager.can(eq(authorizable), anyString(), anyString(),
            eq(Permissions.CAN_DELETE))).thenReturn(false);

    when(adminContentManager.exists(eq(adminContentPath))).thenReturn(false);

    liteBasicLTIConsumerServlet.doDelete(request, response);

    verify(sparseRepository, never()).loginAdministrative();
    verify(adminContentManager, never()).exists(eq(adminContentPath));
    verify(adminContentManager, never()).delete(eq(adminContentPath));
    verify(adminSession, never()).logout();
    verify(userContentManager, never()).delete(eq(contentPath));
    verify(eventAdmin, never()).postEvent(any(Event.class));
    verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
    verify(response, never()).setStatus(HttpServletResponse.SC_OK);
  }

  /**
   * Edge case where the resource cannot be found.
   * {@link LiteBasicLTIConsumerServlet#doDelete(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoDeleteResourceNotFound() throws Exception {
    when(request.getResource()).thenReturn(null);

    liteBasicLTIConsumerServlet.doDelete(request, response);

    verify(sparseRepository, never()).loginAdministrative();
    verify(adminContentManager, never()).exists(eq(adminContentPath));
    verify(adminContentManager, never()).delete(eq(adminContentPath));
    verify(adminSession, never()).logout();
    verify(userContentManager, never()).delete(eq(contentPath));
    verify(eventAdmin, never()).postEvent(any(Event.class));
    verify(response).sendError(eq(HttpServletResponse.SC_NOT_FOUND), anyString());
    verify(response, never()).setStatus(HttpServletResponse.SC_OK);
  }

  /**
   * Edge case where the resource is found but content is null.
   * {@link LiteBasicLTIConsumerServlet#doDelete(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoDeleteContentNotFound() throws Exception {
    when(resource.adaptTo(Content.class)).thenReturn(null);

    liteBasicLTIConsumerServlet.doDelete(request, response);

    verify(sparseRepository, never()).loginAdministrative();
    verify(adminContentManager, never()).exists(eq(adminContentPath));
    verify(adminContentManager, never()).delete(eq(adminContentPath));
    verify(adminSession, never()).logout();
    verify(userContentManager, never()).delete(eq(contentPath));
    verify(eventAdmin, never()).postEvent(any(Event.class));
    verify(response).sendError(eq(HttpServletResponse.SC_NOT_FOUND), anyString());
    verify(response, never()).setStatus(HttpServletResponse.SC_OK);
  }

  /**
   * Edge case where an exception is thrown.
   * {@link LiteBasicLTIConsumerServlet#doDelete(SlingHttpServletRequest, SlingHttpServletResponse)}
   * 
   * @throws Exception
   */
  @Test
  public void testDoDeleteThrownException() throws Exception {
    doThrow(StorageClientException.class).when(userContentManager).delete(anyString());

    liteBasicLTIConsumerServlet.doDelete(request, response);

    verify(eventAdmin, never()).postEvent(any(Event.class));
    verify(response).sendError(eq(HttpServletResponse.SC_INTERNAL_SERVER_ERROR),
        anyString());
    verify(response, never()).setStatus(HttpServletResponse.SC_OK);
  }

  /**
   * Edge case where a null argument is passed to method.
   * {@link LiteBasicLTIConsumerServlet#effectiveSetting(String, Map, Map, Map)}
   */
  @Test(expected = IllegalArgumentException.class)
  public void testEffectiveSettingNullArgument1() {
    Map<String, Object> effectiveSettings = new HashMap<String, Object>();
    liteBasicLTIConsumerServlet.effectiveSetting(null, effectiveSettings,
        adminContentProperties, contentProperties);
  }

  /**
   * Edge case where a null argument is passed to method.
   * {@link LiteBasicLTIConsumerServlet#effectiveSetting(String, Map, Map, Map)}
   */
  @Test(expected = IllegalArgumentException.class)
  public void testEffectiveSettingNullArgument2() {
    liteBasicLTIConsumerServlet.effectiveSetting(SECRET, null, adminContentProperties,
        contentProperties);
  }

  /**
   * Edge case where a null argument is passed to method.
   * {@link LiteBasicLTIConsumerServlet#effectiveSetting(String, Map, Map, Map)}
   */
  @Test(expected = IllegalArgumentException.class)
  public void testEffectiveSettingNullArgument3() {
    Map<String, Object> effectiveSettings = new HashMap<String, Object>();
    liteBasicLTIConsumerServlet.effectiveSetting(SECRET, effectiveSettings, null,
        contentProperties);
  }

  /**
   * Edge case where a null argument is passed to method.
   * {@link LiteBasicLTIConsumerServlet#effectiveSetting(String, Map, Map, Map)}
   */
  @Test(expected = IllegalArgumentException.class)
  public void testEffectiveSettingNullArgument4() {
    Map<String, Object> effectiveSettings = new HashMap<String, Object>();
    liteBasicLTIConsumerServlet.effectiveSetting(SECRET, effectiveSettings,
        adminContentProperties, null);
  }

  /**
   * Edge case where the response is already committed.
   * {@link LiteBasicLTIConsumerServlet#sendError(int, String, Throwable, HttpServletResponse)}
   */
  @Test(expected = Error.class)
  public void testSendErrorResponseAlreadyCommitted() {
    when(response.isCommitted()).thenReturn(true);

    liteBasicLTIConsumerServlet.sendError(HttpServletResponse.SC_CONFLICT, "message",
        new Throwable(), response);
  }

  /**
   * Edge case where an exception is thrown.
   * {@link LiteBasicLTIConsumerServlet#sendError(int, String, Throwable, HttpServletResponse)}
   */
  @Test(expected = Error.class)
  public void testSendErrorResponseIOException() throws Exception {
    doThrow(IOException.class).when(response).sendError(anyInt(), anyString());
    doThrow(IOException.class).when(response).sendError(anyInt());

    liteBasicLTIConsumerServlet.sendError(HttpServletResponse.SC_CONFLICT, "message",
        new Throwable(), response);
  }

  // --------------------------------------------------------------------------

  private void verifyHtml() {
    // TODO make this event check more specific
    verify(eventAdmin).postEvent(any(Event.class));
    verify(response).setContentType(eq("text/html"));
    verify(response).setStatus(HttpServletResponse.SC_OK);
    verify(writer, atLeastOnce()).write(anyString());
    if (withinWorld) {
      verify(writer, times(1))
          .write(contains("name=\"context_id\" value=\"qwerty1234\""));
      if (externalCourseId) {
        verify(writer, times(1)).write(
            contains("name=\"custom_external_course_id\" value=\"SIS-101-001\""));
      }
    } else { // outside world use case
      verify(writer, times(1)).write(
          contains("name=\"context_id\" value=\"/p/j25KqM5SU4\""));
      verify(writer, never()).write(contains("name=\"custom_external_course_id\""));
    }
    if (widgetUseCase) {
      verify(writer, times(1)).write(
          contains("form action=\"http://dr-chuck.com/ims/php-simple/tool.php\""));
    } else {
      verify(writer, times(1))
          .write(
              contains("form action=\"http://localhost/imsblti/provider/sakai.gradebook.gwt.rpc\""));
    }
    verify(writer, times(1)).write(
        contains("name=\"context_label\" value=\"pooledContentDescription\""));
    verify(writer, times(1)).write(
        contains("name=\"context_title\" value=\"pooledContentFileName\""));
    verify(writer, times(1)).write(contains("name=\"context_type\" value=\"course\""));
    verify(writer, times(1))
        .write(
            contains("name=\"custom_complex____________key\" value=\"Complex!@#$^*(){}[]Value\""));
    verify(writer, times(1)).write(
        contains("name=\"custom_simple_key\" value=\"custom_simple_value\""));
    verify(writer, times(1)).write(
        contains("name=\"launch_presentation_document_target\" value=\"iframe\""));
    if (hasEmail) {
      if (releaseEmail) {
        verify(writer, times(1))
            .write(
                contains("name=\"lis_person_contact_email_primary\" value=\"lance@foo.org\""));
      } else {
        verify(writer, never())
            .write(
                contains("name=\"lis_person_contact_email_primary\" value=\"lance@foo.org\""));
        verify(writer, never()).write(contains("lance@foo.org"));
      }
    } else {
      verify(writer, never())
          .write(contains("name=\"lis_person_contact_email_primary\""));
      verify(writer, never()).write(contains("lance@foo.org"));
    }
    if (releaseNames) {
      if (hasFirstName) {
        verify(writer, times(1)).write(
            contains("name=\"lis_person_name_given\" value=\"Lance\""));
      } else {
        verify(writer, never()).write(contains("name=\"lis_person_name_given\""));
      }
      if (hasLastName) {
        verify(writer, times(1)).write(
            contains("name=\"lis_person_name_family\" value=\"Speelmon\""));
      } else {
        verify(writer, never()).write(contains("name=\"lis_person_name_family\""));
      }
      if (hasFirstName && hasLastName) {
        verify(writer, times(1)).write(
            contains("name=\"lis_person_name_full\" value=\"Lance Speelmon\""));
      }
      if (hasFirstName && !hasLastName) {
        verify(writer, times(1)).write(
            contains("name=\"lis_person_name_full\" value=\"Lance\""));
      }
      if (!hasFirstName && hasLastName) {
        verify(writer, times(1)).write(
            contains("name=\"lis_person_name_full\" value=\"Speelmon\""));
      }
    } else {
      verify(writer, never()).write(contains("name=\"lis_person_name_family\""));
      verify(writer, never()).write(contains("name=\"lis_person_name_full\""));
      verify(writer, never()).write(contains("name=\"lis_person_name_given\""));
    }
    verify(writer, times(1)).write(
        contains("name=\"lti_message_type\" value=\"basic-lti-launch-request\""));
    verify(writer, times(1)).write(contains("name=\"lti_version\" value=\"LTI-1p0\""));
    verify(writer, times(1)).write(
        contains("name=\"oauth_callback\" value=\"about:blank\""));
    verify(writer, times(1)).write(
        contains("name=\"oauth_consumer_key\" value=\"12345\""));
    verify(writer, times(1)).write(contains("name=\"oauth_nonce\" value="));
    verify(writer, times(1)).write(contains("name=\"oauth_signature\" value="));
    verify(writer, times(1)).write(
        contains("name=\"oauth_signature_method\" value=\"HMAC-SHA1\""));
    verify(writer, times(1)).write(contains("name=\"oauth_timestamp\" value="));
    verify(writer, times(1)).write(contains("name=\"oauth_version\" value=\"1.0\""));
    verify(writer, times(1))
        .write(
            contains("name=\"resource_link_id\" value=\"mHAY1acZec/id470170/id7577541/sakai2gradebook\""));
    if (canManageContentPool) {
      verify(writer, times(1)).write(contains("name=\"roles\" value=\"Instructor\""));
      verify(writer, never()).write(contains("name=\"roles\" value=\"Student\""));
    } else {
      if (anonymous) {
        verify(writer, times(1)).write(contains("name=\"roles\" value=\"None\""));
        verify(writer, never()).write(contains("name=\"roles\" value=\"Instructor\""));
      } else {
        verify(writer, times(1)).write(contains("name=\"roles\" value=\"Student\""));
        verify(writer, never()).write(contains("name=\"roles\" value=\"Instructor\""));
      }
    }
    verify(writer, times(1))
        .write(
            contains("name=\"tool_consumer_instance_contact_email\" value=\"admin@sakaiproject.org\""));
    verify(writer, times(1))
        .write(
            contains("name=\"tool_consumer_instance_description\" value=\"The Sakai Project\""));
    verify(writer, times(1)).write(
        contains("name=\"tool_consumer_instance_guid\" value=\"sakaiproject.org\""));
    verify(writer, times(1)).write(
        contains("name=\"tool_consumer_instance_name\" value=\"Sakai Development\""));
    verify(writer, times(1))
        .write(
            contains("name=\"tool_consumer_instance_url\" value=\"http://sakaiproject.org\""));
    if (releasePrincipalName) {
      if (anonymous) {
        verify(writer, times(1)).write(contains("name=\"user_id\" value=\"anonymous\""));
      } else {
        verify(writer, times(1)).write(contains("name=\"user_id\" value=\"lance\""));
      }
    } else {
      verify(writer, never()).write(contains("name=\"user_id\""));
    }
    if (debugEnabled) {
      verify(writer, times(1)).write(contains("name=\"custom_debug\" value=\"true\""));
    } else {
      verify(writer, times(1)).write(contains("name=\"custom_debug\" value=\"false\""));
    }
    // KERN-2890 Add timezone and locale support to LTI consumer launch payloads
    final TimeZone tz = TimeZone.getDefault();
    verify(writer, times(1)).write(
        contains("name=\"launch_presentation_locale\" value=\"en_US\""));
    verify(writer, times(1)).write(
        contains("name=\"ext_launch_presentation_locale_iso3\" value=\"eng_USA\""));
    verify(writer, times(1)).write(
        contains("name=\"ext_tz\" value=\"" + tz.getID() + "\""));
    verify(writer, times(1)).write(
        contains("name=\"ext_timezone\" value=\"" + tz.getID() + "\""));
    verify(writer, times(1)).write(
        contains("name=\"ext_tz_offset\" value=\"" + localeUtils.getOffset(tz) + "\""));
    verify(writer, times(1)).write(
        contains("name=\"ext_timezone_offset\" value=\"" + localeUtils.getOffset(tz)
            + "\""));
  }

  private void verifyRenderedJson(final boolean shouldIncludeAdminContent)
      throws Exception {
    verify(response).setContentType(eq("application/json"));
    verify(writer, atLeastOnce()).write(anyString());
    for (final Entry<String, Object> entry : contentProperties.entrySet()) {
      verify(writer, times(1)).write(contains("\"" + entry.getKey() + "\""));
      verify(writer, atLeastOnce()).write(contains(String.valueOf(entry.getValue())));
    }
    if (shouldIncludeAdminContent) {
      verify(sparseRepository).loginAdministrative();
      verify(adminContentManager).exists(eq(adminContentPath));
      verify(adminSession).logout();
      verify(writer, times(1)).write(contains("\"ltikey\""));
      verify(writer, times(1)).write(contains(_12345));
      verify(writer, times(1)).write(contains("\"ltisecret\""));
      verify(writer, times(1)).write(contains(SECRET));
    } else {
      verify(writer, never()).write(contains("\"ltikey\""));
      verify(writer, never()).write(contains(_12345));
      verify(writer, never()).write(contains("\"ltisecret\""));
      verify(writer, never()).write(contains(SECRET));
    }
  }

  private void setupProperties() {
    contentProperties = new HashMap<String, Object>();
    contentProperties.put("lti_virtual_tool_id", SAKAI_GRADEBOOK_GWT_RPC);
    contentProperties.put("_id", "hnfnEKqGEeGLB4EWCjQLZA");
    contentProperties.put("_path", "j25KqM5SU4/id7339024/sakai2gradebook");
    contentProperties.put("_charset_", "utf-8");
    contentProperties.put("_lastModified", new Long(1338403055617L));
    contentProperties.put("_lastModifiedBy", CURRENT_USER_ID);
    contentProperties.put("_created", new Long(1338403055618L));
    contentProperties.put("_createdBy", CURRENT_USER_ID);
    contentProperties.put("sling:resourceType", "sakai/basiclti");

    adminContentProperties = new HashMap<String, Object>();
    adminContentProperties.put(LTI_KEY, _12345);
    adminContentProperties.put(LTI_SECRET, SECRET);
    adminContentProperties.put("_id", "sbsa8FzeEeGgtwV2CjQLZA");
    adminContentProperties.put("_path", "j25KqM5SU4/id7339024/sakai2gradebook/ltiKeys");
    adminContentProperties.put("_lastModified", new Long(1338403055619L));
    adminContentProperties.put("_lastModifiedBy", ADMIN);
    adminContentProperties.put("_created", new Long(1329864733215L));
    adminContentProperties.put("_createdBy", ADMIN);
  }
}
