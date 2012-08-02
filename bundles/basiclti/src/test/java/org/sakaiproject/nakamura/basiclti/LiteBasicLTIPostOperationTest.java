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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sakaiproject.nakamura.api.basiclti.BasicLTIAppConstants.LTI_KEY;
import static org.sakaiproject.nakamura.api.basiclti.BasicLTIAppConstants.LTI_SECRET;
import static org.sakaiproject.nakamura.basiclti.LiteBasicLTIServletUtils.sensitiveKeys;
import static org.sakaiproject.nakamura.basiclti.LiteBasicLTIServletUtils.unsupportedKeys;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.request.RequestParameterMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HtmlResponse;
import org.apache.sling.servlets.post.Modification;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.SessionAdaptable;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification.Operation;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permission;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.resource.lite.SparseNonExistingResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

@RunWith(MockitoJUnitRunner.class)
public class LiteBasicLTIPostOperationTest {
  private static final String LONG_PROP = "longProp";
  private static final String TYPE_HINT = "@TypeHint";
  private static final String OPERATION = ":operation";
  private static final String SAKAI_BASICLTI = "sakai/basiclti";
  private static final String FALSE = "false";
  private static final String TRUE = "true";
  private static final String BOOLEAN = "Boolean";
  private static final String CONTENT_TYPE = ":contentType";
  private static final String COLON_CONTENT = ":content";
  private static final String DEBUG = "debug";
  private static final String SLING_RESOURCE_TYPE = "sling:resourceType";
  private static final String WIDTH_UNIT = "width_unit";
  private static final String WIDTH = "width";
  private static final String BORDER_COLOR = "border_color";
  private static final String BORDER_SIZE = "border_size";
  private static final String RELEASE_EMAIL = "release_email";
  private static final String RELEASE_PRINCIPAL_NAME = "release_principal_name";
  private static final String RELEASE_NAMES = "release_names";
  private static final String LTISECRET = "ltisecret";
  private static final String LTIKEY = "ltikey";
  private static final String LTI_URL_KEY = "ltiurl";
  private static final String LTI_URL_VALUE = "http://dr-chuck.com/ims/php-simple/tool.php";
  LiteBasicLTIPostOperation liteBasicLTIPostOperation;
  String sensitiveNodePath = "/foo/bar/baz";
  String currentUserId = "lance";
  Map<String, String> sensitiveData = new HashMap<String, String>(sensitiveKeys.size());
  Permission[] userPrivs;
  final String adminUserId = User.ADMIN_USER;
  List<Modification> changes;
  String contentPath;
  String content;
  SparseNonExistingResource nonExistingResource;
  Map<String, RequestParameter[]> map;

  @Mock
  Repository repository;
  @Mock
  EventAdmin eventAdmin;
  @Mock
  Session adminSession;
  @Mock
  Session userSession;
  @Mock
  AccessControlManager accessControlManager;
  @Mock
  Content parent;
  @Mock
  ContentManager adminContentManager;
  @Mock
  SlingHttpServletRequest request;
  @SuppressWarnings("deprecation")
  @Mock
  HtmlResponse response;
  @Mock
  ContentManager contentManager;
  @Mock
  ResourceResolver resourceResolver;
  @Mock(extraInterfaces = { SessionAdaptable.class })
  javax.jcr.Session jcrSession;
  @Mock
  Resource resource;
  @Mock
  Content node;
  @Mock
  RequestParameterMap requestParameterMap;
  @Mock
  RequestParameter requestParameterOperation;
  @Mock
  RequestParameter requestParameterReplaceProperties;
  @Mock
  RequestParameter requestParameterReplace;
  @Mock
  RequestParameter requestParameterCharset;
  @Mock
  RequestParameter requestParameterContentType;
  @Mock
  RequestParameter requestParameterContent;
  @Mock
  Resource wrappedResource;
  @Mock
  RequestParameter requestParameterSlingResourceType;
  @Mock
  RequestParameter requestParameterLtiUrl;
  @Mock
  RequestParameter requestParameterLtiKey;
  @Mock
  RequestParameter requestParameterLtiSecret;
  @Mock
  RequestParameter requestParameterDebugTypeHint;
  @Mock
  RequestParameter requestParameterDebug;
  @Mock
  RequestParameter requestParameterReleaseNames;
  @Mock
  RequestParameter requestParameterReleaseNamesTypeHint;
  @Mock
  RequestParameter requestParameterReleasePrincipalName;
  @Mock
  RequestParameter requestParameterReleasePrincipalNameTypeHint;
  @Mock
  RequestParameter requestParameterReleaseEmail;
  @Mock
  RequestParameter requestParameterReleaseEmailTypeHint;
  @Mock
  RequestParameter requestParameterBorderSize;
  @Mock
  RequestParameter requestParameterBorderColor;
  @Mock
  RequestParameter requestParameterWidth;
  @Mock
  RequestParameter requestParameterWidthUnit;
  @Mock
  RequestParameter requestParameterPropertyToRemove;
  @Mock
  RequestParameter requestParameterLongProp;
  @Mock
  RequestParameter requestParameterLongPropTypeHint;

  @Before
  public void setUp() throws Exception {
    liteBasicLTIPostOperation = new LiteBasicLTIPostOperation();
    liteBasicLTIPostOperation.repository = repository;
    liteBasicLTIPostOperation.eventAdmin = eventAdmin;
    when(adminSession.getAccessControlManager()).thenReturn(accessControlManager);
    when(parent.getPath()).thenReturn(sensitiveNodePath);
    when(repository.loginAdministrative()).thenReturn(adminSession);
    when(adminSession.getContentManager()).thenReturn(adminContentManager);
    when(userSession.getAccessControlManager()).thenReturn(accessControlManager);
    sensitiveData.put(LTI_KEY, "ltiKey");
    sensitiveData.put(LTI_SECRET, "ltiSecret");
    when(userSession.getUserId()).thenReturn(currentUserId);
    userPrivs = new Permission[] {};
    when(accessControlManager.getPermissions(eq(Security.ZONE_CONTENT), anyString()))
        .thenReturn(userPrivs);
    when(request.getResourceResolver()).thenReturn(resourceResolver);
    when(resourceResolver.adaptTo(javax.jcr.Session.class)).thenReturn(jcrSession);
    when(((SessionAdaptable) jcrSession).getSession()).thenReturn(userSession);
    contentPath = "/foo/bar/baz/id1234";
    when(wrappedResource.adaptTo(Content.class)).thenReturn((Content) null);
    nonExistingResource = new SparseNonExistingResource(wrappedResource, contentPath,
        userSession, contentManager);
    when(request.getResource()).thenReturn(nonExistingResource);
    changes = new ArrayList<Modification>();
    content = "{" + "\"ltiurl\":\"http://dr-chuck.com/ims/php-simple/tool.php\","
        + "\"ltikey\":\"12345\"," + "\"ltisecret\":\"secret\","
        + "\"release_names\":true," + "\"release_principal_name\":true,"
        + "\"release_email\":true," + "\"border_size\":0," + "\"border_color\":\"ccc\","
        + "\"width\":100," + "\"width_unit\":\"%\"," + "\"isSakai2Tool\":false,"
        + "\"defined\":\"\"," + "\"sling:resourceType\":\"sakai/basiclti\","
        + "\"debug@TypeHint\":\"Boolean\"," + "\"debug\":false,"
        + "\"release_names@TypeHint\":\"Boolean\","
        + "\"release_principal_name@TypeHint\":\"Boolean\","
        + "\"release_email@TypeHint\":\"Boolean\"," + "\"launchDataUrl\":\"\","
        + "\"tuidFrame\":\"\"," + "\"nullProperty\":null,"
        + "\"jcr:primaryType\":\"nt:folder\"," + "\"longProp\": 99999999999999,"
        + "\"longProp@TypeHint\": \"Long\"," + '}';
    when(request.getRequestParameterMap()).thenReturn(requestParameterMap);
    map = new HashMap<String, RequestParameter[]>();
    when(requestParameterOperation.getString(eq("UTF-8"))).thenReturn("basiclti");
    when(requestParameterReplaceProperties.getString(eq("UTF-8"))).thenReturn(TRUE);
    when(requestParameterReplace.getString(eq("UTF-8"))).thenReturn(TRUE);
    when(requestParameterCharset.getString(eq("UTF-8"))).thenReturn("utf-8");
    when(requestParameterContentType.getString(eq("UTF-8"))).thenReturn("json");
    when(requestParameterContent.getString(eq("UTF-8"))).thenReturn(content);
    when(requestParameterPropertyToRemove.getString(eq("UTF-8"))).thenReturn("");

    map.put(OPERATION, new RequestParameter[] { requestParameterOperation });
    map.put(":replaceProperties",
        new RequestParameter[] { requestParameterReplaceProperties });
    map.put(":replace", new RequestParameter[] { requestParameterReplace });
    map.put("_charset_", new RequestParameter[] { requestParameterCharset });
    map.put(SLING_RESOURCE_TYPE,
        new RequestParameter[] { requestParameterSlingResourceType });
    map.put(LTI_URL_KEY, new RequestParameter[] { requestParameterLtiUrl });
    map.put(LTI_KEY, new RequestParameter[] { requestParameterLtiKey });
    map.put(LTI_SECRET, new RequestParameter[] { requestParameterLtiSecret });
    map.put(BORDER_SIZE, new RequestParameter[] { requestParameterBorderSize });
    map.put(BORDER_COLOR, new RequestParameter[] { requestParameterBorderColor });
    map.put(WIDTH, new RequestParameter[] { requestParameterWidth });
    map.put(WIDTH_UNIT, new RequestParameter[] { requestParameterWidthUnit });
    map.put("propertyToRemove",
        new RequestParameter[] { requestParameterPropertyToRemove });
    map.put("emptyProperty", new RequestParameter[] {});
    map.put("nullProperty", null);
    // add an unsupported key
    map.put((String) unsupportedKeys.toArray()[0],
        new RequestParameter[] { requestParameterDebug });

    map.put(DEBUG, new RequestParameter[] { requestParameterDebug });
    map.put(DEBUG + TYPE_HINT, new RequestParameter[] { requestParameterDebugTypeHint });
    when(requestParameterMap.get(eq(DEBUG + TYPE_HINT))).thenReturn(
        new RequestParameter[] { requestParameterDebugTypeHint });
    when(requestParameterMap.containsKey(eq(DEBUG + TYPE_HINT))).thenReturn(true);

    map.put(LONG_PROP, new RequestParameter[] { requestParameterLongProp });
    map.put(LONG_PROP + TYPE_HINT,
        new RequestParameter[] { requestParameterLongPropTypeHint });
    when(requestParameterMap.get(eq(LONG_PROP + TYPE_HINT))).thenReturn(
        new RequestParameter[] { requestParameterLongPropTypeHint });
    when(requestParameterMap.containsKey(eq(LONG_PROP + TYPE_HINT))).thenReturn(true);
    when(requestParameterLongProp.getString(eq("UTF-8"))).thenReturn("99999999999999");
    when(requestParameterLongPropTypeHint.getString(eq("UTF-8"))).thenReturn("Long");

    map.put(RELEASE_NAMES, new RequestParameter[] { requestParameterReleaseNames });
    map.put(RELEASE_NAMES + TYPE_HINT,
        new RequestParameter[] { requestParameterReleaseNamesTypeHint });
    when(requestParameterMap.get(eq(RELEASE_NAMES + TYPE_HINT))).thenReturn(
        new RequestParameter[] { requestParameterReleaseNamesTypeHint });
    when(requestParameterMap.containsKey(eq(RELEASE_NAMES + TYPE_HINT))).thenReturn(true);

    map.put(RELEASE_PRINCIPAL_NAME,
        new RequestParameter[] { requestParameterReleasePrincipalName });
    map.put(RELEASE_PRINCIPAL_NAME + TYPE_HINT,
        new RequestParameter[] { requestParameterReleasePrincipalNameTypeHint });
    when(requestParameterMap.get(eq(RELEASE_PRINCIPAL_NAME + TYPE_HINT))).thenReturn(
        new RequestParameter[] { requestParameterReleasePrincipalNameTypeHint });
    when(requestParameterMap.containsKey(eq(RELEASE_PRINCIPAL_NAME + TYPE_HINT)))
        .thenReturn(true);

    map.put(RELEASE_EMAIL, new RequestParameter[] { requestParameterReleaseEmail });
    map.put(RELEASE_EMAIL + TYPE_HINT,
        new RequestParameter[] { requestParameterReleaseEmailTypeHint });
    when(requestParameterMap.get(eq(RELEASE_EMAIL + TYPE_HINT))).thenReturn(
        new RequestParameter[] { requestParameterReleaseEmailTypeHint });
    when(requestParameterMap.containsKey(eq(RELEASE_EMAIL + TYPE_HINT))).thenReturn(true);

    map.put(CONTENT_TYPE, new RequestParameter[] { requestParameterContentType });
    map.put(COLON_CONTENT, new RequestParameter[] { requestParameterContent });
    when(requestParameterMap.entrySet()).thenReturn(map.entrySet());
    when(request.getParameter(COLON_CONTENT)).thenReturn(content);
    when(request.getParameter(CONTENT_TYPE)).thenReturn("json");

    when(request.getRemoteUser()).thenReturn(currentUserId);

    when(requestParameterSlingResourceType.getString(eq("UTF-8"))).thenReturn(
        SAKAI_BASICLTI);
    when(requestParameterLtiUrl.getString(eq("UTF-8"))).thenReturn(LTI_URL_VALUE);
    when(requestParameterLtiKey.getString(eq("UTF-8"))).thenReturn("12345");
    when(requestParameterLtiSecret.getString(eq("UTF-8"))).thenReturn("secret");
    when(requestParameterDebugTypeHint.getString(eq("UTF-8"))).thenReturn(BOOLEAN);
    when(requestParameterDebug.getString(eq("UTF-8"))).thenReturn(FALSE);
    when(requestParameterReleaseNames.getString(eq("UTF-8"))).thenReturn(TRUE);
    when(requestParameterReleaseNamesTypeHint.getString(eq("UTF-8"))).thenReturn(BOOLEAN);
    when(requestParameterReleasePrincipalName.getString(eq("UTF-8"))).thenReturn(TRUE);
    when(requestParameterReleasePrincipalNameTypeHint.getString(eq("UTF-8"))).thenReturn(
        BOOLEAN);
    when(requestParameterReleaseEmail.getString(eq("UTF-8"))).thenReturn(TRUE);
    when(requestParameterReleaseEmailTypeHint.getString(eq("UTF-8"))).thenReturn(BOOLEAN);
    when(requestParameterBorderSize.getString(eq("UTF-8"))).thenReturn("0");
    when(requestParameterBorderColor.getString(eq("UTF-8"))).thenReturn("ccc");
    when(requestParameterWidth.getString(eq("UTF-8"))).thenReturn("100");
    when(requestParameterWidthUnit.getString(eq("UTF-8"))).thenReturn("%");
  }

  /**
   * Validate happy case for
   * {@link LiteBasicLTIPostOperation#accessControlSensitiveNode(String, Session, String)}
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  @Test
  public void testAccessControlSensitiveNode() throws StorageClientException,
      AccessDeniedException {
    liteBasicLTIPostOperation.accessControlSensitiveNode(sensitiveNodePath, adminSession,
        currentUserId);
    verifyAclModification();
  }

  /**
   * Happy case where everything goes as expected.
   * {@link LiteBasicLTIPostOperation#createSensitiveNode(Content, Session, Map)}
   * 
   * @throws Exception
   */
  @Test
  public void testCreateSensitiveNode() throws Exception {
    liteBasicLTIPostOperation.createSensitiveNode(parent, userSession, sensitiveData);
    verify(adminContentManager, atLeastOnce()).update(any(Content.class));
    verify(adminSession, times(1)).logout();
    verifyAclModification();
  }

  /**
   * Edge case where deny acls were applied but the sanity check fails.
   * {@link LiteBasicLTIPostOperation#createSensitiveNode(Content, Session, Map)}
   * 
   * @throws Exception
   */
  @Test(expected = IllegalStateException.class)
  public void testCreateSensitiveNodeFailedAclModification() throws Exception {
    userPrivs = new Permission[] { Permissions.CAN_READ };
    when(accessControlManager.getPermissions(eq(Security.ZONE_CONTENT), anyString()))
        .thenReturn(userPrivs);

    liteBasicLTIPostOperation.createSensitiveNode(parent, userSession, sensitiveData);
  }

  /**
   * Test argument passing null parent node.
   * {@link LiteBasicLTIPostOperation#createSensitiveNode(Content, Session, Map)}
   * 
   * @throws Exception
   */
  @Test(expected = IllegalArgumentException.class)
  public void testCreateSensitiveNodePassNullParent() throws Exception {
    liteBasicLTIPostOperation.createSensitiveNode(null, userSession, sensitiveData);
  }

  /**
   * Test argument passing null userSession.
   * {@link LiteBasicLTIPostOperation#createSensitiveNode(Content, Session, Map)}
   * 
   * @throws Exception
   */
  @Test(expected = IllegalArgumentException.class)
  public void testCreateSensitiveNodePassUserSession() throws Exception {
    liteBasicLTIPostOperation.createSensitiveNode(parent, null, sensitiveData);
  }

  /**
   * Test argument passing null sensitiveData.
   * {@link LiteBasicLTIPostOperation#createSensitiveNode(Content, Session, Map)}
   * 
   * @throws Exception
   */
  @Test
  public void testCreateSensitiveNodePassNullSensitiveData() throws Exception {
    liteBasicLTIPostOperation.createSensitiveNode(parent, userSession, null);
    verify(adminContentManager, never()).update(any(Content.class));
    verify(adminSession, never()).logout();
  }

  /**
   * Test argument passing empty sensitiveData.
   * {@link LiteBasicLTIPostOperation#createSensitiveNode(Content, Session, Map)}
   * 
   * @throws Exception
   */
  @Test
  public void testCreateSensitiveNodePassEmptySensitiveData() throws Exception {
    sensitiveData.clear();
    liteBasicLTIPostOperation.createSensitiveNode(parent, userSession, sensitiveData);
    verify(adminContentManager, never()).update(any(Content.class));
    verify(adminSession, never()).logout();
  }

  /**
   * Test exception handling for AccessDeniedException.
   * {@link LiteBasicLTIPostOperation#createSensitiveNode(Content, Session, Map)}
   * 
   * @throws Exception
   */
  @Test(expected = IllegalStateException.class)
  public void testCreateSensitiveNodeWhenAccessDeniedExceptionThrown() throws Exception {
    doThrow(AccessDeniedException.class).when(adminContentManager).update(
        any(Content.class));
    liteBasicLTIPostOperation.createSensitiveNode(parent, userSession, sensitiveData);
  }

  /**
   * Test exception handling for StorageClientException.
   * {@link LiteBasicLTIPostOperation#createSensitiveNode(Content, Session, Map)}
   * 
   * @throws Exception
   */
  @Test(expected = IllegalStateException.class)
  public void testCreateSensitiveNodeWhenStorageClientExceptionThrown() throws Exception {
    doThrow(StorageClientException.class).when(adminContentManager).update(
        any(Content.class));
    liteBasicLTIPostOperation.createSensitiveNode(parent, userSession, sensitiveData);
  }

  /**
   * Test exception handling for ClientPoolException.
   * {@link LiteBasicLTIPostOperation#createSensitiveNode(Content, Session, Map)}
   * 
   * @throws Exception
   */
  @Test(expected = IllegalStateException.class)
  public void testCreateSensitiveNodeWhenClientPoolExceptionThrown() throws Exception {
    doThrow(ClientPoolException.class).when(adminSession).logout();
    liteBasicLTIPostOperation.createSensitiveNode(parent, userSession, sensitiveData);
  }

  /**
   * Code coverage case when current user could be an admin.
   * {@link LiteBasicLTIPostOperation#createSensitiveNode(Content, Session, Map)}
   * 
   * @throws Exception
   */
  @Test
  public void testCreateSensitiveNodeAsAdminUser() throws Exception {
    when(userSession.getUserId()).thenReturn(adminUserId);

    liteBasicLTIPostOperation.createSensitiveNode(parent, userSession, sensitiveData);
    verify(adminContentManager, atLeastOnce()).update(any(Content.class));
    verify(adminSession, times(1)).logout();
  }

  /**
   * Code coverage case when adminSession could be null.
   * {@link LiteBasicLTIPostOperation#createSensitiveNode(Content, Session, Map)}
   * 
   * @throws Exception
   */
  @Test
  public void testCreateSensitiveNodeNullAdminSession() throws Exception {
    when(repository.loginAdministrative()).thenReturn(null);

    liteBasicLTIPostOperation.createSensitiveNode(parent, userSession, sensitiveData);
    verify(adminContentManager, never()).update(any(Content.class));
    verify(adminSession, never()).logout();
  }

  /**
   * Code coverage case where userPrivs could be null.
   * {@link LiteBasicLTIPostOperation#createSensitiveNode(Content, Session, Map)}
   * 
   * @throws Exception
   */
  @Test
  public void testCreateSensitiveNodeNullUserPrivs() throws Exception {
    userPrivs = null;
    when(accessControlManager.getPermissions(eq(Security.ZONE_CONTENT), anyString()))
        .thenReturn(userPrivs);

    liteBasicLTIPostOperation.createSensitiveNode(parent, userSession, sensitiveData);
    verify(adminContentManager, atLeastOnce()).update(any(Content.class));
    verify(adminSession, times(1)).logout();
    verifyAclModification();
  }

  /**
   * Happy case. Code coverage case where userPrivs are not empty or null but also cannot
   * be matched.
   * {@link LiteBasicLTIPostOperation#createSensitiveNode(Content, Session, Map)}
   * 
   * @throws Exception
   */
  @Test
  public void testCreateSensitiveNodeUserPrivsNotMatched() throws Exception {
    userPrivs = new Permission[] { Permissions.CAN_WRITE_PROPERTY };
    when(accessControlManager.getPermissions(eq(Security.ZONE_CONTENT), anyString()))
        .thenReturn(userPrivs);

    liteBasicLTIPostOperation.createSensitiveNode(parent, userSession, sensitiveData);
    verify(adminContentManager, atLeastOnce()).update(any(Content.class));
    verify(adminSession, times(1)).logout();
    verifyAclModification();
  }

  /**
   * Code coverage case where StorageClientException is thrown but not rethrown; only
   * logged. {@link LiteBasicLTIPostOperation#createSensitiveNode(Content, Session, Map)}
   * 
   * @throws Exception
   */
  @Test
  public void testCreateSensitiveNodeWhenStorageClientExceptionThrown2() throws Exception {
    doThrow(StorageClientException.class).when(accessControlManager).getPermissions(
        anyString(), anyString());

    liteBasicLTIPostOperation.createSensitiveNode(parent, userSession, sensitiveData);
    verify(adminContentManager, atLeastOnce()).update(any(Content.class));
    verify(adminSession).logout();
    verifyAclModification();
  }

  /**
   * As of OAE 1.2 this is now the default use case. See:
   * {@link LiteBasicLTIPostOperation#doRun(SlingHttpServletRequest, HtmlResponse, ContentManager, List, String)}
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   * @throws IOException
   */
  @Test
  public void testDoRunImportSemantics() throws StorageClientException,
      AccessDeniedException, IOException {
    liteBasicLTIPostOperation.doRun(request, response, contentManager, changes,
        contentPath);
    verify(request, times(1)).getParameter(eq(COLON_CONTENT));
    verify(request, times(1)).getParameter(eq(CONTENT_TYPE));
    verifyNode();
  }

  /**
   * Prior to OAE 1.2 this was the happy case. See:
   * {@link LiteBasicLTIPostOperation#doRun(SlingHttpServletRequest, HtmlResponse, ContentManager, List, String)}
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   * @throws IOException
   */
  @Test
  public void testDoRunIndividualProperties() throws StorageClientException,
      AccessDeniedException, IOException {
    when(request.getParameter(COLON_CONTENT)).thenReturn(null);
    when(request.getParameter(CONTENT_TYPE)).thenReturn(null);

    liteBasicLTIPostOperation.doRun(request, response, contentManager, changes,
        contentPath);
    verifyNode();
  }

  /**
   * Edge case test where an operation is called against an existing resource.
   * {@link LiteBasicLTIPostOperation#doRun(SlingHttpServletRequest, HtmlResponse, ContentManager, List, String)}
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   * @throws IOException
   */
  @Test
  public void testDoRunContentManagerFoundPath() throws StorageClientException,
      AccessDeniedException, IOException {
    when(contentManager.exists(eq(contentPath))).thenReturn(true);
    when(contentManager.get(eq(contentPath))).thenReturn(node);

    liteBasicLTIPostOperation.doRun(request, response, contentManager, changes,
        contentPath);
    verify(request, times(1)).getParameter(eq(COLON_CONTENT));
    verify(request, times(1)).getParameter(eq(CONTENT_TYPE));
    verify(node, atLeast(5)).setProperty(anyString(), anyObject());
  }

  /**
   * Code coverage test case where :contentType is NOT "json".
   * {@link LiteBasicLTIPostOperation#doRun(SlingHttpServletRequest, HtmlResponse, ContentManager, List, String)}
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   * @throws IOException
   */
  @Test
  public void testDoRunUnknownContentType() throws StorageClientException,
      AccessDeniedException, IOException {
    when(request.getParameter(CONTENT_TYPE)).thenReturn("garbage");
    when(requestParameterContentType.getString(eq("UTF-8"))).thenReturn("garbage");

    liteBasicLTIPostOperation.doRun(request, response, contentManager, changes,
        contentPath);
    verify(request, times(1)).getParameter(eq(COLON_CONTENT));
    verify(request, times(1)).getParameter(eq(CONTENT_TYPE));
    verifyNode();
  }

  /**
   * Edge case test where an operation is called against an existing resource.
   * {@link LiteBasicLTIPostOperation#doRun(SlingHttpServletRequest, HtmlResponse, ContentManager, List, String)}
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   * @throws IOException
   */
  @Test
  public void testDoRunRequestResource() throws StorageClientException,
      AccessDeniedException, IOException {
    when(request.getResource()).thenReturn(resource);
    when(resource.adaptTo(Content.class)).thenReturn(node);

    liteBasicLTIPostOperation.doRun(request, response, contentManager, changes,
        contentPath);
    verify(request, times(1)).getParameter(eq(COLON_CONTENT));
    verify(request, times(1)).getParameter(eq(CONTENT_TYPE));
    verify(node, atLeast(5)).setProperty(anyString(), anyObject());
  }

  /**
   * Edge case test where an operation is called against an existing resource of an
   * unknown type.
   * {@link LiteBasicLTIPostOperation#doRun(SlingHttpServletRequest, HtmlResponse, ContentManager, List, String)}
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   * @throws IOException
   */
  @Test(expected = IllegalStateException.class)
  public void testDoRunRequestResourceUnknownType() throws StorageClientException,
      AccessDeniedException, IOException {
    when(request.getResource()).thenReturn(resource);
    when(resource.adaptTo(Content.class)).thenReturn(null);

    liteBasicLTIPostOperation.doRun(request, response, contentManager, changes,
        contentPath);
  }

  /**
   * Verification edge case where user UX has passed a malformed URL.
   * {@link LiteBasicLTIPostOperation#doRun(SlingHttpServletRequest, HtmlResponse, ContentManager, List, String)}
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   * @throws IOException
   */
  @SuppressWarnings("deprecation")
  @Test
  public void testDoRunMalformedURL() throws StorageClientException,
      AccessDeniedException, IOException {
    when(request.getParameter(COLON_CONTENT)).thenReturn(null);
    when(request.getParameter(CONTENT_TYPE)).thenReturn(null);
    when(requestParameterLtiUrl.getString(eq("UTF-8"))).thenReturn(".");

    liteBasicLTIPostOperation.doRun(request, response, contentManager, changes,
        contentPath);
    verify(response, times(1)).setStatus(eq(HttpServletResponse.SC_PRECONDITION_FAILED),
        anyString());
    verify(contentManager, never()).update(any(Content.class));
  }

  /**
   * Edge case where multi-valued properties are passed to operation. See:
   * {@link LiteBasicLTIPostOperation#doRun(SlingHttpServletRequest, HtmlResponse, ContentManager, List, String)}
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   * @throws IOException
   */
  @Test(expected = StorageClientException.class)
  public void testDoRunMultiValuedProperties() throws StorageClientException,
      AccessDeniedException, IOException {
    when(request.getParameter(COLON_CONTENT)).thenReturn(null);
    when(request.getParameter(CONTENT_TYPE)).thenReturn(null);
    // Multi-valued parameters are not supported
    map.put(DEBUG,
        new RequestParameter[] { requestParameterDebug, requestParameterDebug });

    liteBasicLTIPostOperation.doRun(request, response, contentManager, changes,
        contentPath);
    verifyNode();
  }

  /**
   * Code coverage case where StorageClientException is thrown. See:
   * {@link LiteBasicLTIPostOperation#doRun(SlingHttpServletRequest, HtmlResponse, ContentManager, List, String)}
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   * @throws IOException
   */
  @Test(expected = StorageClientException.class)
  public void testDoRunSecurityExceptionThrown() throws StorageClientException,
      AccessDeniedException, IOException {
    doThrow(StorageClientException.class).when(eventAdmin).postEvent(any(Event.class));
    liteBasicLTIPostOperation.doRun(request, response, contentManager, changes,
        contentPath);
  }

  /**
   * Code coverage test case. Make sure IllegalArgumentException is thrown when nulls are
   * passed to method. See:
   * {@link LiteBasicLTIPostOperation#doRun(SlingHttpServletRequest, HtmlResponse, ContentManager, List, String)}
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   * @throws IOException
   */
  @Test(expected = IllegalArgumentException.class)
  public void testDoRunIllegalArgumentException1() throws StorageClientException,
      AccessDeniedException, IOException {
    liteBasicLTIPostOperation.doRun(null, response, contentManager, changes, contentPath);
  }

  /**
   * Code coverage test case. Make sure IllegalArgumentException is thrown when nulls are
   * passed to method. See:
   * {@link LiteBasicLTIPostOperation#doRun(SlingHttpServletRequest, HtmlResponse, ContentManager, List, String)}
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   * @throws IOException
   */
  @Test(expected = IllegalArgumentException.class)
  public void testDoRunIllegalArgumentException2() throws StorageClientException,
      AccessDeniedException, IOException {
    liteBasicLTIPostOperation.doRun(request, null, contentManager, changes, contentPath);
  }

  /**
   * Code coverage test case. Make sure IllegalArgumentException is thrown when nulls are
   * passed to method. See:
   * {@link LiteBasicLTIPostOperation#doRun(SlingHttpServletRequest, HtmlResponse, ContentManager, List, String)}
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   * @throws IOException
   */
  @Test(expected = IllegalArgumentException.class)
  public void testDoRunIllegalArgumentException3() throws StorageClientException,
      AccessDeniedException, IOException {
    liteBasicLTIPostOperation.doRun(request, response, null, changes, contentPath);
  }

  /**
   * Code coverage test case. Make sure IllegalArgumentException is thrown when nulls are
   * passed to method. See:
   * {@link LiteBasicLTIPostOperation#doRun(SlingHttpServletRequest, HtmlResponse, ContentManager, List, String)}
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   * @throws IOException
   */
  @Test(expected = IllegalArgumentException.class)
  public void testDoRunIllegalArgumentException4() throws StorageClientException,
      AccessDeniedException, IOException {
    liteBasicLTIPostOperation.doRun(request, response, contentManager, changes, null);
  }

  /**
   * Code coverage test case. {@link LiteBasicLTIPostOperation#bindRepository(Repository)}
   */
  @Test
  public void testBindRepository() {
    liteBasicLTIPostOperation.bindRepository(repository);
    assertTrue("objects should be identical",
        repository == liteBasicLTIPostOperation.repository);
  }

  /**
   * Code coverage test case.
   * {@link LiteBasicLTIPostOperation#unbindRepository(Repository)}
   */
  @Test
  public void testUnBindRepository() {
    liteBasicLTIPostOperation.unbindRepository(repository);
    assertNull(liteBasicLTIPostOperation.repository);
  }

  /**
   * Test happy case.
   * {@link LiteBasicLTIPostOperation#mutateProperties(Content, String, String, boolean, Map)}
   */
  @Test
  public void testMutateProperties() {
    liteBasicLTIPostOperation.mutateProperties(node, LTI_URL_KEY, LTI_URL_VALUE, false,
        sensitiveData);
    verify(node, times(1)).setProperty(LTI_URL_KEY, LTI_URL_VALUE);
  }

  /**
   * Test illegal URL passed to operation.
   * {@link LiteBasicLTIPostOperation#mutateProperties(Content, String, String, boolean, Map)}
   */
  @Test(expected = IllegalArgumentException.class)
  public void testMutatePropertiesIllegalURL() {
    liteBasicLTIPostOperation.mutateProperties(node, LTI_URL_KEY, ".", false,
        sensitiveData);
    verify(node, never()).setProperty(LTI_URL_KEY, LTI_URL_VALUE);
  }

  /**
   * Test happy case where a property has been removed.
   * {@link LiteBasicLTIPostOperation#mutateProperties(Content, String, String, boolean, Map)}
   */
  @Test
  public void testMutatePropertiesNullValue() {
    when(node.hasProperty(LTI_URL_KEY)).thenReturn(true);

    liteBasicLTIPostOperation.mutateProperties(node, LTI_URL_KEY, null, false,
        sensitiveData);
    verify(node, times(1)).removeProperty(LTI_URL_KEY);
  }

  /**
   * Test happy case where a property has been removed.
   * {@link LiteBasicLTIPostOperation#mutateProperties(Content, String, String, boolean, Map)}
   */
  @Test
  public void testMutatePropertiesEmptyValue() {
    when(node.hasProperty(LTI_URL_KEY)).thenReturn(true);

    liteBasicLTIPostOperation.mutateProperties(node, LTI_URL_KEY, "", false,
        sensitiveData);
    verify(node, times(1)).hasProperty(LTI_URL_KEY);
  }

  // --------------------------------------------------------------------------

  private void verifyNode() throws AccessDeniedException, StorageClientException {
    verify(contentManager, atLeast(1)).exists(eq(contentPath));
    final ArgumentCaptor<Content> nodeArgument = ArgumentCaptor.forClass(Content.class);
    verify(contentManager, atLeast(1)).update(nodeArgument.capture());
    final Content node = nodeArgument.getValue();
    assertNotNull(node);
    assertEquals(LTI_URL_VALUE, node.getProperty(LTI_URL_KEY));
    assertNull(node.getProperty(LTIKEY));
    assertNull(node.getProperty(LTISECRET));
    assertEquals(true, node.getProperty(RELEASE_NAMES));
    assertEquals(true, node.getProperty(RELEASE_PRINCIPAL_NAME));
    assertEquals(true, node.getProperty(RELEASE_EMAIL));
    assertEquals("0", node.getProperty(BORDER_SIZE));
    assertEquals("ccc", node.getProperty(BORDER_COLOR));
    assertEquals("100", node.getProperty(WIDTH));
    assertEquals("%", node.getProperty(WIDTH_UNIT));
    assertEquals(SAKAI_BASICLTI, node.getProperty(SLING_RESOURCE_TYPE));
    assertEquals(false, node.getProperty(DEBUG));
    verify(eventAdmin, times(1)).postEvent(any(Event.class));
  }

  private void verifyAclModification() throws StorageClientException,
      AccessDeniedException {
    final ArgumentCaptor<AclModification[]> aclModificationArrayArgument = ArgumentCaptor
        .forClass(AclModification[].class);
    verify(accessControlManager, atLeastOnce()).setAcl(eq(Security.ZONE_CONTENT),
        anyString(), aclModificationArrayArgument.capture());
    // ensure we are applying the right deny Acls
    final List<AclModification> aclModifications = Arrays
        .asList(aclModificationArrayArgument.getValue());
    final AclModification denyAnonymous = new AclModification(
        AclModification.denyKey(User.ANON_USER), Permissions.ALL.getPermission(),
        Operation.OP_REPLACE);
    final AclModification denyEveryone = new AclModification(
        AclModification.denyKey(Group.EVERYONE), Permissions.ALL.getPermission(),
        Operation.OP_REPLACE);
    final AclModification denyCurrentUser = new AclModification(
        AclModification.denyKey(currentUserId), Permissions.ALL.getPermission(),
        Operation.OP_REPLACE);
    assertTrue(aclModifications.contains(denyAnonymous));
    assertTrue(aclModifications.contains(denyEveryone));
    assertTrue(aclModifications.contains(denyCurrentUser));
  }

}
