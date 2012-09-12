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
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.walkaround.wave.server.Flag;
import com.google.walkaround.wave.server.FlagName;
import com.google.walkaround.wave.server.auth.AccountStore;
import com.google.walkaround.wave.server.auth.OAuthedFetchService;
import com.google.walkaround.wave.server.auth.StableUserId;

import org.json.JSONException;
import org.json.JSONObject;
import org.waveprotocol.wave.model.util.Pair;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.io.IOException;
import java.net.URL;
import java.util.logging.Logger;

public class GoogleOAuthProvider implements OAuthProvider {
  private static final Logger log = Logger.getLogger(GoogleOAuthProvider.class.getName());
  private final Provider<OAuthedFetchService> fetch;
  private final String clientId;
  private final String clientSecret;

  @Inject
  GoogleOAuthProvider(Provider<OAuthedFetchService> fetch,
      @Flag(FlagName.OAUTH_CLIENT_ID) String clientId,
      @Flag(FlagName.OAUTH_CLIENT_SECRET) String clientSecret) {
    this.fetch = fetch;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
  }

  @Override
  public String getAuthorizationEndpointBaseUrl() {
    return "https://accounts.google.com/o/oauth2/auth";
  }

  @Override
  public String getClientId() {
    return clientId;
  }

  @Override
  public String getClientSecret() {
    return clientSecret;
  }

  @Override
  public String getProviderName() {
    return "google";
  }

  @Override
  public String getScope() {
    return "https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/userinfo.email https://www.google.com/m8/feeds";
  }

  @Override
  public String getTokenEndpoint() {
    return "https://accounts.google.com/o/oauth2/token";
  }

  @Override
  public AccountStore.Record getUserInfo() throws IOException {
    URL targetUrl;
    try {
      targetUrl = new URL("https://www.googleapis.com/oauth2/v1/userinfo");
      HTTPResponse resp =
          fetch.get().fetch(
              new HTTPRequest(targetUrl, HTTPMethod.GET, FetchOptions.Builder.withDefaults()
                  .disallowTruncate()));
      String body = OAuthedFetchService.getUtf8ResponseBody(resp, EXPECTED_CONTENT_TYPE);
      JSONObject jsonObject;
      jsonObject = new JSONObject(body);
      return new AccountStore.Record(new StableUserId(getProviderName().charAt(0)
          + jsonObject.getString("id")), ParticipantId.ofUnsafe(jsonObject.getString("email")),
          null);
    } catch (JSONException e) {
      throw new RuntimeException("Bad Json Format: ", e);
    }
  }

  @Override
  public Pair<String, String> parseNonStandardTokenResponse(String response) {
    return null;
  }

  @Override
  public void setAuthorizationEndpointExtraParams(StringBuilder sb) {
    OAuthProvider.Helper.urlParam(sb, "access_type", "offline");
    OAuthProvider.Helper.urlParam(sb, "approval_prompt", "force");
  }

}