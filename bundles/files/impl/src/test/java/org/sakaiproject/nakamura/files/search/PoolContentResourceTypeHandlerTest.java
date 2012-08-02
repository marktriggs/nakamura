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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrInputDocument;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.service.event.Event;
import org.sakaiproject.nakamura.api.files.FilesConstants;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.solr.RepositorySession;
import org.sakaiproject.nakamura.api.tika.TikaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests the PoolContentResourceTypeHandler class.
 */
@RunWith(MockitoJUnitRunner.class)
public class PoolContentResourceTypeHandlerTest {

  private final static Logger LOGGER = LoggerFactory.getLogger(PoolContentResourceTypeHandlerTest.class);
  private final static String RESOURCE_TYPE = "sakai/pooled-content";
  private final static String PATH_TO_FILE = "/path/to/file.json";
  private final static String PATH_TO_PAGE = "/path/to/page";
  private final static String PATH_TO_PARENT = "/path/to";
  private final static String[] PROPERTY_EDITOR = new String[] { "user1" };
  private final static String[] PROPERTY_EDITORS = new String[] { "user1", "user2" };
  
  @Mock
  private RepositorySession repositorySession;
  
  @Mock
  private Session session;
  
  @Mock
  private ContentManager contentManager;
  
  private Event event = new Event("test", buildEventProperties());
  
  /**
   * Verify that the pool content handler will index the value of a single editor in the content editor property.
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  @Test
  public void editor() throws StorageClientException, AccessDeniedException {
    Map<String, Object> props = buildStandardPooledContentProperties();
    props.put(FilesConstants.POOLED_CONTENT_USER_EDITOR, PROPERTY_EDITOR);
    Content content = new Content(PATH_TO_FILE, props);
    
    when(repositorySession.adaptTo(Session.class)).thenReturn(session);
    when(session.getContentManager()).thenReturn(contentManager);
    when(contentManager.get(PATH_TO_FILE)).thenReturn(content);
    
    PoolContentResourceTypeHandler handler = new PoolContentResourceTypeHandler();
    Collection<SolrInputDocument> docs = handler.getDocuments(repositorySession, event);
    
    assertEquals("Expected 1 Solr input document to be returned.", 1, docs.size());
    SolrInputDocument doc = docs.iterator().next();
    
    String editor = (String) doc.getFieldValue("editor");
    assertNotNull("Expected 1 editor to be indexed.", editor);
    assertEquals("Invalid editor username", "user1", editor);
  }
  
  /**
   * Verify that the pool content handler will index the values of multiple editors in the content editor property.
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  @Test
  public void editors() throws StorageClientException, AccessDeniedException {
    Map<String, Object> props = buildStandardPooledContentProperties();
    props.put(FilesConstants.POOLED_CONTENT_USER_EDITOR, PROPERTY_EDITORS);
    Content content = new Content(PATH_TO_FILE, props);
    
    when(repositorySession.adaptTo(Session.class)).thenReturn(session);
    when(session.getContentManager()).thenReturn(contentManager);
    when(contentManager.get(PATH_TO_FILE)).thenReturn(content);
    
    PoolContentResourceTypeHandler handler = new PoolContentResourceTypeHandler();
    Collection<SolrInputDocument> docs = handler.getDocuments(repositorySession, event);
    
    assertEquals("Expected 1 Solr input document to be returned.", 1, docs.size());
    SolrInputDocument doc = docs.iterator().next();
    
    Object[] editors = doc.getFieldValues("editor").toArray(new Object[0]);
    assertEquals("Expected 1 editors to be indexed.", 2, editors.length);
    assertEquals("Invalid editor username", "user1", editors[0]);
    assertEquals("Invalid editor username", "user2", editors[1]);
  }

  @Test
  public void testResourceType() throws Exception {
    Map<String, Object> props = buildNonPooledContentProperties();
    Content content = new Content(PATH_TO_FILE, props);

    when(repositorySession.adaptTo(Session.class)).thenReturn(session);
    when(session.getContentManager()).thenReturn(contentManager);
    when(contentManager.get(PATH_TO_FILE)).thenReturn(content);
    InputStream is = mock(InputStream.class);
    when(contentManager.getInputStream(anyString())).thenReturn(is);

    PoolContentResourceTypeHandler handler = new PoolContentResourceTypeHandler();
    Collection<SolrInputDocument> docs = handler.getDocuments(repositorySession, event);

    assertTrue(docs.isEmpty());

    Collection<String> queries = handler.getDeleteQueries(repositorySession, event);
    assertTrue(queries.isEmpty());
  }

  @Test
  public void testDeleteQueries() throws Exception {
    Map<String, Object> props = buildStandardPooledContentProperties();
    Content content = new Content(PATH_TO_FILE, props);

    when(repositorySession.adaptTo(Session.class)).thenReturn(session);
    when(session.getContentManager()).thenReturn(contentManager);
    when(contentManager.get(PATH_TO_FILE)).thenReturn(content);

    PoolContentResourceTypeHandler handler = new PoolContentResourceTypeHandler();

    Map<String, Object> eventProps = buildEventProperties();
    eventProps.put("resourceType", RESOURCE_TYPE);
    Event event = new Event("test", eventProps);

    Collection<String> queries = handler.getDeleteQueries(repositorySession, event);
    assertTrue(queries.contains("id:" + ClientUtils.escapeQueryChars((String)event.getProperty("path"))));
  }

  @Test
  public void testPagedContent() throws Exception {
    Map<String, Object> props = buildStandardPooledContentProperties();

    props.put("page", "page");
    props.put("structure0","{\"key\":{\"_ref\":\"page\"}}");

    Content content = new Content(PATH_TO_FILE, props);
    Content parent = new Content(PATH_TO_PARENT, props);
    Content page = new Content(PATH_TO_PAGE, props);

    when(repositorySession.adaptTo(Session.class)).thenReturn(session);
    when(session.getContentManager()).thenReturn(contentManager);
    when(contentManager.get(PATH_TO_PARENT)).thenReturn(parent);
    when(contentManager.get(PATH_TO_FILE)).thenReturn(content);
    when(contentManager.get(PATH_TO_PAGE)).thenReturn(page);

    TikaService tika = mock(TikaService.class);
    when(tika.parseToString(any(InputStream.class))).thenReturn("indexString");

    PoolContentResourceTypeHandler handler = new PoolContentResourceTypeHandler();
    handler.tika = tika;

    Collection<SolrInputDocument> docs = handler.getDocuments(repositorySession, event);

    assertEquals(1, docs.size());
    SolrInputDocument doc = docs.iterator().next();

    assertEquals("indexString", doc.getField("content").getValue());
  }

  /**
   * Build a map of test event properties for the tests.
   * 
   * @return
   */
  private Map<String, Object> buildEventProperties() {
    Map<String, Object> props = new HashMap<String, Object>();
    props.put("path", PATH_TO_FILE);
    return props;
  }
  
  /**
   * Build the map of properties that is common to all pooled content objects in the tests.
   * 
   * @return
   */
  private Map<String, Object> buildStandardPooledContentProperties() {
    Map<String, Object> props = new HashMap<String, Object>();
    props.put("sling:resourceType", "sakai/pooled-content");
    props.put("sakai:pooled-content-file-name", "file.json");
    return props;
  }

  /**
   * Build a map of properties that is not sakai/pooled-content.
   *
   * @return
   */
  private Map<String, Object> buildNonPooledContentProperties() {
    Map<String, Object> props = new HashMap<String, Object>();
    props.put("sling:resourceType", "sakai/not-pooled-content");
    return props;
  }
}
