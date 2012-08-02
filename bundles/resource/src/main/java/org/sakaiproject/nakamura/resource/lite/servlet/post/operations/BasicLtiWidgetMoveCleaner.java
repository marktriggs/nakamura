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
package org.sakaiproject.nakamura.resource.lite.servlet.post.operations;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.servlets.post.Modification;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.ActionRecord;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.resource.MoveCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

/**
 * This cleaner will complete the move of a BasicLTI widget by moving the protected key information
 * of the basic LTI widget to the destination location.
 */
@Component
@Service(value=MoveCleaner.class)
public class BasicLtiWidgetMoveCleaner extends AbstractBasicLtiCleaner {

  private static final Logger LOGGER = LoggerFactory.getLogger(BasicLtiWidgetMoveCleaner.class);
  public static final String BASIC_LTI_WIDGET_RESOURCE_TYPE = "sakai/basiclti";
  public static final String LTI_KEYS_NODE = "ltiKeys";

  @Reference
  protected Repository repository;
  
  @Override
  public List<Modification> doClean(String widgetFromPath, String widgetToPath, Session session)
      throws StorageClientException, AccessDeniedException {
    List<Modification> modifications = new LinkedList<Modification>();
    LOGGER.debug("Cleaning a BasicLTI widget after move from '{}' to '{}'", widgetFromPath, widgetToPath);
    String ltiKeyFromPath = getLtiKeyNode(widgetFromPath);
    String ltiKeyToPath = getLtiKeyNode(widgetToPath);
    
    boolean keysWereMoved = false;
    Session adminSession = null;
    try {
      adminSession = repository.loginAdministrative();
      ContentManager adminContentManager = adminSession.getContentManager();
      if (adminContentManager.exists(ltiKeyFromPath)) {
        List<ActionRecord> moves = adminContentManager.move(ltiKeyFromPath, ltiKeyToPath, true);
        keysWereMoved = (moves != null && !moves.isEmpty());
      }
    } finally {
      if (adminSession != null) {
        adminSession.logout();
      }
    }
    
    // We may have gained sensitive path information from the admin session while moving nodes.
    // Lets only expose the fact that a root ltiKeys node was moved, just in case it had children.
    if (keysWereMoved) {
      LOGGER.debug("Moved protected key from source to destination.");
      modifications.add(Modification.onMoved(ltiKeyFromPath, ltiKeyToPath));
    } else {
      LOGGER.debug("No protected keys were moved from source to destination.");
    }
    
    return modifications;
  }
  
  @Override
  protected Repository getRepository() {
    return repository;
  }

}
