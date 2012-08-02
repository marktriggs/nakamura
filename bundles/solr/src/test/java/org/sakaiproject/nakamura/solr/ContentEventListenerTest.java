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
package org.sakaiproject.nakamura.solr;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.common.SolrInputDocument;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.osgi.service.event.Event;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.solr.IndexingHandler;
import org.sakaiproject.nakamura.api.solr.QoSIndexHandler;
import org.sakaiproject.nakamura.api.solr.RepositorySession;
import org.sakaiproject.nakamura.api.solr.SolrServerService;
import org.sakaiproject.nakamura.api.solr.TopicIndexer;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.UUID;

import javax.xml.parsers.ParserConfigurationException;

public class ContentEventListenerTest {

  private static final Logger LOGGER = LoggerFactory
      .getLogger(ContentEventListener.class);
  public static final String TEST_TOPIC = "test/topic";
  public static final String TARGET_SOLRTEST = "target/solrtest";

  @Mock
  private SolrServerService solrServerService;
  @Mock
  private SolrServer server;
  private BaseMemoryRepository baseMemoryRepository;
  private ContentEventListener contentEventListener;
  private IndexingHandler defaultIndexingHandler;

  public ContentEventListenerTest() throws IOException, ParserConfigurationException,
      SAXException, ClientPoolException, StorageClientException, AccessDeniedException, ClassNotFoundException {
    MockitoAnnotations.initMocks(this);
  }

  @Before
  public void setup() throws Exception {
    baseMemoryRepository = new BaseMemoryRepository();
    contentEventListener = new ContentEventListener();
    contentEventListener.sparseRepository = baseMemoryRepository.getRepository();

    contentEventListener.solrServerService = solrServerService;
    Mockito.when(solrServerService.getSolrHome()).thenReturn(TARGET_SOLRTEST);
    Mockito.when(solrServerService.getServer()).thenReturn(server);

    defaultIndexingHandler =  new IndexingHandler() {

      public Collection<SolrInputDocument> getDocuments(RepositorySession repositorySession, Event event) {
        return new ArrayList<SolrInputDocument>();
      }

      public Collection<String> getDeleteQueries(RepositorySession repositorySession, Event event) {
        return new ArrayList<String>();
      }

    };

    contentEventListener.addHandler(TEST_TOPIC, defaultIndexingHandler);
  }

  @After
  public void teardown() throws Exception {
    contentEventListener.closeAll();
    LOGGER.info("Done adding Events ");

    contentEventListener.removeHandler("/test/topic", defaultIndexingHandler);

    contentEventListener.deactivate(null);

    LOGGER.info("Waiting for worker thread ");
    contentEventListener.joinAll();
    LOGGER.info("Joined worker thread");
  }

  protected void assertHasEvent (String solrHome, String queueName, String uniqueValue) throws Exception {
    File queueDir = new File (solrHome, "/indexq-" + queueName);

    File queueFiles[] = queueDir.listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File file, String s) {
        return (!".".equals(s) && !"..".equals(s));
      }
    });

    for (File queueFile : queueFiles) {
      FileReader fr = null;
      try {
        fr = new FileReader(queueFile);
        BufferedReader reader = new BufferedReader(fr);
        String line = null;

        while ((line = reader.readLine()) != null) {
          if (line.contains(uniqueValue)) {
            return;
          }
        }
      }
      finally {
        if (fr != null) {
          fr.close();
        }
      }
    }

    fail ("did not find value \"" + uniqueValue + "\" in queue \"" + queueName + "\"");
  }

  protected String getUniqueValue() {
    return UUID.randomUUID().toString();
  }

  protected void testTtlLandsInQueue (String ttl, Dictionary<String, Object> extraProps,
     String queue) throws Exception {
    String uniqueValue = getUniqueValue();

    Dictionary<String, Object> evtProps = new Hashtable<String, Object>();

    evtProps.put(TopicIndexer.TTL, ttl);
    evtProps.put("uinqueValue", uniqueValue);

    if (extraProps != null) {
      final Enumeration<String> keys = extraProps.keys();
      while (keys.hasMoreElements()) {
        final String key = keys.nextElement();
        evtProps.put(key, extraProps.get(key));
      }
    }

    Event testEvent = new Event(TEST_TOPIC, evtProps);

    contentEventListener.handleEvent(testEvent);

    assertHasEvent(TARGET_SOLRTEST, queue, uniqueValue);
  }

  class QoSHandler implements IndexingHandler, QoSIndexHandler {


    public Collection<SolrInputDocument> getDocuments(RepositorySession repositorySession, Event event) {
      return new ArrayList<SolrInputDocument>();
    }

    public Collection<String> getDeleteQueries(RepositorySession repositorySession, Event event) {
      return new ArrayList<String>();
    }

    @Override
    public int getTtl(Event event) {
      if (Boolean.TRUE.equals(event.getProperty("qos"))) {
        return 30;
      } else {
        return 5000;
      }
    }
  }
  @Test
  public void testTtl() throws Exception {
    String queueDefn[] = new String[] {
       "name=loafing;batch-delay=200;batched-index-size=100;near-real-time=false",
       "name=moderate;batch-delay=100;batched-index-size=100;near-real-time=false",
       "name=zippy;batch-delay=50;batched-index-size=10;near-real-time=true"
    };

    Map<String, Object> props = new HashMap<String, Object>();
    props.put("queue-config", queueDefn);

    contentEventListener.activate(props);
    contentEventListener.closeAll();

    testTtlLandsInQueue("30", null, "zippy");
    testTtlLandsInQueue("51", null, "zippy");
    testTtlLandsInQueue("100", null, "moderate");
    testTtlLandsInQueue("5000", null, "loafing");

    Dictionary<String, Object> triggerQoS = new Hashtable<String, Object>();
    triggerQoS.put("qos", Boolean.TRUE);

    QoSHandler qosHandler = new QoSHandler();
    contentEventListener.addHandler(TEST_TOPIC, qosHandler);

    testTtlLandsInQueue("1000", triggerQoS, "zippy");
  }

  @Test
  public void testContentEventListener() throws IOException, 
      InterruptedException, ClientPoolException, StorageClientException, AccessDeniedException {
    Map<String, Object> properties = new HashMap<String, Object>();

    contentEventListener.activate(properties);

    for (int j = 0; j < 10; j++) {
      LOGGER.info("Adding Events ");
      for (int i = 0; i < 100; i++) {
        Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put("evnumber", i);
        props.put("a nasty,key", "with a, nasty \n value");
        contentEventListener.handleEvent(new Event(TEST_TOPIC, props));
      }
      Thread.sleep(100);
    }
  }

}
