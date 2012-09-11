/*
 * Copyright 2012 Goodow.com
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.goodow.moon.web.server.auth;

import com.google.appengine.api.urlfetch.FetchOptions;
import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.common.base.Charsets;
import com.google.common.net.UriEscapers;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.walkaround.slob.server.InvalidStoreRequestException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class HttpRequestBuilder {
  private static final Logger log = Logger.getLogger(HttpRequestBuilder.class.getName());
  private final StringBuilder urlBuilder = new StringBuilder();
  private final StringBuilder contentBuilder = new StringBuilder();
  private List<HTTPHeader> headers;
  private final URLFetchService fetchService;
  public static final String GOODOW_TRUSTED_HEADER = "X-Goodow-Trusted";
  private final Provider<UserContext> userContext;

  @Inject
  HttpRequestBuilder(URLFetchService fetchService, Provider<UserContext> userContext) {
    this.fetchService = fetchService;
    this.userContext = userContext;
  }

  public HttpRequestBuilder addHeader(String name, String value) {
    if (headers == null) {
      headers = new ArrayList<HTTPHeader>();
    }
    headers.add(new HTTPHeader(name, value));
    return this;
  }

  public HttpRequestBuilder authorizeThroughUrlParam() {
    urlParam("access_token", userContext.get().getOAuthCredentials().getAccessToken());
    return this;
  }

  public HTTPRequest getRequest(String base, HTTPMethod method) throws MalformedURLException {
    URL url = new URL(base + urlBuilder.toString());
    HTTPRequest req =
        new HTTPRequest(url, method == null ? HTTPMethod.POST : method, getFetchOptions());

    if (HTTPMethod.POST.equals(method)) {
      // TODO(ohler): use multipart/form-data for efficiency
      req.setHeader(new HTTPHeader("Content-Type", "application/x-www-form-urlencoded"));
      if (headers != null) {
        for (HTTPHeader header : headers) {
          req.addHeader(header);
        }
      }
      // req.setHeader(new HTTPHeader(WALKAROUND_TRUSTED_HEADER, secret.getHexData()));
      req.setPayload(contentBuilder.toString().getBytes(Charsets.UTF_8));
    }
    return req;
  }

  public HttpRequestBuilder postParam(String key, String value) {
    contentBuilder.append(key + "=" + urlEncode(value) + "&");
    return this;
  }

  /**
   * @return the response body as a String.
   * @throws IOException for 500 or above or general connection problems.
   * @throws InvalidStoreRequestException for any response code not 200.
   */
  public String send(String base, HTTPMethod method) throws IOException {
    HTTPRequest req = getRequest(base, method);
    log.info("Sending to " + req.getURL());
    String ret = fetch(req);
    log.info("Request completed");
    return ret;
  }

  public HttpRequestBuilder urlParam(String key, String value) {
    urlBuilder.append((urlBuilder.length() == 0 ? "?" : "&") + key + "=" + urlEncode(value));
    return this;
  }

  private String describeResponse(HTTPResponse resp) {
    StringBuilder b =
        new StringBuilder(resp.getResponseCode() + " with " + resp.getContent().length
            + " bytes of content");
    for (HTTPHeader h : resp.getHeaders()) {
      b.append("\n" + h.getName() + ": " + h.getValue());
    }
    b.append("\n" + new String(resp.getContent(), Charsets.UTF_8));
    return "" + b;
  }

  private String fetch(HTTPRequest req) throws IOException {
    HTTPResponse response = fetchService.fetch(req);
    int responseCode = response.getResponseCode();

    if (responseCode >= 300 && responseCode < 400) {
      throw new RuntimeException("Unexpected redirect for url " + req.getURL() + ": "
          + describeResponse(response));
    }

    byte[] rawResponseBody = response.getContent();
    String responseBody;
    if (rawResponseBody == null) {
      responseBody = "";
    } else {
      responseBody = new String(rawResponseBody, Charsets.UTF_8);
    }

    if (responseCode != 200) {
      String msg = req.getURL() + " gave response code " + responseCode + ", body: " + responseBody;
      if (responseCode >= 500) {
        throw new IOException(msg);
      } else {
        throw new InvalidStoreRequestException(msg);
      }
    }

    return responseBody;
  }

  private FetchOptions getFetchOptions() {
    FetchOptions options = FetchOptions.Builder.disallowTruncate().doNotFollowRedirects();
    return options;
  }

  private String urlEncode(String s) {
    return UriEscapers.uriQueryStringEscaper(false).escape(s);
  }
}
