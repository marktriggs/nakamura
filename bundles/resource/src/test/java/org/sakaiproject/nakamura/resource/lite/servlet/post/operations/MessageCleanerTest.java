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

import org.apache.sling.servlets.post.Modification;
import org.junit.Assert;
import org.junit.Test;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification.Operation;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;
import org.sakaiproject.nakamura.util.ContentUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

/**
 * Verify the operation of the MessageCopyCleaner.
 */
public class MessageCleanerTest {

  private static final String NAMESPACE = "/tests/org/sakaiproject/nakamura/resource/lite/" +
      "servlet/post/operations/MessageCopyCleanerTest";
  
  /**
   * Verify that when bogus non-existent paths are supplied, no equally-bogus modifications
   * are made.
   * 
   * @throws Exception
   */
  @Test
  public void testNothingToClean() throws Exception {
    String from = namespace("testNothingToClean/from");
    String to = namespace("testNothingToClean/to");
    MessageCleaner cleaner = createCleaner();
    
    List<Modification> modifications = cleaner.clean(from, to, createSession(cleaner.repository, "test"));
    Assert.assertNotNull(modifications);
    Assert.assertEquals(0, modifications.size());
  }
  
  /**
   * Verify that the clean operation can identify a message from its path, and adjust its
   * sakai:messagestore properly.
   * 
   * @throws Exception
   */
  @Test
  public void testCleanSakaiMessagestore() throws Exception {
    String path = namespace("testNothingToClean/content");
    MessageCleaner cleaner = createCleaner();
    Session session = createSession(cleaner.repository, null);
    ContentManager contentManager = session.getContentManager();
    ContentUtils.createContentFromJsonResource(contentManager, path, getClassLoader(),
        "org/sakaiproject/nakamura/resource/lite/servlet/post/operations/CopyCleanerTest1.json");
    
    String messagestorePath = String.format("%s/id2529654/comments/message", path);
    String messagePath = String.format("%s/inbox/c2f752c635feca58abb582fb2113eb4342d50ffe",
        messagestorePath);
    String expectedMessagestoreValue = String.format("%s/", messagestorePath);
    
    cleaner.clean("", messagePath, session);
    Content message = contentManager.get(messagePath);
    Assert.assertNotNull(message);
    Assert.assertEquals(expectedMessagestoreValue, message.getProperty("sakai:messagestore"));
  }
  
  /**
   * Similar to {@link #testCleanSakaiMessagestore()}, except this verifies that if the user
   * performing the copy does not have privilege to modify the message, the sakai:messagestore
   * property is still able to be fixed to maintain the content hierarchy integrity. 
   * 
   * @throws Exception
   */
  @Test
  public void testCleanUnprivileged() throws Exception {
    String path = namespace("testNothingToClean/content");
    MessageCleaner cleaner = createCleaner();
    ContentManager contentManager = createSession(cleaner.repository, null).getContentManager();
    ContentUtils.createContentFromJsonResource(contentManager, path, getClassLoader(),
        "org/sakaiproject/nakamura/resource/lite/servlet/post/operations/CopyCleanerTest1.json");
    
    // reload content manager with an unprivileged user
    Session testSession = createSession(cleaner.repository, "test");
    contentManager = testSession.getContentManager();
    String messagestorePath = String.format("%s/id2529654/comments/message", path);
    String messagePath = String.format("%s/inbox/c2f752c635feca58abb582fb2113eb4342d50ffe",
        messagestorePath);
    String expectedMessagestoreValue = String.format("%s/", messagestorePath);
    
    // deny the "test" user access to write to the messagestore property of messages
    cleaner.repository.loginAdministrative().getAccessControlManager().setAcl(
        Security.ZONE_CONTENT,
        messagePath,
        new AclModification[] {
            new AclModification(AclModification.denyKey(User.ANON_USER), Permissions.CAN_WRITE_PROPERTY
                .getPermission(), Operation.OP_REPLACE),
            new AclModification(AclModification.denyKey(Group.EVERYONE), Permissions.CAN_WRITE_PROPERTY
                .getPermission(), Operation.OP_REPLACE),
            new AclModification(AclModification.denyKey("test"), Permissions.CAN_WRITE_PROPERTY
                .getPermission(), Operation.OP_REPLACE) });
    
    // clean, and verify that despite the user's unprivileged permission, the data was corrected.
    cleaner.clean("", messagePath, testSession);
    Content message = contentManager.get(messagePath);
    Assert.assertNotNull(message);
    Assert.assertEquals(expectedMessagestoreValue, message.getProperty("sakai:messagestore"));
  }
  
  private ClassLoader getClassLoader() {
    return Thread.currentThread().getContextClassLoader();
  }
  
  private MessageCleaner createCleaner() throws ClientPoolException, StorageClientException,
      AccessDeniedException, ClassNotFoundException, IOException {
    MessageCleaner cleaner = new MessageCleaner();
    cleaner.repository = createRepository();
    return cleaner;
  }
  
  private Repository createRepository() throws ClientPoolException, AccessDeniedException,
      StorageClientException, ClassNotFoundException, IOException {
    Repository repo = new BaseMemoryRepository().getRepository();
    repo.loginAdministrative().getAuthorizableManager().createUser("test", "test", "test", new HashMap<String, Object>());
    return repo;
  }
  
  private Session createSession(Repository repository, String userId) throws ClientPoolException,
      StorageClientException, AccessDeniedException, ClassNotFoundException, IOException {
    if (userId == null) {
      return repository.loginAdministrative();
    } else {
      return repository.loginAdministrative(userId);
    }
  }
  
  private String namespace(String path) {
    return String.format("%s/%s", NAMESPACE, path);
  }
}
