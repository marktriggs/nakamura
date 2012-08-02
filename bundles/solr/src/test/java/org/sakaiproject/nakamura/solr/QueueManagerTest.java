package org.sakaiproject.nakamura.solr;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.CoreDescriptor;
import org.apache.solr.core.SolrConfig;
import org.apache.solr.core.SolrCore;
import org.apache.solr.schema.IndexSchema;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.solr.IndexingHandler;
import org.sakaiproject.nakamura.api.solr.RepositorySession;
import org.sakaiproject.nakamura.api.solr.SolrServerService;
import org.xml.sax.InputSource;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Properties;

/**
 * User: duffy
 * Date: 5/3/12
 * Time: 3:56 PM
 */
public class QueueManagerTest {

  protected QueueManager qMgr;

  protected QueueManagerDriver qMgrDrvr;
  protected SolrServerService solrServerService;
  protected IndexingHandler indexingHandler;
  protected SolrServer server, serverSpy;

  /**
   * A simple IndexingHandler that produces very simple SolrInputDocuments
   */
  class TestIndexingHandler implements IndexingHandler {

    @Override
    public Collection<SolrInputDocument> getDocuments(RepositorySession repositorySession, Event event) {
      SolrInputDocument doc = new SolrInputDocument();

      doc.setField("id", event.getProperty("path"));
      doc.setField("event", event.getTopic());
      doc.setField("field1", event.getProperty("field1"));
      doc.setField("field2", event.getProperty("field2"));

      ArrayList<SolrInputDocument> docs = new ArrayList<SolrInputDocument>();

      docs.add(doc);

      return docs;
    }

    @Override
    public Collection<String> getDeleteQueries(RepositorySession respositorySession, Event event) {
      return null;
    }
  }

  @Before
  public void setupQueueManager() throws Exception {

    // configure a Solr server using config files from test/resources
    CoreContainer container = new CoreContainer();
    // these config files are *not* production files - we are testing QueueManager, not the schema
    SolrConfig config = new SolrConfig(".", "target/test-classes/solrconfig.xml", null);
    InputStream is = new FileInputStream("target/test-classes/schema.xml");
    InputSource iSource = new InputSource(is);
    IndexSchema schema = new IndexSchema(config, "test", iSource);
    CoreDescriptor descriptor = new CoreDescriptor(container, "test", "target/queueManagerTest/solr");
    SolrCore core = new SolrCore("test", "target/queueManagerTest/solr/data", config, schema, descriptor);

    container.register("test", core, false);

    server = new EmbeddedSolrServer(container, "test");

    // create a Mockito Spy object so we can intercept calls to the SolrServer to alter behavior as needed
    serverSpy = spy(server);

    // wire up all the Mocks needed by QueueManager
    qMgrDrvr = mock(QueueManagerDriver.class);
    solrServerService = mock(SolrServerService.class);
    indexingHandler = new TestIndexingHandler();

    HashSet<IndexingHandler> handlerSet = new HashSet<IndexingHandler>();
    handlerSet.add(indexingHandler);
    Collection<IndexingHandler> handlers = handlerSet;

    when(solrServerService.getUpdateServer()).thenReturn(serverSpy);

    Repository repo = mock(Repository.class);
    Session session = mock(Session.class);
    EventAdmin eventAdmin = mock(EventAdmin.class);
    when(repo.loginAdministrative()).thenReturn(session);
    when(qMgrDrvr.getSolrServerService()).thenReturn(solrServerService);
    when(qMgrDrvr.getTopicHandler(anyString())).thenReturn(handlers);
    when(qMgrDrvr.getSparseRepository()).thenReturn(repo);
    when(qMgrDrvr.getEventAdmin()).thenReturn(eventAdmin);

    qMgr = new QueueManager(qMgrDrvr, "target/queueManagerTest/indexQueues", "testQueue", true, 10, 5000);
  }

  /**
   * provide a mix-in interface for determining how many times a method was called
   */
  interface CallCountingAnswer extends Answer {
    int getCallCount();
  }

  private void registerAddMethodMock() {

  }

  /**
   * generate a number of events for indexing
   * @param num
   * @throws IOException
   */
  protected void fireEvents (int num) throws IOException {
    Event indexEvent = null;

    for (int i = 0; i < num; i++) {
      Properties props = new Properties();
      props.setProperty("path", "event" + i + "path");
      props.setProperty("field1", "event" + i + "field1value");
      props.setProperty("field2", "event" + i + "field2value");
      indexEvent = new Event("topic" + i, (Dictionary)props);
      qMgr.saveEvent(indexEvent);
    }
  }

  @Test
  public void testEventsPersistedAcrossConnectException() throws Exception {

    // final reference to be used within anonymous inner classes
    final QueueManager qm = qMgr;

    /*
      implements a callback object for handling calls to add(Collection<SolrInputDoc>)
      it will count the number of times add(...) has been called and will throw a ConnectException once
        during the batch
     */
    final CallCountingAnswer addDocsAnswer = new CallCountingAnswer() {
      int count = 0;

      @Override
      public int getCallCount() {
        return count;
      }

      @Override
      public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
        ++count;

        if (count == 6) {
          throw new SolrServerException(new ConnectException("try again"));
        }

        return invocationOnMock.callRealMethod();
      }
    };

    // register the addDocsAnswer callback to handle any SolrServer.add(...) calls
    doAnswer(addDocsAnswer).when(serverSpy).add(any(Collection.class));

    // load up 10 events
    fireEvents(10);

    // create a thread that will stop QueueManager after SolrServer.add(...) has been called 10 times
    (new Thread (new Runnable () {

      @Override
      public void run() {
        int internalCount = 0;

        while (addDocsAnswer.getCallCount() < 11 && internalCount < 20) {
          try {
            Thread.sleep(500);
          } catch (InterruptedException e) {
          }
          internalCount++;
        }

        if (internalCount >= 20) {
          fail("QueueManager did not finish processing - test timed out instead. Increase max loop count for test if this is not an error");
        }
        try {
          qm.stop();
        } catch (IOException e) {

        }
      }
    })).start();

    // start the QueueManager
    qm.start();

    // wait until the QueueManager is stopped
    qm.getQueueDispatcher().join();

    // the actual test: did all 10 issues get committed to Solr?
    ModifiableSolrParams params = new ModifiableSolrParams();
    params.set("q","*:*");

    QueryResponse response = server.query(params);

    int size = response.getResults().size();
    assertEquals (10, size);
  }
}
