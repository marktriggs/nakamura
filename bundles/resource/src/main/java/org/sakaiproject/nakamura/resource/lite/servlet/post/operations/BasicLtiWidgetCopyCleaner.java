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
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.ActionRecord;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.resource.CopyCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * This cleaner will complete the copy of a BasicLTI widget by copying the protected key information
 * of the basic LTI widget to the destination location.
 */
@Component
@Service(value=CopyCleaner.class)
public class BasicLtiWidgetCopyCleaner extends AbstractBasicLtiCleaner {

  private static final Logger LOGGER = LoggerFactory.getLogger(BasicLtiWidgetCopyCleaner.class);
  
  @Reference
  protected Repository repository;

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.resource.lite.servlet.post.operations.AbstractBasicLtiCleaner#doClean(java.lang.String, java.lang.String, org.sakaiproject.nakamura.api.lite.Session)
   */
  @Override
  public List<Modification> doClean(String widgetFromPath, String widgetToPath, Session session)
      throws StorageClientException, AccessDeniedException {
    List<Modification> modifications = new LinkedList<Modification>();
    LOGGER.debug("Cleaning a BasicLTI widget after copy from '{}' to '{}'", widgetFromPath, widgetToPath);
    String ltiKeyFromPath = getLtiKeyNode(widgetFromPath);
    String ltiKeyToPath = getLtiKeyNode(widgetToPath);
    
    boolean keysWereCopied = false;
    Session adminSession = null;
    try {
      adminSession = repository.loginAdministrative();
      ContentManager adminContentManager = adminSession.getContentManager();
      
      if (adminContentManager.exists(ltiKeyFromPath)) {
        List<ActionRecord> copies = StorageClientUtils.copyTree(adminContentManager, ltiKeyFromPath, ltiKeyToPath, true);
        keysWereCopied = (copies != null && !copies.isEmpty());
        
        // make sure the copied ltiKeys node is still locked down
        if (keysWereCopied) {
          lockDownKeys(adminSession, widgetToPath, session.getUserId());
        }
      }
    } catch (IOException e) {
      throw new StorageClientException("Exception occurred when copying BasicLTI keys to destination location.", e);
    } finally {
      if (adminSession != null) {
        adminSession.logout();
      }
    }
    
    // We may have gained sensitive path information from the admin session while copying nodes.
    // Lets only expose the fact that a root ltiKeys node was copied, just in case it had children.
    if (keysWereCopied) {
      LOGGER.debug("Copied protected key from source to destination.");
      modifications.add(Modification.onCopied(ltiKeyFromPath, ltiKeyToPath));
    } else {
      LOGGER.debug("No protected keys were copied from source to destination.");
    }
    
    return modifications;
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.resource.lite.servlet.post.operations.AbstractBasicLtiCleaner#getRepository()
   */
  @Override
  protected Repository getRepository() {
    return repository;
  }
}
