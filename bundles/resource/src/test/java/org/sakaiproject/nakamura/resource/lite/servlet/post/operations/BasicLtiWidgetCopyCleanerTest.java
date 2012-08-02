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
package org.sakaiproject.nakamura.resource.lite.servlet.post.operations;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HtmlResponse;
import org.apache.sling.servlets.post.Modification;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification.Operation;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;
import org.sakaiproject.nakamura.resource.lite.SparsePostOperationServiceImpl;
import org.sakaiproject.nakamura.util.ContentUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * Verifies the functionality of the BasicLtiWidgetCopyCleaner class.
 */
@RunWith(value = MockitoJUnitRunner.class)
public class BasicLtiWidgetCopyCleanerTest {

  private static final String NAMESPACE = "/tests/org/sakaiproject/nakamura/resource/lite/" +
      "servlet/post/operations/BasicLtiWidgetCopyCleanerTest";

  @Mock
  SlingHttpServletRequest request;
  
  @Mock
  ResourceResolver resourceResolver;
  
  @Mock
  MockableJcrSessionSessionAdaptable sessionAdaptable;
  
  @Mock
  HtmlResponse response;
  
  /**
   * Verify that when the destination does not exist, there is nothing to clean. Therefore, no
   * changes to the data-store should be reported.
   * 
   * @throws Exception
   */
  @Test
  public void testNothingToClean() throws Exception {
    String from = namespace("testNothingToClean/from");
    String to = namespace("testNothingToClean/to");
    AbstractBasicLtiCleaner cleaner = createCleaner();
    
    List<Modification> modifications = cleaner.clean(from, to, createSession(cleaner.getRepository(), "test"));
    Assert.assertNotNull(modifications);
    Assert.assertEquals(0, modifications.size());
  }
  
  /**
   * Verify that the protected keys are indeed copied from the source location to the destination.
   * 
   * @throws Exception
   */
  @Test
  public void testProtectedKeysAreCopied() throws Exception {
    AbstractBasicLtiCleaner cleaner = createCleaner();
    doTestProtectedKeysAreCopied(cleaner);
  }
  
  protected void doTestProtectedKeysAreCopied(AbstractBasicLtiCleaner cleaner) throws Exception {
    String root = namespace("testProtectedKeysAreCopied");
    String from = namespace("testProtectedKeysAreCopied/from");
    String to = namespace("testProtectedKeysAreCopied/to");
    String ltiPathFrom = StorageClientUtils.newPath(from, "id2207414/basiclti");
    String ltiPathTo = StorageClientUtils.newPath(to, "id2207414/basiclti");
    String ltiKeysPathRelative = "id2207414/basiclti/ltiKeys";
    
    Session adminSession = cleaner.getRepository().loginAdministrative();
    Session testSession = cleaner.getRepository().loginAdministrative("test");
    ContentManager adminContentManager = adminSession.getContentManager();
    ContentManager testContentManager = testSession.getContentManager();
    
    // allow read/write for the "test" user on the root content node
    adminSession = cleaner.getRepository().loginAdministrative();
    adminSession.getAccessControlManager().setAcl(Security.ZONE_CONTENT, root,
        new AclModification[] { new AclModification(AclModification.grantKey("test"),
            Permissions.CAN_WRITE.getPermission(), Operation.OP_REPLACE) });
    
    // first, load the data to copy into the "from" path
    ContentUtils.createContentFromJsonResource(adminContentManager, from, getClassLoader(),
        "org/sakaiproject/nakamura/resource/lite/servlet/post/operations/CopyCleanerTest1.json");
    
    // second, lock down the ltiKeys
    String ltiKeysFrom = StorageClientUtils.newPath(from, ltiKeysPathRelative);
    accessControlSensitiveNode(ltiKeysFrom, cleaner.getRepository(), "test");
    
    // third, copy it to the 'to' path as the unprivileged user
    // little permission sanity check, verify the 'test' user cannot access the source keys
    boolean hasAccess = true;
    try {
      Content keys = testContentManager.get(ltiKeysFrom);
      keys.getProperty("ltikey");
    } catch (AccessDeniedException e) {
      hasAccess = false;
    }
    Assert.assertFalse(hasAccess);
    runCopyOperation(from, to, testSession);
    
    // fourth, clean it up as the unprivileged user
    cleaner.clean(ltiPathFrom, ltiPathTo, testSession);
    
    // fifth, verify that the keys were copied and are in tact
    String ltiKeysTo = StorageClientUtils.newPath(to, ltiKeysPathRelative);
    Content ltiKeys = adminContentManager.get(ltiKeysTo);
    Assert.assertNotNull(ltiKeys);
    Assert.assertEquals("key", ltiKeys.getProperty("ltikey"));
    Assert.assertEquals("secret", ltiKeys.getProperty("ltisecret"));
    
    // sixth, verify that the copied keys are still locked down to admin
    Authorizable testAuth = testSession.getAuthorizableManager().getUser();
    boolean lockedDown = !adminSession.getAccessControlManager().can(testAuth, Security.ZONE_CONTENT,
        ltiKeysTo, Permissions.CAN_READ);
    Assert.assertTrue("Expected the ltiKeys to be locked down.", lockedDown);
  }
  
  /**
   * Apply the necessary access control entries so that only admin users can read/write
   * the sensitive node.
   * 
   * @param sensitiveNodePath
   * @param adminSession
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  private void accessControlSensitiveNode(final String sensitiveNodePath,
      final Repository repository, String currentUserId) throws StorageClientException,
      AccessDeniedException {
    Session adminSession = null;
    try {
      adminSession = repository.loginAdministrative();
      adminSession.getAccessControlManager().setAcl(
          Security.ZONE_CONTENT,
          sensitiveNodePath,
          new AclModification[] {
              new AclModification(AclModification.denyKey(User.ANON_USER), Permissions.ALL
                  .getPermission(), Operation.OP_REPLACE),
              new AclModification(AclModification.denyKey(Group.EVERYONE), Permissions.ALL
                  .getPermission(), Operation.OP_REPLACE),
              new AclModification(AclModification.denyKey(currentUserId), Permissions.ALL
                  .getPermission(), Operation.OP_REPLACE) });
    } finally {
      if (adminSession != null) {
        adminSession.logout();
      }
    }
  }
  
  private ClassLoader getClassLoader() {
    return Thread.currentThread().getContextClassLoader();
  }
  
  protected void runCopyOperation(String from, String to, Session session)
      throws StorageClientException, AccessDeniedException, IOException {
    Mockito.when(request.getParameter(CopyOperation.PROP_DEST)).thenReturn(to);
    Mockito.when(request.getResourceResolver()).thenReturn(resourceResolver);
    Mockito.when(resourceResolver.adaptTo(javax.jcr.Session.class)).thenReturn(sessionAdaptable);
    Mockito.when(sessionAdaptable.getSession()).thenReturn(session);
    createCopyOperation().doRun(request, response, session.getContentManager(), new LinkedList<Modification>(), from);
  }
  
  protected AbstractBasicLtiCleaner createCleaner() throws ClientPoolException, StorageClientException,
      AccessDeniedException, ClassNotFoundException, IOException {
    BasicLtiWidgetCopyCleaner cleaner = new BasicLtiWidgetCopyCleaner();
    cleaner.repository = createRepository();
    return cleaner;
  }
  
  protected Repository createRepository() throws ClientPoolException, AccessDeniedException,
      StorageClientException, ClassNotFoundException, IOException {
    Repository repo = new BaseMemoryRepository().getRepository();
    repo.loginAdministrative().getAuthorizableManager().createUser("test", "test", "test", new HashMap<String, Object>());
    return repo;
  }
  
  private CopyOperation createCopyOperation() {
    CopyOperation copyOperation = new CopyOperation();
    copyOperation.sparsePostOperationService = new SparsePostOperationServiceImpl();
    return copyOperation;
  }
  
  private Session createSession(Repository repository, String userId) throws ClientPoolException,
      StorageClientException, AccessDeniedException {
    if (userId == null) {
      return repository.loginAdministrative();
    } else {
      return repository.loginAdministrative(userId);
    }
  }

  protected String namespace(String path) {
    return String.format("%s/%s", NAMESPACE, path);
  }
}
