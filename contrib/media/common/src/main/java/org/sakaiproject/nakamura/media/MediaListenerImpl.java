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
 * specific language governing permissions and limitations under
 * the License.
 */
package org.sakaiproject.nakamura.media;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.util.Map;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.api.request.RequestParameter;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.sakaiproject.nakamura.api.media.MediaService;
import org.sakaiproject.nakamura.api.activemq.ConnectionFactoryService;
import org.sakaiproject.nakamura.api.files.FileUploadHandler;
import org.sakaiproject.nakamura.api.files.FileUploadFilter;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.media.MediaListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/*
 * use immediate = true to start the worker pool up during server start rather than leaving that
 * for the first caller to incur the cost of doing.
 */
@Component(immediate = true, metatype = true)
@Service({ MediaListener.class, EventHandler.class, FileUploadHandler.class, FileUploadFilter.class })
@Properties({
  @Property(name = "event.topics", value = {"org/sakaiproject/nakamura/lite/content/UPDATED", "org/sakaiproject/nakamura/lite/content/DELETE", "org/sakaiproject/nakamura/media/UPDATED"})
})
public class MediaListenerImpl implements MediaListener, EventHandler, FileUploadHandler, FileUploadFilter {
  static final int MAX_RETRIES_DEFAULT = 5;
  @Property(intValue = MAX_RETRIES_DEFAULT)
  public static final String MAX_RETRIES = "maxRetries";

  static final int RETRY_MS_DEFAULT = 5 * 60 * 1000;
  @Property(intValue = RETRY_MS_DEFAULT)
  public static final String RETRY_MS = "retryMs";

  static final int WORKER_COUNT_DEFAULT = 5;
  @Property(intValue = WORKER_COUNT_DEFAULT)
  public static final String WORKER_COUNT = "workerCount";

  static final int POLL_FREQUENCY_DEFAULT = 5000;
  @Property(intValue = POLL_FREQUENCY_DEFAULT)
  public static final String POLL_FREQUENCY = "pollFrequency";

  @Property(description = "The directory path to store uploaded media files while they're being processed.  If you leave this blank, defaults to the 'java.io.tmpdir' property (which is usually /tmp)")
  public static final String MEDIA_TEMPDIR = "mediaTempDirectory";



  private static final Logger LOGGER = LoggerFactory.getLogger(MediaListenerImpl.class);

  private String QUEUE_NAME = "MEDIA";

  @Reference
  private ConnectionFactoryService connectionFactoryService;
  private ConnectionFactory connectionFactory;

  @Reference
  protected Repository sparseRepository;

  @Reference
  protected MediaService mediaService;


  private MediaCoordinator mediaCoordinator;
  private MediaTempFileStore mediaTempStore;


  private int maxRetries;
  private int retryMs;
  private int workerCount;
  private int pollFrequency;
  private String mediaTempDirectory;

  @Activate
  @Modified
  protected void activate(Map<?, ?> props) {
    LOGGER.debug("Activating Media bundle");

    connectionFactory = connectionFactoryService.getDefaultPooledConnectionFactory();

    maxRetries = PropertiesUtil.toInteger(props.get(MAX_RETRIES), MAX_RETRIES_DEFAULT);
    retryMs = PropertiesUtil.toInteger(props.get(RETRY_MS), RETRY_MS_DEFAULT);
    workerCount = PropertiesUtil.toInteger(props.get(WORKER_COUNT), WORKER_COUNT_DEFAULT);
    pollFrequency = PropertiesUtil.toInteger(props.get(POLL_FREQUENCY), POLL_FREQUENCY_DEFAULT);

    mediaTempDirectory = PropertiesUtil.toString(props.get(MEDIA_TEMPDIR), "");

    mediaTempDirectory = mediaTempDirectory.trim();

    if ("".equals(mediaTempDirectory)) {
      mediaTempDirectory = System.getProperty("java.io.tmpdir");
    }

    LOGGER.info("Using temporary directory for media files: {}", mediaTempDirectory);

    mediaTempStore = new MediaTempFileStore(mediaTempDirectory);

    mediaCoordinator = new MediaCoordinator(connectionFactory, QUEUE_NAME,
                                            sparseRepository, mediaTempStore,
                                            mediaService, maxRetries,
                                            retryMs, workerCount, pollFrequency);
    mediaCoordinator.start();
  }

  @Deactivate
  protected void deactivate(ComponentContext context) {
    LOGGER.info("Deactivating Media bundle");

    if (mediaCoordinator != null) {
      LOGGER.info("Shutting down Media coordinator thread...");
      mediaCoordinator.shutdown();
      LOGGER.info("Done");
    }
  }

  // --------------- MediaListener interface -----------------------------------
  @Override
  public void contentUpdated(String pid) {
    LOGGER.debug("Content updated: {}", pid);

    try {
      Connection conn = connectionFactory.createConnection();
      Session jmsSession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Queue mediaQueue = jmsSession.createQueue(QUEUE_NAME);

      MessageProducer producer = jmsSession.createProducer(mediaQueue);

      producer.send(MediaUtils.message(jmsSession, "pid", pid));

      producer.close();
      jmsSession.close();
      conn.close();

    } catch (JMSException e) {
      LOGGER.error("Failed when adding content PID to JMS queue: {}", e);
    }
  }

  // --------------- EventHandler interface -----------------------------------
  /**
   * Handle updated & delete events for content and add a message to a queue we control
   * separately.
   *
   * {@inheritDoc}
   *
   * @see org.osgi.service.event.EventHandler#handleEvent(org.osgi.service.event.Event)
   */
  @Override
  public void handleEvent(Event event) {
    LOGGER.debug("Got event: {}", event);

    String path = (String) event.getProperty("path");
    String op = (String) event.getProperty("op");
    String resourceType = (String) event.getProperty("resourceType");

    // We're only interested in top-level content PIDs here. Don't bother
    // us about /activity nodes or other children.
    if (path.indexOf("/") == -1 && "sakai/pooled-content".equals(resourceType)
        && (op == null || "update".equals(op))) {
      contentUpdated(path);
    }
  }

  // --------------- FileUploadHandler interface ------------------------------
  @Override
  public void handleFile(Map<String, Object> results, String poolId,
      InputStream inputStream, String userId, boolean isNew) throws IOException {

    mediaCoordinator.maybeMarkAsMedia(poolId);

    contentUpdated(poolId);
  }


  // --------------- FileUploadFilter interface ------------------------------  

  @Override
  public InputStream filterInputStream(String path, InputStream inputStream, String contentType, RequestParameter value) {

    if (mediaCoordinator.isAcceptedMediaType(contentType)) {
      // We're going to handle this file, but we want to prevent it from being
      // permanently stored in the repository since we're just going to upload
      // it to the remote media service anyway.

      LOGGER.debug("Returning empty InputStream");

      try {
        String tempVersion = mediaTempStore.store(inputStream, path);
        mediaCoordinator.recordTempVersion(path, tempVersion);
      } catch (FileNotFoundException e) {
        LOGGER.warn("Couldn't store temporary file: {}", e);
        return inputStream;
      } catch (IOException e) {
        LOGGER.warn("Couldn't store temporary file: {}", e);
        return inputStream;
      }

      try {
        inputStream.close();
      } catch (IOException ex) {
        LOGGER.warn("Got IOException when closing original InputStream");
      }

      return new ByteArrayInputStream("".getBytes());
    }

    return inputStream;
  }
}
