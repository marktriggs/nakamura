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
package org.sakaiproject.nakamura.files.migrator;

import com.google.common.collect.ImmutableMap;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sanselan.ImageInfo;
import org.apache.sanselan.Sanselan;
import org.apache.sanselan.util.IOUtils;
import org.sakaiproject.nakamura.api.files.FilesConstants;
import org.sakaiproject.nakamura.api.lite.PropertyMigrator;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.util.SparseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Map;


/**
 * A migrator that checks for and flags images that need to be re-processed by the preview processor.
 *
 * Before this migrator (v1.4.0), some image previews were generated sub-optimally by the preview processor.
 * For the specific details, see:
 * https://jira.sakaiproject.org/browse/KERN-2865
 * https://jira.sakaiproject.org/browse/KERN-2883
 *
 * Since these defects were fixed in the preview processor, images that meet certain criteria should be
 * flagged with our special property: sakai:needsprocessing = true
 *
 * The preview processor will then re-generate better preview images for that content.
 */
@Service
@Component
public class ReprocessImagesMigrator implements PropertyMigrator {

  private final static Logger LOGGER = LoggerFactory.getLogger(ReprocessImagesMigrator.class);

  private static final String JPEG_MIMETYPE = "image/jpeg";
  public static final int PREVIEW_PROCESSOR_NORMAL_WIDTH = 900;

  @Reference
  private Repository sparseRepository;

  @Override
  public boolean migrate(String rowId, Map<String, Object> properties) {
    boolean changeMade = false;
    if (properties != null && isImage(properties) &&
        (isNotJpeg((String) properties.get(Content.MIMETYPE_FIELD)) || isSmall(properties))) {
      properties.put(FilesConstants.POOLED_NEEDS_PROCESSING, Boolean.TRUE);
      changeMade = true;
    }
    return changeMade;
  }

  private boolean isSmall(Map<String, Object> contentProperties) {
    boolean isSmall = false;
    String imagePath = (String) contentProperties.get(Content.PATH_FIELD);
    Session adminSession = null;
    try {
      adminSession = sparseRepository.loginAdministrative();
      ContentManager contentManager = adminSession.getContentManager();
      InputStream inputStream = contentManager.getInputStream(imagePath);
      byte[] bytes = IOUtils.getInputStreamBytes(inputStream);
      ImageInfo info = Sanselan.getImageInfo(bytes);
      isSmall = info.getWidth() < PREVIEW_PROCESSOR_NORMAL_WIDTH;
    } catch (Exception e) {
      LOGGER.error("Error determining image size of {}", imagePath);
    } finally {
      if (adminSession == null) {
        SparseUtils.logoutQuietly(adminSession);
      }
    }
    return isSmall;
  }

  private boolean isNotJpeg(String mimeType) {
    return mimeType == null || !JPEG_MIMETYPE.equals(mimeType);
  }

  private boolean isImage(Map<String, Object> contentProperties) {
    return FilesConstants.POOLED_CONTENT_RT.equals(contentProperties.get("sling:resourceType"))
        && contentProperties.get(Content.MIMETYPE_FIELD) != null
        && ((String)contentProperties.get(Content.MIMETYPE_FIELD)).startsWith("image/");
  }

  @Override
  public String[] getDependencies() {
    return new String[0];
  }

  @Override
  public String getName() {
    return getClass().getName();
  }

  @Override
  public Map<String, String> getOptions() {
    return ImmutableMap.of(PropertyMigrator.OPTION_RUNONCE, Boolean.TRUE.toString());
  }
}
