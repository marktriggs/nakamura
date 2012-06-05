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
package org.sakaiproject.nakamura.media.brightcove;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.osgi.service.component.ComponentException;
import org.sakaiproject.nakamura.api.media.VideoService;
import org.sakaiproject.nakamura.api.media.VideoServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(metatype = true, policy = ConfigurationPolicy.REQUIRE)
@Service
public class BrightCoveVideoService implements VideoService {

  private static final Logger LOG = LoggerFactory.getLogger(BrightCoveVideoService.class);

  @Property
  public static final String READ_TOKEN = "readToken";

  @Property
  public static final String WRITE_TOKEN = "writeToken";

  @Property(value = "http://api.brightcove.com/services")
  public static final String BASE_URL = "baseUrl";

  String readToken;
  String writeToken;
  String baseUrl;
  String libraryUrl;
  String postUrl;

  private HttpClient client;

  public BrightCoveVideoService() {
    client = new HttpClient();
  }

  @Activate
  @Modified
  protected void activate(Map<?, ?> props) {
    readToken = PropertiesUtil.toString(props.get(READ_TOKEN), null);
    // require readToken
    if (StringUtils.isBlank(readToken)) {
      throw new ComponentException("'readToken' required to communicate with BrightCove");
    }

    writeToken = PropertiesUtil.toString(props.get(WRITE_TOKEN), null);
    // if writeToken is blank, use readToken
    if (StringUtils.isBlank(writeToken)) {
      throw new ComponentException("'writeToken' required to communicate with BrightCove");
    }

    baseUrl = PropertiesUtil.toString(props.get(BASE_URL), null);
    // require baseUrl
    if (StringUtils.isBlank(baseUrl)) {
      throw new ComponentException("'baseUrl' required to communicate with BrightCove");
    }
    libraryUrl = String.format("%s/library", baseUrl);
    postUrl = String.format("%s/post", baseUrl);
  }

  // --------------- VideoService interface -----------------------------------
  /**
   * {@inheritDoc}
   *
   * @see org.sakaiproject.nakamura.api.media.VideoService#createVideo(java.io.File, java.lang.String, java.lang.String, java.lang.String[])
   */
  @Override
  public String createVideo(File videoFile, String title, String description,
      String[] tags) throws VideoServiceException {
    String response = sendVideo(title, description, tags, videoFile, null);
    LOG.info(response);
    return response;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.sakaiproject.nakamura.api.media.VideoService#updateVideo(java.lang.String, java.lang.String, java.lang.String, java.lang.String[])
   */
  @Override
  public String updateVideo(String id, String title, String description, String[] tags)
      throws VideoServiceException {
    String response = sendVideo(title, description, tags, null, id);
    LOG.info(response);
    return response;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.sakaiproject.nakamura.api.media.VideoService#getStatus(java.lang.String)
   */
  @Override
  public String getStatus(String id) {
    PostMethod post = null;
    String status = null;

    try {
      JSONObject json = new JSONObject()
          .put("method", "get_upload_status")
          .put("params", new JSONObject()
              .put("token", writeToken)
              .put("video_id", id));
      // Define the url to the api
      post = new PostMethod(postUrl);
      Part[] parts = { new StringPart("JSON-RPC", json.toString()) };
      post.setRequestEntity(new MultipartRequestEntity(parts, post.getParams()));
      int returnCode = client.executeMethod(post);

      String response = post.getResponseBodyAsString();
      status = String.format("Video upload information [%s]: %s", new Object[] {
          returnCode, response });
    } catch (JSONException e) {
      LOG.error(e.getMessage(), e);
    } catch (HttpException e) {
      LOG.error(e.getMessage(), e);
    } catch (IOException e) {
      LOG.error(e.getMessage(), e);
    } finally {
      if (post != null) {
        post.releaseConnection();
      }
    }
    LOG.info(status);
    return status;
  }

  private String sendVideo(String title, String description, String[] tags,
      File videoFile, String id) throws VideoServiceException {
    if (id == null && videoFile == null) {
      throw new IllegalArgumentException("Must supply 'id' or 'videoFile'");
    }

    PostMethod post = null;
    try {
      /*
       * Assemble the JSON params
       */
      JSONObject video = new JSONObject()
          .put("name", title)
          .put("shortDescription", description)
          .put("tags", Arrays.toString(tags));
      String method = "update_video";
      if (videoFile != null) {
        method = "create_video";
      } else {
        video.put("referenceId", id);
      }

      JSONObject json = new JSONObject()
          .put("method", method)
          .put("params", new JSONObject()
              .put("token", writeToken)
              .put("video", video));

      Part[] parts;
      if (videoFile != null) {
        parts = new Part[] { new StringPart("JSON-RPC", json.toString()),
            new FilePart(videoFile.getName(), videoFile) };
      } else {
        parts = new Part[] { new StringPart("JSON-RPC", json.toString()) };
      }

      post = new PostMethod(postUrl);
      post.setRequestEntity(new MultipartRequestEntity(parts, post.getParams()));
      int returnCode = client.executeMethod(post);

      String response = post.getResponseBodyAsString();
      String msg = String.format("Posted video information [%s]: %s", new Object[] {
          returnCode, response });
      LOG.info(msg);
      String output = "{'post':" + json.toString() + ",\n'response':" + response + "}\n";
      return output;
    } catch (JSONException e) {
      throw new VideoServiceException(e.getMessage(), e);
    } catch (FileNotFoundException e) {
      throw new VideoServiceException(e.getMessage(), e);
    } catch (HttpException e) {
      throw new VideoServiceException(e.getMessage(), e);
    } catch (IOException e) {
      throw new VideoServiceException(e.getMessage(), e);
    } finally {
      if (post != null) {
        post.releaseConnection();
      }
    }
  }
}
