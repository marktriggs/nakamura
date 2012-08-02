package org.sakaiproject.nakamura.connections.search;

import org.apache.solr.common.SolrInputDocument;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.service.event.Event;
import org.sakaiproject.nakamura.api.connections.ConnectionConstants;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.solr.IndexingHandler;
import org.sakaiproject.nakamura.api.solr.RepositorySession;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * User: duffy
 * Date: 5/9/12
 * Time: 3:57 PM
 */
@RunWith(MockitoJUnitRunner.class)
public class ConnectionIndexingHandlerTest {

  @Mock
  protected RepositorySession repositorySession;

  @Mock
  protected Session session;

  @Mock
  protected ContentManager contentManager;

  @Mock
  protected AuthorizableManager authorizableManager;

  @Test
  public void testGetDocuments() throws Exception {
    Map<String, Object> props = new HashMap<String, Object>();
    props.put("sling:resourceType", ConnectionConstants.SAKAI_CONTACT_RT);
    String path = "path/user1";

    props.put("sakai:contactstorepath", "test-contactstorepath");
    props.put("sakai:state", "test-state");

    Content content = new Content(path, props);
    when(contentManager.get(path)).thenReturn(content);

    User contactAuth = mock(User.class);
    HashMap<String, Object> safePropertiesMap = new HashMap<String, Object>();

    safePropertiesMap.put("firstName", "test");
    safePropertiesMap.put("lastName", "user");
    safePropertiesMap.put("id", "myid");

    when(contactAuth.getProperty("firstName")).thenReturn("test");
    when(contactAuth.getSafeProperties()).thenReturn(safePropertiesMap);
    when(authorizableManager.findAuthorizable(eq("user1"))).thenReturn(contactAuth);
    when(repositorySession.adaptTo(Session.class)).thenReturn(session);
    when(session.getContentManager()).thenReturn(contentManager);
    when(session.getAuthorizableManager()).thenReturn(authorizableManager);

    ConnectionIndexingHandler handler = new ConnectionIndexingHandler();
    Event event = new Event("topic", buildEventProperties(path));

    Collection<SolrInputDocument> documents = handler.getDocuments(repositorySession, event);

    assertNotNull(documents);
    assertTrue(!documents.isEmpty());

    Iterator<SolrInputDocument> docIt = documents.iterator();

    assertTrue(docIt.hasNext());

    SolrInputDocument doc = docIt.next();

    assertEquals(content, doc.getField(IndexingHandler._DOC_SOURCE_OBJECT).getValue());
    assertEquals("test", doc.getField("firstName").getValue());
    assertEquals("user", doc.getField("lastName").getValue());
    assertEquals("test-contactstorepath", doc.getField("contactstorepath").getValue());
    assertEquals("test-state", doc.getField("state").getValue());
    assertNull(doc.getField("id"));

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

    ConnectionIndexingHandler handler = new ConnectionIndexingHandler();
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
    props.put("resourceType", ConnectionConstants.SAKAI_CONTACT_RT);
    return props;
  }

}