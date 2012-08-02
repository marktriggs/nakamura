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
package org.sakaiproject.nakamura.resource.lite;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.servlets.post.Modification;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.ActionRecord;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.resource.CopyCleaner;
import org.sakaiproject.nakamura.api.resource.MoveCleaner;
import org.sakaiproject.nakamura.api.resource.lite.SparsePostOperationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Default implementation of the SparsePostOperationService.
 * 
 * @see SparsePostOperationService
 */
@Component
@Service
public class SparsePostOperationServiceImpl implements SparsePostOperationService {

  private final static Logger LOGGER = LoggerFactory.getLogger(SparsePostOperationServiceImpl.class);

  @Reference(cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE,
      policy = ReferencePolicy.DYNAMIC, referenceInterface = CopyCleaner.class,
      bind = "bindCopyCleaner", unbind = "unbindCopyCleaner")
  protected CopyOnWriteArrayList<CopyCleaner> copyCleaners;
  
  @Reference(cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE,
      policy = ReferencePolicy.DYNAMIC, referenceInterface = MoveCleaner.class,
      bind = "bindMoveCleaner", unbind = "unbindMoveCleaner")
  protected CopyOnWriteArrayList<MoveCleaner> moveCleaners;
  
  public SparsePostOperationServiceImpl() {
    copyCleaners = new CopyOnWriteArrayList<CopyCleaner>();
    moveCleaners = new CopyOnWriteArrayList<MoveCleaner>();
  }
  
  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.resource.lite.SparsePostOperationService#copy(org.sakaiproject.nakamura.api.lite.content.ContentManager, java.lang.String, java.lang.String)
   */
  @Override
  public List<Modification> copy(Session session, String from, String to)
      throws StorageClientException, AccessDeniedException, IOException {
    LOGGER.debug("Copying resource tree from '{}' to '{}'", from, to);
    ContentManager contentManager = session.getContentManager();
    if (contentManager.exists(to)) {
      throw new StorageClientException("Copy destination already exists.");
    }

    List<Modification> changes = new LinkedList<Modification>();
    
    // convert all ActionRecord objects into Modification copy objects
    List<ActionRecord> copies = StorageClientUtils.copyTree(contentManager, from, to, true);
    for (ActionRecord copy : copies) {
      changes.add(Modification.onCopied(copy.getFrom(), copy.getTo()));
    }
    
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Copied {} items", String.valueOf(copies.size()));
    }
    
    //Apply all cleaners to all actions that were performed in this request.
    List<Modification> copyChanges = new LinkedList<Modification>();
    if (copyCleaners != null && !copyCleaners.isEmpty()) {
      for (Modification modification : changes) {
        for (CopyCleaner cleaner : copyCleaners) {
          List<Modification> currentCleanerChanges = cleaner.clean(modification.getSource(),
              modification.getDestination(), session);
          if (currentCleanerChanges != null) {
            copyChanges.addAll(currentCleanerChanges);
          }
        }
      }
    }
    changes.addAll(copyChanges);
    return changes;
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.resource.lite.SparsePostOperationService#move(org.sakaiproject.nakamura.api.lite.Session, java.lang.String, java.lang.String, boolean, boolean)
   */
  @Override
  public List<Modification> move(Session session, String from, String to, boolean replace,
      boolean keepDestinationHistory) throws StorageClientException, AccessDeniedException {
    LOGGER.debug("Moving resource tree from '{}' to '{}'", from, to);
    ContentManager contentManager = session.getContentManager();
    List<Modification> changes = new LinkedList<Modification>();
    List<ActionRecord> moves = contentManager.move(from, to, replace, keepDestinationHistory);
    for (ActionRecord move : moves) {
      changes.add(Modification.onMoved(move.getFrom(), move.getTo()));
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Moved {} items", String.valueOf(moves.size()));
    }
    
    List<Modification> moveChanges = new LinkedList<Modification>();
    // if there are cleaners, see if one can handle this content
    if (!moveCleaners.isEmpty()) {
      // get the content from the new location and clean it before returning
      for (MoveCleaner cleaner : moveCleaners) {
        for (Modification change : changes) {
          // consult each cleaner and collect the modifications
          List<Modification> mods = cleaner.clean(change.getSource(), change.getDestination(),
              session);
          if (mods != null) {
            moveChanges.addAll(mods);
          }
        }
      }
    }
    changes.addAll(moveChanges);
    return changes;
  }

  protected void bindCopyCleaner(CopyCleaner cleaner) {
    copyCleaners.add(cleaner);
  }

  protected void unbindCopyCleaner(CopyCleaner cleaner) {
    copyCleaners.remove(cleaner);
  }
  
  protected void bindMoveCleaner(MoveCleaner cleaner) {
    moveCleaners.add(cleaner);
  }

  protected void unbindMoveCleaner(MoveCleaner cleaner) {
    moveCleaners.remove(cleaner);
  }
}
