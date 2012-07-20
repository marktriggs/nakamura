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
package org.sakaiproject.nakamura.user.search;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import java.util.Collection;
import java.util.Iterator;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrInputDocument;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.service.event.Event;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StoreListener;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.solr.RepositorySession;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class ProfileIndexingHandlerTest {

  @Mock
  private RepositorySession repoSession;

  @Mock
  private Session session;

  @Mock
  private ContentManager contentManager;

  @Mock
  private Content authprofileContent;

  private static String AUTH_PROFILE_PATH = "a:jane/public/authprofile";
  private static String SECTION_PATH = AUTH_PROFILE_PATH + "/institutional";
  private static String ELEMENTS_PATH = SECTION_PATH + "/elements";

  private ProfileIndexingHandler handler;

  @Before
  public void setUp() throws Exception {
    handler = new ProfileIndexingHandler();
    when(repoSession.adaptTo(Session.class)).thenReturn(session);
    when(session.getContentManager()).thenReturn(contentManager);
    when(contentManager.get(AUTH_PROFILE_PATH)).thenReturn(authprofileContent);
  }

  @Test
  public void doNotReturnNull() {
    Event event = new Event(StoreListener.ADDED_TOPIC, ImmutableMap.of(
        "path", "unexpectedpath"
    ));
    // The IndexingHandler API specifies non-null returns.
    assertNotNull(handler.getDocuments(repoSession, event));
    assertNotNull(handler.getDeleteQueries(repoSession, event));
  }

  @Test
  public void doNotWalkDownFromTop() {
    Event event = new Event(StoreListener.ADDED_TOPIC, ImmutableMap.of(
        "path", AUTH_PROFILE_PATH
    ));
    Collection<SolrInputDocument> documents = handler.getDocuments(repoSession, event);
    assertEquals(0, documents.size());
  }

  @Test
  public void doNotIndexBasicSection() {
    Event event = new Event(StoreListener.ADDED_TOPIC, ImmutableMap.of(
        "path", AUTH_PROFILE_PATH + "/basic/elements/firstName"
    ));
    Collection<SolrInputDocument> documents = handler.getDocuments(repoSession, event);
    assertEquals(0, documents.size());
  }
  
  @Test
  public void indexSectionWhenElementUpdated() throws Exception {
    Iterator<Content> elements = Iterators.forArray(
        mockElementContent("college", "Wottsamatta U."),
        mockElementContent("major", "Basket Theory")
    );
    when(contentManager.listChildren(ELEMENTS_PATH)).thenReturn(elements);
    Event event = new Event(StoreListener.UPDATED_TOPIC, ImmutableMap.of(
        "path", ELEMENTS_PATH + "/college"
    ));
    Collection<SolrInputDocument> documents = handler.getDocuments(repoSession, event);
    assertEquals(1, documents.size());
    SolrInputDocument document = documents.iterator().next();
    Collection<Object> values = document.getFieldValues("profile");
    assertTrue(values.containsAll(ImmutableList.of("Wottsamatta U.", "Basket Theory")));
  }
  
  private Content mockElementContent(String elementName, String value) {
    Content elementContent = mock(Content.class);
    when(elementContent.getProperty("value")).thenReturn(value);
    return elementContent;
  }

  @Test
  public void deleteSectionIndexWhenSectionDeleted() {
    Event event = new Event(StoreListener.DELETE_TOPIC, ImmutableMap.of(
        "path", ELEMENTS_PATH
    ));
    Collection<String> deleteQueries = handler.getDeleteQueries(repoSession, event);
    assertEquals(1, deleteQueries.size());
    assertEquals("id:" + ClientUtils.escapeQueryChars(SECTION_PATH),
        deleteQueries.iterator().next());
    Collection<SolrInputDocument> documents = handler.getDocuments(repoSession, event);
    assertEquals(0, documents.size());
  }

  @Test
  public void reindexSectionWhenElementDeleted() throws Exception {
    Iterator<Content> elements = Iterators.forArray(
        mockElementContent("college", "Wottsamatta U."),
        mockElementContent("major", "Basket Theory")
    );
    when(contentManager.listChildren(ELEMENTS_PATH)).thenReturn(elements);
    Event event = new Event(StoreListener.DELETE_TOPIC, ImmutableMap.of(
        "path", ELEMENTS_PATH + "/role"
    ));
    Collection<String> deleteQueries = handler.getDeleteQueries(repoSession, event);
    assertEquals(0, deleteQueries.size());
    // This tests that the handler does the right thing, but its handling is
    // currently blocked by a bug in the Solr integration module: KERN-2722
    Collection<SolrInputDocument> documents = handler.getDocuments(repoSession, event);
    assertEquals(1, documents.size());
    SolrInputDocument document = documents.iterator().next();
    Collection<Object> values = document.getFieldValues("profile");
    assertTrue(values.containsAll(ImmutableList.of("Wottsamatta U.", "Basket Theory")));
  }
}
