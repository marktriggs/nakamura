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
package org.sakaiproject.nakamura.docproxy;

import org.apache.commons.io.IOUtils;
import org.sakaiproject.nakamura.api.docproxy.DocProxyException;
import org.sakaiproject.nakamura.api.docproxy.ExternalDocumentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 *
 */
public abstract class AbstractDocumentResult implements ExternalDocumentResult {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDocumentResult.class);
  
  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.docproxy.ExternalDocumentResult#getDocumentInputStream(long, java.lang.String)
   */
  @Override
  public InputStream getDocumentInputStream(long startingAt, String userId)
      throws DocProxyException {
    InputStream is = getDocumentInputStream(userId);
    if (is != null) {
      try {
        long skipped = is.skip(startingAt);
        if (skipped < startingAt) {
          LOGGER.warn("Skipped less bytes than requested. Requested {}, actual {}.", startingAt, skipped);
        }
      } catch (IOException e) {
        IOUtils.closeQuietly(is);
        return null;
      }
    }
    return is;
  }

}
