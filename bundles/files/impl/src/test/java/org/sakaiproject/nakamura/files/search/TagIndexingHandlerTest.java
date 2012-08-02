package org.sakaiproject.nakamura.files.search;

import static org.junit.Assert.*;

import org.apache.solr.common.SolrInputDocument;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;

import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.service.event.Event;
import org.sakaiproject.nakamura.api.files.FilesConstants;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.solr.IndexingHandler;
import org.sakaiproject.nakamura.api.solr.RepositorySession;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * User: duffy
 * Date: 5/22/12
 * Time: 11:27 AM
 */
@RunWith(MockitoJUnitRunner.class)
public class TagIndexingHandlerTest {

  @Mock
  protected RepositorySession repositorySession;

  @Mock
  protected Session session;

  @Mock
  protected ContentManager contentManager;

  private final static String resourceType = "sakai/tag";

  @Test
  public void testEmptyPath() throws Exception {
    TagIndexingHandler handler = new TagIndexingHandler();
    Event noPathEvent = new Event("topic", new HashMap<String, Object>());

    Collection<SolrInputDocument> docs = handler.getDocuments(null, noPathEvent);

    assertNotNull(docs);
    assertTrue(docs.isEmpty());
  }

  @Test
  public void testGetDocuments() throws Exception {
    Map<String, Object> props = new HashMap<String, Object>();
    String path = "path";
    props.put(FilesConstants.SAKAI_TAG_NAME, "testTag");

    Content content = new Content(path, props);
    when(repositorySession.adaptTo(Session.class)).thenReturn(session);
    when(session.getContentManager()).thenReturn(contentManager);
    when(contentManager.get(path)).thenReturn(content);

    TagIndexingHandler handler = new TagIndexingHandler();
    Event event = new Event("topic", buildEventProperties(path));

    Collection<SolrInputDocument> documents = handler.getDocuments(repositorySession, event);

    assertNotNull(documents);
    assertTrue(!documents.isEmpty());

    Iterator<SolrInputDocument> docIt = documents.iterator();

    assertTrue(docIt.hasNext());

    SolrInputDocument doc = docIt.next();

    assertEquals("testTag", doc.getField("tagname").getValue());
    assertEquals(content, doc.getField(IndexingHandler._DOC_SOURCE_OBJECT).getValue());
  }

  @Test
  public void testDeleteQueries() throws Exception {
    Map<String, Object> props = new HashMap<String, Object>();
    String path = "path";
    props.put(FilesConstants.SAKAI_TAG_NAME, "testTag");

    Content content = new Content(path, props);
    when(repositorySession.adaptTo(Session.class)).thenReturn(session);
    when(session.getContentManager()).thenReturn(contentManager);
    when(contentManager.get(path)).thenReturn(content);

    TagIndexingHandler handler = new TagIndexingHandler();
    Event event = new Event("topic", buildEventProperties(path));

    Collection<String> queries = handler.getDeleteQueries(repositorySession, event);
    assertNotNull(queries);

    Iterator<String> queryIt = queries.iterator();

    assertTrue(queryIt.hasNext());

    String query = queryIt.next();
    assertEquals("id:path", query);

    assertTrue(!queryIt.hasNext());
  }

  /**
   * Build a map of test event properties for the tests.
   *
   * @return
   */
  private Map<String, Object> buildEventProperties(String path) {
    Map<String, Object> props = new HashMap<String, Object>();
    props.put("path", path);
    props.put("resourceType", resourceType);
    return props;
  }

}
