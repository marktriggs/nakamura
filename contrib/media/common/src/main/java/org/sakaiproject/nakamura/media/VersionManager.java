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
 * specific language governing permissions and limitations
 * under the License.
 */
package org.sakaiproject.nakamura.media;


import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;

import org.sakaiproject.nakamura.api.files.FilesConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

/**
 * Manage versions of media related to an instance of content.
 */
class VersionManager {

  private ContentManager contentManager;


  public VersionManager(ContentManager cm) {
    this.contentManager = cm;
  }


  public Version getLatestVersionOf(String pid) throws StorageClientException, AccessDeniedException {
    List<Version> versions = getVersionsMetadata(pid);
    return versions.get(versions.size() - 1);
  }


  // Get the versions metadata for 'pid' from the oldest version to newest.
  public List<Version> getVersionsMetadata(String pid) throws StorageClientException, AccessDeniedException {

    Content latest = contentManager.get(pid);
    if (latest == null) {
      return Collections.emptyList();
    }

    List<Content> versions = new ArrayList<Content>();
    versions.add(latest);

    for (String versionId : contentManager.getVersionHistory(pid)) {
      versions.add(contentManager.getVersion(pid, versionId));
    }

    // So this is strange.  Each object in the version history list has the
    // right binary blob for its version, but its metadata corresponds to
    // the previous version.  For example, version 5 will have the bytes of
    // the fifth uploaded file, but the title/description/etc. of the 4th
    // version.
    List<Version> result = new ArrayList<Version>();
    for (int i = 1; i < versions.size(); i++) {
      Content current = versions.get(i);
      Content last = versions.get(i - 1);

      String[] tags = (String[])last.getProperty("sakai:tags");

      List<String> tagList = new ArrayList<String>();

      if (tags != null) {
        for (String tag : tags) {
          tagList.add(tag);
        }
      }

      String owner = (String)last.getProperty("sakai:pool-content-created-for");

      if (owner != null) {
        tagList.add("user-" + owner);
      }

      result.add(new Version(pid,
              (String)current.getProperty("_id"),
              (String)last.getProperty("sakai:pooled-content-file-name"),
              (String)last.getProperty("sakai:description"),
              (String)current.getProperty("media:extension"),
              (String)current.getProperty(FilesConstants.POOLED_CONTENT_MIMETYPE),
              (String)current.getProperty("media:tempStoreLocation"),
              tagList.toArray(new String[0]),
              contentManager));
    }

    Collections.reverse(result);

    return result;
  }
}

