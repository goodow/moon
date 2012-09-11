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

import com.goodow.moon.web.server.ConfigName;
import com.goodow.moon.web.server.ConfigName.Config;

import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.walkaround.wave.server.auth.AccountStore;
import com.google.walkaround.wave.server.auth.AccountStore.Record;
import com.google.walkaround.wave.server.auth.StableUserId;

import org.json.JSONException;
import org.json.JSONObject;
import org.waveprotocol.wave.model.util.Pair;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class QqOAuthProvider implements OAuthProvider {

  private static final Logger log = Logger.getLogger(QqOAuthProvider.class.getName());
  private final Provider<HttpRequestBuilder> request;
  private final Provider<UserContext> userCtx;
  private final String clientId;
  private final String clientSecret;

  @Inject
  QqOAuthProvider(Provider<HttpRequestBuilder> request, Provider<UserContext> userCtx,
      @Config(ConfigName.OAUTH_CLIENT_ID_QQ) String clientId,
      @Config(ConfigName.OAUTH_CLIENT_SECRET_QQ) String clientSecret) {
    this.request = request;
    this.userCtx = userCtx;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
  }

  @Override
  public String getAuthorizationEndpointBaseUrl() {
    return "https://graph.qq.com/oauth2.0/authorize";
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
    return "qq";
  }

  @Override
  public String getScope() {
    return "get_user_info,get_info";
  }

  @Override
  public String getTokenEndpoint() {
    return "https://graph.qq.com/oauth2.0/token";
  }

  @Override
  public Record getUserInfo() throws IOException {
    // fetch id
    HttpRequestBuilder req = request.get();
    String resp =
        req.authorizeThroughUrlParam().send("https://graph.z.qq.com/moc2/me", HTTPMethod.GET);
    String id = parameterMap(resp).get("openid");
    StableUserId userId = new StableUserId(getProviderName().charAt(0) + id);
    userCtx.get().setUserId(userId);

    // fetch email
    String email = null;
    req = request.get();
    req.authorizeThroughUrlParam();
    extraAuthorizeParams(req);
    resp = req.send("https://graph.qq.com/user/get_info", HTTPMethod.GET);
    log.info("getUserInfo() returned: " + resp);
    try {
      JSONObject jsonObject = new JSONObject(resp);
      JSONObject data = jsonObject.getJSONObject("data");
      if (data.has("email") && !data.getString("email").isEmpty()) {
        email = data.getString("email");
      }
      if (email == null && data.has("name") && !data.getString("name").isEmpty()) {
        email = data.getString("name") + "@t.qq.com";
      }
      if (email == null) {
        email = data.getString("openid") + "@t.qq.com";
      }
    } catch (JSONException e) {
      throw new RuntimeException("Bad JSON: " + resp, e);
    }
    return new AccountStore.Record(userId, ParticipantId.ofUnsafe(email), null);
  }

  /**
   * @param response access_token=ABCD&expires_in=7776000
   */
  @Override
  public Pair<String, String> parseNonStandardTokenResponse(String response) {
    log.info("parse token response:" + response);
    Map<String, String> paramMap = parameterMap(response);
    return Pair.of(paramMap.get("access_token"), paramMap.get("refresh_token"));
  }

  @Override
  public void setAuthorizationEndpointExtraParams(StringBuilder sb) {
    // I cannot believe, they don't allow to end with an extra '&'.
    sb.deleteCharAt(sb.length() - 1);
  }

  private void extraAuthorizeParams(HttpRequestBuilder req) {
    req.urlParam("oauth_consumer_key", getClientId());
    req.urlParam("openid", userCtx.get().getUserId().getId().substring(1));
  }

  private Map<String, String> parameterMap(String response) {
    Map<String, String> paramMap = new HashMap<String, String>();
    for (String kvPair : response.split("&")) {
      String[] kv = kvPair.split("=", 2);
      if (kv.length > 1) {
        paramMap.put(kv[0], kv[1]);
      } else {
        paramMap.put(kv[0], "");
      }
    }
    return paramMap;
  }

}