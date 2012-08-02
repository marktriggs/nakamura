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
package org.sakaiproject.nakamura.message.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.solr.common.SolrInputDocument;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.service.event.Event;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.message.MessageConstants;
import org.sakaiproject.nakamura.api.resource.DateParser;
import org.sakaiproject.nakamura.api.solr.IndexingHandler;
import org.sakaiproject.nakamura.api.solr.RepositorySession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Test the Message Indexing Handler
 */
@RunWith(MockitoJUnitRunner.class)
public class MessageIndexingHandlerTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(MessageIndexingHandlerTest.class);

  private final static String TEMP_PATH = "/DffdkDl/tmp_id12345678/content";

  @Mock
  protected RepositorySession repositorySession;

  @Mock
  private Session session;

  @Mock
  private ContentManager contentManager;

  @Mock
  private AuthorizableManager authorizableManager;

  private DateParser dateParser;

  private Map<String, Object> messageProps;

  @Before
  public void setUp() {
    dateParser = new DateParser();
    String[] dateFormats = new String[]{ "EEE MMM dd yyyy HH:mm:ss 'GMT'Z", "ISO8601", "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
    "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd", "dd.MM.yyyy HH:mm:ss", "dd.MM.yyyy" };
    for (String dateFormat : dateFormats) {
      try {
        dateParser.register(dateFormat);
      } catch (Throwable t) {
        LOGGER.warn("activate: Ignoring format {} because it is invalid: {}", dateFormat, t);
      }
    }

    messageProps = new HashMap<String, Object>();
    messageProps.put("sling:resourceType", MessageConstants.SAKAI_MESSAGE_RT);
    messageProps.put("sakai:messagestore", "test-messagestore");
    messageProps.put("sakai:messagebox", "test-messagebox");
    messageProps.put("sakai:type", "test-type");
    messageProps.put("sakai:category", "test-category");
    messageProps.put("sakai:from", "test-from");
    messageProps.put("sakai:to", "test-to");
    messageProps.put("sakai:read", "test-read");
    messageProps.put("sakai:marker", "test-marker");
    messageProps.put("sakai:sendstate", "test-sendstate");
    messageProps.put("sakai:initialpost", "test-initialpost");
    messageProps.put(MessageConstants.PROP_SAKAI_SUBJECT, "test-title");
    messageProps.put(MessageConstants.PROP_SAKAI_BODY, "test-content");
    messageProps.put("notindexed", "notindexed");
  }

  /**
   * Verify that trying to index content within a temporary path is gracefully ignored.
   */
  @Test
  public void testIgnoreTempContent() {
    Event event = createEventWithTempPath();
    MessageIndexingHandler handler = new MessageIndexingHandler();
    handler.dateParser = this.dateParser;
    Collection<SolrInputDocument> documents = handler.getDocuments(repositorySession, event);
    assertTrue("Expected an empty collection of solr input documents.", documents.isEmpty());
  }

  private Event createEventWithTempPath() {
    Map<String, Object> props = new HashMap<String, Object>();
    props.put(IndexingHandler.FIELD_PATH, TEMP_PATH);
    return new Event("topic", props);
  }

  @Test
  public void testCalendarDateIndexMessage() throws Exception {
    Calendar testCal = Calendar.getInstance();
    Map<String, Object> calendarTestProps = new HashMap<String, Object>(messageProps);
    calendarTestProps.put(MessageConstants.PROP_SAKAI_CREATED, testCal);
    SolrInputDocument doc = handleMessageIndexing(calendarTestProps);
    assertEquals(testCal.getTimeInMillis(), doc.getField(Content.CREATED_FIELD).getValue());
  }

  // test the date string that OAE is seeing in sakai:created message prop
  @Test
  public void testFirstDateStringIndexMessage() throws Exception {
    Map<String, Object> firstDateStringProps = new HashMap<String, Object>(messageProps);
                                                              //  "EEE MMM dd yyyy HH:mm:ss 'GMT'Z"
    firstDateStringProps.put(MessageConstants.PROP_SAKAI_CREATED, "Wed Apr 04 2012 18:14:32 GMT-0700");
    SolrInputDocument doc = handleMessageIndexing(firstDateStringProps);
    Calendar testCal = dateParser.parse("Wed Apr 04 2012 18:14:32 GMT-0700");
    assertEquals(testCal.getTimeInMillis(), doc.getField(Content.CREATED_FIELD).getValue());
  }

 //test the date string that UC Berkeley is seeing in sakai:created message prop
  @Test
  public void testSecondDateStringIndexMessage() throws Exception {
    Map<String, Object> secondDateStringProps = new HashMap<String, Object>(messageProps);
    //  seen at UCBerkeley in discussion messages                 "yyyy-MM-dd'T'HH:mm:ss.SSSZ -
    secondDateStringProps.put(MessageConstants.PROP_SAKAI_CREATED, "2012-04-25T18:14:32-0700");
    SolrInputDocument doc = handleMessageIndexing(secondDateStringProps);
    Calendar testCal = dateParser.parse("2012-04-25T18:14:32-0700");
    assertEquals(testCal.getTimeInMillis(), doc.getField(Content.CREATED_FIELD).getValue());
  }

  private SolrInputDocument handleMessageIndexing(Map<String, Object> props) throws Exception {
    String messagePath = "a:user1/messagePath";

    Content content = new Content(messagePath, props);
    Authorizable sender = mock(Authorizable.class);

    when(sender.getId()).thenReturn("sender");
    when(sender.isGroup()).thenReturn(Boolean.FALSE);
    when(sender.getProperty(eq("firstName"))).thenReturn("test");
    when(sender.getProperty(eq("lastName"))).thenReturn("user");

    Authorizable user1 = mock(Authorizable.class);
    when(user1.getId()).thenReturn("user1");
    when(user1.isGroup()).thenReturn(Boolean.FALSE);
    when(user1.getProperty(eq("firstName"))).thenReturn("user");
    when(user1.getProperty(eq("lastName"))).thenReturn("one");

    when(authorizableManager.findAuthorizable(anyString())).thenReturn(sender);
    when(repositorySession.adaptTo(Session.class)).thenReturn(session);
    when(session.getAuthorizableManager()).thenReturn(authorizableManager);
    when(session.getContentManager()).thenReturn(contentManager);
    when(contentManager.get(messagePath)).thenReturn(content);

    MessageIndexingHandler handler = new MessageIndexingHandler();
    handler.dateParser = this.dateParser;
    Event event = new Event("topic", buildEventProperties(messagePath));

    Collection<SolrInputDocument> documents = handler.getDocuments(repositorySession, event);

    assertNotNull(documents);
    assertTrue(!documents.isEmpty());

    Iterator<SolrInputDocument> docIt = documents.iterator();

    SolrInputDocument doc = docIt.next();

    //test basic message properties
    assertEquals("test-messagestore", doc.getField("messagestore").getValue());
    assertEquals("test-messagebox", doc.getField("messagebox").getValue());
    assertEquals("test-type", doc.getField("type").getValue());
    assertEquals("test-category", doc.getField("category").getValue());
    assertEquals("test-from", doc.getField("from").getValue());
    assertEquals("test-to", doc.getField("to").getValue());
    assertEquals("test-read", doc.getField("read").getValue());
    assertEquals("test-marker", doc.getField("marker").getValue());
    assertEquals("test-sendstate", doc.getField("sendstate").getValue());
    assertEquals("test-initialpost", doc.getField("initialpost").getValue());
    assertEquals("test-title", doc.getField("title").getValue());
    assertEquals("test-content", doc.getField("content").getValue());

    //ensure unexpected value is skipped
    assertNull(doc.getField("notindexed"));

    //test sender name is set
    assertEquals("test", doc.getField("firstName").getValue());
    assertEquals("user", doc.getField("lastName").getValue());

    //an additional doc should have been added for authorizable searching:
    assertTrue(docIt.hasNext());
    SolrInputDocument authDoc = docIt.next();

    //test values set for user/group searching
    assertEquals("test-title", authDoc.getField("title").getValue());
    assertEquals("test-content", authDoc.getField("content").getValue());
    assertEquals("u", authDoc.getField("type").getValue());
    assertEquals(content, authDoc.getField(IndexingHandler._DOC_SOURCE_OBJECT).getValue());
    assertEquals("user1", authDoc.getField(IndexingHandler.FIELD_PATH).getValue());
    assertEquals(messagePath + "-auth", authDoc.getField(IndexingHandler.FIELD_ID).getValue());
    assertEquals("user1", authDoc.getField("returnpath").getValue());

    assertTrue(!docIt.hasNext());
    return doc;
  }

  /**
   * Build a map of test event properties for the tests.
   *
   * @return
   */
  private Map<String, Object> buildEventProperties(String path) {
    Map<String, Object> props = new HashMap<String, Object>();
    props.put("path", path);
    return props;
  }

}
