package org.sakaiproject.nakamura.pages.search;

import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang.StringUtils;
import org.apache.solr.common.SolrInputDocument;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
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
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

/**
 * User: duffy
 * Date: 5/22/12
 * Time: 3:07 PM
 */
@RunWith(MockitoJUnitRunner.class)
public class WidgetDataIndexingHandlerTest {

  @Mock
  protected RepositorySession repositorySession;

  @Mock
  protected Session session;

  @Mock
  protected ContentManager contentManager;

  private final static String resourceType = "sakai/widget-data";

  @Test
  public void testGetDocuments() throws Exception {
    Map<String, Object> props = new HashMap<String, Object>();
    Map<String, Object> parentProps = new HashMap<String, Object>();

    props.put("sling:resourceType", resourceType);
    props.put(WidgetDataIndexingHandler.INDEXED_FIELDS, new String[] { "field1", "field3" });
    props.put("field1", "value1");
    props.put("field2", "value2");
    props.put("field3", "value3");

    parentProps.put(FilesConstants.POOLED_CONTENT_FILENAME, "contentFileName");

    String path = "docpath/widgetpath";
    Content content = new Content(path, props);
    Content parent = new Content("docpath", parentProps);

    when(contentManager.get(eq(path))).thenReturn(content);
    when(contentManager.get(eq("docpath"))).thenReturn(parent);
    when(repositorySession.adaptTo(Session.class)).thenReturn(session);
    when(session.getContentManager()).thenReturn(contentManager);

    WidgetDataIndexingHandler handler = new WidgetDataIndexingHandler();
    Event event = new Event("topic", buildEventProperties(path));

    Collection<SolrInputDocument> documents = handler.getDocuments(repositorySession, event);

    assertNotNull(documents);
    assertTrue(!documents.isEmpty());

    Iterator<SolrInputDocument> docIt = documents.iterator();
    assertTrue(docIt.hasNext());

    SolrInputDocument doc = docIt.next();

    Set<String> indexedValues = ImmutableSet.copyOf(StringUtils.split((String)doc.getField("widgetdata").getValue()," "));
    assertEquals(2, indexedValues.size());
    assertTrue(indexedValues.contains("value1"));
    assertTrue(indexedValues.contains("value3"));
    assertTrue(!indexedValues.contains("value2"));

    assertEquals("docpath", doc.getField("path").getValue());
    assertEquals("contentFileName", doc.getField("filename").getValue());
    assertEquals("contentFileName", doc.getField("general_sort").getValue());
    assertEquals("docpath", doc.getField("returnpath").getValue());
    assertEquals(content, doc.getField(IndexingHandler._DOC_SOURCE_OBJECT).getValue());

    assertTrue(!docIt.hasNext());
  }

  @Test
  public void testDeleteQueries() throws Exception {
    Map<String, Object> props = new HashMap<String, Object>();
    String path = "path";

    Content content = new Content(path, props);
    when(repositorySession.adaptTo(Session.class)).thenReturn(session);
    when(session.getContentManager()).thenReturn(contentManager);
    when(contentManager.get(path)).thenReturn(content);

    WidgetDataIndexingHandler handler = new WidgetDataIndexingHandler();
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
