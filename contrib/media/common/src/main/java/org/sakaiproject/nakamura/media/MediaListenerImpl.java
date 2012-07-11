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
 * specific language governing permissions and limitations under
 * the License.
 */
package org.sakaiproject.nakamura.media;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Map;

import org.sakaiproject.nakamura.media.util.DurableQueue;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.sakaiproject.nakamura.api.media.MediaService;
import org.sakaiproject.nakamura.api.activemq.ConnectionFactoryService;
import org.sakaiproject.nakamura.api.files.FileUploadHandler;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.media.MediaListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/*
 * use immediate = true to start the worker pool up during server start rather than leaving that
 * for the first caller to incur the cost of doing.
 */
@Component(immediate = true, metatype = true)
@Service({ MediaListener.class, EventHandler.class, FileUploadHandler.class })
@Properties({
  @Property(name = "event.topics", value = "org/sakaiproject/nakamura/lite/content/UPDATED")
})
public class MediaListenerImpl implements MediaListener, EventHandler, FileUploadHandler {
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

  private static final Logger LOGGER = LoggerFactory.getLogger(MediaListenerImpl.class);

  @Reference
  protected Repository sparseRepository;

  @Reference
  protected MediaService mediaService;

  private DurableQueue queue;

  private MediaCoordinator mediaCoordinator;
  private int maxRetries;
  private int retryMs;
  private int workerCount;
  private int pollFrequency;

  @Activate
  @Modified
  protected void activate(Map<?, ?> props) {
    LOGGER.info("Activating Media bundle");

    maxRetries = PropertiesUtil.toInteger(props.get(MAX_RETRIES), MAX_RETRIES_DEFAULT);
    retryMs = PropertiesUtil.toInteger(props.get(RETRY_MS), RETRY_MS_DEFAULT);
    workerCount = PropertiesUtil.toInteger(props.get(WORKER_COUNT), WORKER_COUNT_DEFAULT);
    pollFrequency = PropertiesUtil.toInteger(props.get(POLL_FREQUENCY), POLL_FREQUENCY_DEFAULT);

    try {
      queue = new DurableQueue("/tmp/THISWILLNEEDTOBESOMETHINGBETTERTHANTHIS.txt");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    mediaCoordinator = new MediaCoordinator(queue, sparseRepository, mediaService, maxRetries,
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
    LOGGER.info("Content updated: {}", pid);

    try {
      queue.add(pid);
    } catch (IOException e) {
      LOGGER.error("Failed when adding content PID to queue: {}", e);
      e.printStackTrace();
    }
  }

  // --------------- EventHandler interface -----------------------------------
  @Override
  public void handleEvent(Event event) {
    LOGGER.info("Got event: {}", event);

    String path = (String) event.getProperty("path");
    String op = (String) event.getProperty("op");
    String resourceType = (String) event.getProperty("resourceType");

    // We're only interested in top-level content PIDs here. Don't bother
    // us about /activity nodes or other children.
    if (path.indexOf("/") == -1 && "update".equals(op)
        && "sakai/pooled-content".equals(resourceType)) {

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
}
