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
package org.sakaiproject.nakamura.mailman.impl;

import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.osgi.service.event.Event;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.user.AuthorizableEvent;
import org.sakaiproject.nakamura.api.user.AuthorizableEvent.Operation;
import org.sakaiproject.nakamura.mailman.MailmanManager;
import org.sakaiproject.nakamura.testutils.easymock.AbstractEasyMockTest;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;

public class MailmanGroupManagerTest extends AbstractEasyMockTest {

  private MailmanManager mailmanManager;
  private Repository jcrRepository;
  private MailmanGroupManager groupManager;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    mailmanManager = createMock(MailmanManager.class);
    jcrRepository = createMock(Repository.class);
    groupManager = new MailmanGroupManager(mailmanManager, jcrRepository);
  }

  @Test
  public void testHandleGroupAdd() throws Exception {
    groupManager.listManagementPassword = "pwd";
    String groupId = "g-testgroup";
    String topic = "org/apache/sling/jackrabbit/usermanager/event/create";
    Dictionary<String,Object> properties = new Hashtable<String, Object>();
    properties.put("type","group");
    properties.put("path", groupId);
    Event event = new Event(topic, properties);

    mailmanManager.createList(groupId, "pwd");
    replay();

    groupManager.handleEvent(event);
    verify();
  }

  /* MailmanGroupManager provides no path to the delete/remove list logic
  @Test
  public void testHandleGroupRemove() throws MailmanException {
    String groupName = "g-testgroup";
    String topic = "org/apache/sling/jackrabbit/usermanager/event/delete";
    Dictionary<String,Object> properties = new Hashtable<String, Object>();
    properties.put(AuthorizableEvent.OPERATION, Operation.delete);
    properties.put(AuthorizableEvent.PRINCIPAL_NAME, groupName);
    properties.put(AuthorizableEvent.TOPIC, topic);
    Event event = new Event(topic, properties);

    mailmanManager.deleteList(groupName, null);
    replay();
    groupManager.handleEvent(event);
    verify();
  }
  */

  @Test
  public void testHandleGroupJoin() throws Exception {
    String groupId = "g-testgroup";
    String userId = "testuser";
    String testAddress = "test@test.com";

    mockGroupAndUserRepository(userId, groupId, testAddress);

    String topic = "org/apache/sling/jackrabbit/usermanager/event/join";
    Dictionary<String,Object> properties = new Hashtable<String, Object>();
    properties.put("type","group");
    properties.put("path", groupId);
    properties.put("added","testuser");
    Event event = new Event(topic, properties);

    expect(mailmanManager.addMember(groupId, "", testAddress)).andReturn(true);
    expect(mailmanManager.addMember("g", "", testAddress)).andReturn(true);
    replay();
    groupManager.activate(new HashMap<String, String>());

    groupManager.handleEvent(event);
    verify();
  }

  @Test
  public void testHandleGroupPart() throws Exception {
    String groupId = "g-testgroup";
    String userId = "testuser";
    String testAddress = "test@test.com";
    String topic = "org/apache/sling/jackrabbit/usermanager/event/part";

    mockGroupAndUserRepository(userId, groupId, testAddress);

    Dictionary<String,Object> properties = new Hashtable<String, Object>();
    properties.put("type","group");
    properties.put("path", groupId);
    properties.put("removed","testuser");
    Event event = new Event(topic, properties);

    expect(mailmanManager.removeMember(userId, "", testAddress)).andReturn(true);
    replay();
    groupManager.activate(new HashMap<String, String>());

    groupManager.handleEvent(event);
    verify();
  }

  private void mockGroupAndUserRepository(String userId, String groupId, String testAddress)  throws Exception {
    groupManager.repository = createMock(Repository.class);
    Session session = createMock(Session.class);
    AuthorizableManager authzMgr = createMock(AuthorizableManager.class);
    Authorizable user = createMock(User.class);
    Authorizable group = createMock(Group.class);

    expect(groupManager.repository.loginAdministrative()).andReturn(session).anyTimes();
    expect(session.getAuthorizableManager()).andReturn(authzMgr).anyTimes();
    expect(authzMgr.findAuthorizable(eq(userId))).andReturn(user).anyTimes();
    expect(user.getProperty(eq("email"))).andReturn(testAddress).anyTimes();
    expect(user.isGroup()).andReturn(false).anyTimes();
    expect(authzMgr.findAuthorizable(eq(groupId))).andReturn(group).anyTimes();
    expect(group.getId()).andReturn(groupId).anyTimes();
    expect(group.isGroup()).andReturn(true).anyTimes();
  }
}
