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

import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.common.net.UriEscapers;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.walkaround.slob.server.InvalidStoreRequestException;
import com.google.walkaround.wave.server.auth.AccountStore;
import com.google.walkaround.wave.server.auth.NeedNewOAuthTokenException;
import com.google.walkaround.wave.server.auth.OAuthCredentials;
import com.google.walkaround.wave.server.auth.OAuthInterstitialHandler.CallbackPath;

import org.json.JSONException;
import org.json.JSONObject;
import org.waveprotocol.box.server.util.RandomBase64Generator;
import org.waveprotocol.wave.model.util.Pair;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public interface OAuthProvider {
  public static class Helper {
    private static final Logger log = Logger.getLogger(Helper.class.getName());

    public static void urlParam(StringBuilder sb, String key, String value) {
      if (value == null || value.isEmpty()) {
        return;
      }
      sb.append(key + "=" + queryEncode(value) + "&");
    }

    private static String queryEncode(String s) {
      return UriEscapers.uriQueryStringEscaper(false).escape(s);
    }

    @Inject @CallbackPath String callbackUrl;
    @Inject Map<String, OAuthProvider> oAuthProviders;
    @Inject RandomBase64Generator random64;
    @Inject Provider<HttpRequestBuilder> postRequest;

    public OAuthCredentials exchangeCodeForToken(String providerName, String authorizationCode)
        throws IOException {
      Pair<String, String> pair = exchangeCodeForToken(providerName, authorizationCode, false);
      OAuthCredentials credentials =
          new OAuthCredentials(pair.second == null ? "" : pair.second, pair.first);
      return credentials;
    }

    public String getAuthorizationEndpointUrl(String providerName, String state) {
      OAuthProvider oAuthProvider = oAuthProviders.get(providerName);
      StringBuilder sb = new StringBuilder(oAuthProvider.getAuthorizationEndpointBaseUrl());
      sb.append("?");
      urlParam(sb, "response_type", "code");
      urlParam(sb, "client_id", oAuthProvider.getClientId());
      urlParam(sb, "redirect_uri", callbackUrl);
      urlParam(sb, "scope", oAuthProvider.getScope());
      urlParam(sb, "state", providerName + " " + (state == null ? random64.next(8) : state));
      oAuthProvider.setAuthorizationEndpointExtraParams(sb);

      return sb.toString();
    }

    public String refreshToken(String providerName, String refreshToken) throws IOException {
      Pair<String, String> pair = exchangeCodeForToken(providerName, refreshToken, true);
      return pair.first;
    }

    private Pair<String, String> exchangeCodeForToken(String providerName,
        String codeOrRefreshToken, boolean isRefresh) throws IOException {
      OAuthProvider oAuthProvider = oAuthProviders.get(providerName);
      HttpRequestBuilder req = postRequest.get();
      if (isRefresh) {
        req.postParam("refresh_token", codeOrRefreshToken);
        req.postParam("grant_type", "refresh_token");
      } else {
        req.postParam("code", codeOrRefreshToken);
        req.postParam("redirect_uri", callbackUrl);
        req.postParam("grant_type", "authorization_code");
      }
      req.postParam("client_id", oAuthProvider.getClientId());
      req.postParam("client_secret", oAuthProvider.getClientSecret());

      String content = null;
      try {
        log.info("Trying to exchange OAuth token; isRefresh: " + isRefresh);
        content = req.send(oAuthProvider.getTokenEndpoint(), HTTPMethod.POST);
      } catch (InvalidStoreRequestException e) {
        // if (isRefresh) {
        log.log(Level.WARNING, "exchangeOAuthCredentials() failed; perhaps revoked", e);
        throw new RuntimeException("exchangeOAuthCredentials() failed; perhaps revoked", e);
        // }
      }

      Pair<String, String> pair = oAuthProvider.parseNonStandardTokenResponse(content);
      if (pair != null) {
        return pair;
      }
      try {
        JSONObject jsonObject = new JSONObject(content);
        log.info(oAuthProvider.getTokenEndpoint() + " gave response body: " + jsonObject.toString());
        if (!jsonObject.has("access_token")) {
          throw new RuntimeException("No access token provided after exchangeOAuthCredentials");
        }
        return Pair.of(jsonObject.getString("access_token"), jsonObject.has("refresh_token")
            ? jsonObject.getString("refresh_token") : null);
      } catch (JSONException e) {
        // if (isRefresh) {
        log.log(Level.WARNING, "exchangeOAuthCredentials() failed; perhaps revoked", e);
        throw new NeedNewOAuthTokenException("exchangeOAuthCredentials() failed; perhaps revoked",
            e);
        // }
      }
    }
  }

  String EXPECTED_CONTENT_TYPE = "application/json; charset=UTF-8";

  String getAuthorizationEndpointBaseUrl();

  String getClientId();;

  String getClientSecret();

  String getProviderName();

  String getScope();

  String getTokenEndpoint();

  AccountStore.Record getUserInfo() throws IOException;

  Pair<String, String> parseNonStandardTokenResponse(String response);

  void setAuthorizationEndpointExtraParams(StringBuilder sb);

}
