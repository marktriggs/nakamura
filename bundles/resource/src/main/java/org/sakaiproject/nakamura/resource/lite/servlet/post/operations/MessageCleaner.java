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
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.resource.CopyCleaner;
import org.sakaiproject.nakamura.api.resource.MoveCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 * The MessageCleaner class verifies that loosely-dependent properties of messages are reset
 * to appropriate values when messages are copied from one location to another. One concrete
 * example is when a sakai/message node references its parent "sakai:messagestore" path. After
 * a messagestore and all its messages are copied, the child messages may no longer reference the
 * appropriate messagestore.
 * </p>
 * <p>
 * Given the way sakai/message nodes are associated to the messagestore (i.e., by reference and not
 * by natural hierarchy), we need to account for several cases:
 * <ul>
 *  <li>The messagestore and all its "child" messages (by hierarchy) are copied to a new location</li>
 *  <li>A "disjoint" messagestore that is referenced by message nodes elsewhere was copied</li>
 *  <li>A "disjoint" message node, whose associated messagestore is *not* a parent, was copied elsewhere</li>
 * </ul>
 * </p>
 * <p>
 * This implementation of the cleaner makes the following assertions:
 * <ul>
 *  <li>If a message is copied to a child of a message store, its messagestore reference should be
 *  that of its parent.</li>
 *  <li>If a message is copied to a new location that is disjoint from any parent messagestore, its
 *  messagestore reference should be left as it was.</li>
 *  <li>If a "disjoint" messagestore (i.e., no message children) is copied to a new location, any
 *  messages referencing the source are not copied and are not re-referenced.</li>
 * </p>
 * <p>
 * That said, the algorithm to clean a copied message object is as follows:
 * <ol>
 *  <li>If the new copied message does not have a parent node of type "sakai/messagestore", do not
 *  change its copied sakai:messagestore property value</li>
 *  <li>If the new copied message DOES have a parent node of type "sakai/messagestore", set the
 *  message's sakai:messagestore property value to the path of its parent.</li>
 *  <li>No CopyCleaner needs to be created for the sakai/messagestore resource type, because there
 *  is no circumstance under which a copy of a single messagestore element (that did not also copy
 *  child messages), should result in a clean operation. Clean operations only occur as a result of
 *  copying child messages.</li>
 * </ol>
 * </p>
 *  
 */
@Component
@Service(value={ CopyCleaner.class, MoveCleaner.class })
public class MessageCleaner implements CopyCleaner, MoveCleaner {

  private static final Logger LOGGER = LoggerFactory.getLogger(MessageCleaner.class);
  private static final String MESSAGESTORE_PROP = "sakai:messagestore";
  private static final String MESSAGE_RESOURCE_TYPE = "sakai/message";
  private static final String MESSAGESTORE_RESOURCE_TYPE = "sakai/messagestore";

  @Reference
  protected Repository repository;
  
  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.resource.CopyCleaner#clean(java.lang.String, java.lang.String, org.sakaiproject.nakamura.api.lite.Session)
   */
  @Override
  public List<Modification> clean(String fromPath, String toPath, Session session)
      throws StorageClientException, AccessDeniedException {
    ContentManager cm = session.getContentManager();
    Content toContent = cm.get(toPath);
    if (toContent != null && isMessage(toContent)) {
      LOGGER.debug("Cleaning a copy of message content from '{}' to '{}'", fromPath, toPath);
      if (toContent.hasProperty(MESSAGESTORE_PROP)) {
        Content newMessagestore = findParentMessageStore(toContent, cm);
        // only re-reference the messagestore property if there is a messagestore parent
        if (newMessagestore != null) {
          LOGGER.debug("Converting copied messagestore value to '{}'", newMessagestore.getPath());
          
          // since copying the content does not imply that we are able to write to it (i.e. change
          // the message store), we do this messagestore fix in admin session so that we may still
          // maintain the integrity of the message data after the copy.
          Session adminSession = null;
          try {
            adminSession = repository.loginAdministrative();
            ContentManager adminContentManager = adminSession.getContentManager();
            toContent.setProperty(MESSAGESTORE_PROP, String.format("%s/", newMessagestore.getPath()));
            adminContentManager.update(toContent);
          } finally {
            if (adminSession != null) {
              adminSession.logout();
            }
          }
          
          return Arrays.asList(Modification.onModified(toPath));
        } else {
          LOGGER.warn("Did not find messagestore for copied message '{}'", toPath);
        }
      }
    }
    return Collections.emptyList();
  }

  /**
   * Given a message content, traverse up the tree to find the parent message-store, if any.
   * 
   * @param message
   * @param contentManager
   * @return The message store (sakai/messagestore) that stores this message, or {@code null} if
   * none is found.
   */
  private Content findParentMessageStore(Content message, ContentManager contentManager) {
    try {
      Content parent = getParent(message, contentManager);
      while (parent != null) {
        if (isMessagestore(parent)) {
          return parent;
        }
        parent = getParent(parent, contentManager);
      }
    } catch (StorageClientException e) {
      LOGGER.warn("Error when trying to lookup parent message store.", e);
    } catch (AccessDeniedException e) {
      LOGGER.warn("Error when trying to lookup parent message store.", e);
    }
    return null;
  }
  
  /**
   * Get the parent path object of the given content. If there is no parent, null will be returned.
   * 
   * @param content
   * @param contentManager
   * @return The parent of the given {@code content}. {@code null} if there is one (e.g., it is the root).
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  private Content getParent(Content content, ContentManager contentManager) throws StorageClientException,
      AccessDeniedException {
    if ("/".equals(content.getPath())) {
      return null;
    }
    String parentPath = StorageClientUtils.getParentObjectPath(content.getPath());
    return contentManager.get(parentPath);
  }
  
  /**
   * Determine if the given content is a messagestore node.
   * 
   * @param content
   * @return
   */
  private boolean isMessagestore(Content content) {
    return MESSAGESTORE_RESOURCE_TYPE.equals(content.getProperty(Content.SLING_RESOURCE_TYPE_FIELD));
  }
  
  /**
   * Determine if the given content is a message node.
   * 
   * @param content
   * @return
   */
  private boolean isMessage(Content content) {
    return MESSAGE_RESOURCE_TYPE.equals(content.getProperty(Content.SLING_RESOURCE_TYPE_FIELD));
  }
  
}
