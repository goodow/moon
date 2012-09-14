/*
 * Copyright 2012 Goodow.com.
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

import com.google.api.client.http.HttpResponseException;
import com.google.appengine.api.memcache.Expiration;
import com.google.appengine.api.memcache.MemcacheService.SetPolicy;
import com.google.common.net.UriEscapers;
import com.google.gxp.base.GxpContext;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.walkaround.util.server.RetryHelper.PermanentFailure;
import com.google.walkaround.util.server.appengine.MemcacheTable;
import com.google.walkaround.util.server.servlet.BadRequestException;
import com.google.walkaround.wave.server.Flag;
import com.google.walkaround.wave.server.FlagName;
import com.google.walkaround.wave.server.auth.AccountStore;
import com.google.walkaround.wave.server.auth.AccountStore.Record;
import com.google.walkaround.wave.server.auth.OAuthCredentials;
import com.google.walkaround.wave.server.auth.StableUserId;
import com.google.walkaround.wave.server.auth.XsrfHelper;
import com.google.walkaround.wave.server.gxp.AuthPopup;

import org.json.JSONException;
import org.json.JSONObject;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class OAuthCallbackHandler extends
    com.google.walkaround.wave.server.auth.OAuthCallbackHandler {

  private static final Logger log = Logger.getLogger(OAuthCallbackHandler.class.getName());

  @Inject AccountStore accountStore;
  @Inject UserContext userContext;
  @Inject @Flag(FlagName.ANALYTICS_ACCOUNT) String analyticsAccount;
  @Inject OAuthProvider.Helper oAuthProviderHelp;
  @Inject Map<String, OAuthProvider> oAuthProviders;
  @Inject Provider<XsrfHelper> xsrfHelper;
  @Inject @Flag(FlagName.XSRF_TOKEN_EXPIRY_SECONDS) int expirySeconds;
  private final MemcacheTable<String, StableUserId> authorizedCodes;

  @Inject
  OAuthCallbackHandler(MemcacheTable.Factory memcacheFactory) {
    authorizedCodes = memcacheFactory.create("OAuth");
  }

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String errorCode = req.getParameter("error");
    if (errorCode != null) {
      String errorDescription = req.getParameter("error_description");
      log.info("error: " + errorCode + ", description: " + errorDescription);
      String errorMessage;
      if ("access_denied".equals(errorCode)) {
        errorMessage = "请点击上面任一按钮, 在新页面登录, 然后允许访问.";
      } else {
        errorMessage = "An error occured (" + errorCode + "): " + errorDescription;
      }
      log.info("errorMessage=" + errorMessage);
      writeRegularError(req, resp, errorMessage);
      return;
    }

    String code = requireParameter(req, "code");
    String state = requireParameter(req, "state");
    log.info("code=" + code + ", state=" + state);
    String[] split = state.split(" ");
    if (split.length != 2) {
      throw new BadRequestException("state格式错误: " + state);
    }
    OAuthCredentials credentials;
    try {
      credentials = oAuthProviderHelp.exchangeCodeForToken(split[0], code);
    } catch (IOException e) {
      log.log(Level.WARNING, "Failed attempt, trying again", e);
      if (e instanceof HttpResponseException) {
        HttpResponseException f = (HttpResponseException) e;
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        f.getResponse().getRequest().getContent().writeTo(o);
        // TODO(ohler): Use correct character set.
        log.warning("content of rejected request: " + o.toString());
        log.warning("rejection response body: " + f.getResponse().parseAsString());
      }
      resp.sendRedirect(req.getRequestURI() + "?code=" + urlEncode(code) + "&state="
          + urlEncode(state) + "&tryagain=true");
      return;
    }

    // TODO(ohler): Disable user switching in OAuth dialog once Google's OAuth
    // API supports that. (We don't need to be too defensive about account
    // mismatches since there's no real harm in allowing a user to use another
    // Google account for contact information and to import waves from; but it
    // is confusing, so we should disable it.)
    userContext.setOAuthCredentials(credentials);
    userContext.setOAuthProvider(oAuthProviders.get(split[0]));

    Record userInfo = userContext.getOAuthProvider().getUserInfo();
    userContext.setUserId(userInfo.getUserId());
    userContext.setParticipantId(userInfo.getParticipantId());
    log.info("User context: " + userContext);
    writeAccountRecordFromContext();
    authorizedCodes.put(split[1], userInfo.getUserId(), Expiration.byDeltaSeconds(30),
        SetPolicy.ADD_ONLY_IF_NOT_PRESENT);

    resp.setContentType("text/html");
    resp.setCharacterEncoding("UTF-8");
    Cookie uid = new Cookie(TokenBasedAccountLookup.USER_ID_KEY, userContext.getUserId().getId());
    uid.setMaxAge(expirySeconds);
    resp.addCookie(uid);
    Cookie secret =
        new Cookie(TokenBasedAccountLookup.SECRET_TOKEN_COOKIE_KEY, xsrfHelper.get().createToken(
            userContext.getOAuthCredentials().getAccessToken()));
    secret.setMaxAge(expirySeconds);
    resp.addCookie(secret);
    AuthPopup.write(resp.getWriter(), new GxpContext(getLocale(req)), analyticsAccount, null);
  }

  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException,
      ServletException {
    String code = requireParameter(req, "code");
    String clientId = requireParameter(req, "client_id");
    String clientSecret = requireParameter(req, "client_secret");
    AccountStore.Record record = verify(clientId, clientSecret, code);
    JSONObject toRtn = new JSONObject();
    if (record != null) {
      try {
        StableUserId userId = record.getUserId();
        ParticipantId participantId = record.getParticipantId();
        OAuthCredentials oAuthCredentials = record.getOAuthCredentials();
        userContext.setUserId(userId);
        userContext.setParticipantId(participantId);
        userContext.setOAuthCredentials(oAuthCredentials);

        toRtn.put(TokenBasedAccountLookup.USER_ID_KEY, userId.getId());
        toRtn.put("participantId", participantId.getAddress());
        toRtn.put("access_token", xsrfHelper.get().createToken(oAuthCredentials.getAccessToken()));
      } catch (JSONException e) {
        throw new RuntimeException("Bad JSON: " + toRtn, e);
      }
    }
    resp.setStatus(200);
    resp.setContentType("application/json");
    resp.setCharacterEncoding("UTF-8");
    resp.getWriter().print(toRtn.toString());
  }

  private String urlEncode(String s) {
    return UriEscapers.uriQueryStringEscaper(false).escape(s);
  }

  private AccountStore.Record verify(String clientId, String clientSecret, String code)
      throws IOException {
    if (!oAuthProviders.get("google").getClientId().equals(clientId)
        || !oAuthProviders.get("google").getClientSecret().equals(clientSecret)) {
      return null;
    }
    StableUserId userId = authorizedCodes.get(code);
    if (userId == null) {
      return null;
    }
    try {
      return accountStore.get(userId);
    } catch (PermanentFailure e) {
      throw new IOException("Failed to read account record", e);
    } finally {
      authorizedCodes.delete(code);
    }
  }

  private void writeAccountRecordFromContext() throws IOException {
    try {
      accountStore.put(new AccountStore.Record(userContext.getUserId(), userContext
          .getParticipantId(), userContext.getOAuthCredentials()));
    } catch (PermanentFailure e) {
      throw new IOException("Failed to write account record", e);
    }
  }

  private void writeRegularError(HttpServletRequest req, HttpServletResponse resp,
      String errorMessage) throws IOException {
    resp.setContentType("text/html");
    resp.setCharacterEncoding("UTF-8");
    AuthPopup.write(resp.getWriter(), new GxpContext(getLocale(req)), analyticsAccount,
        errorMessage);
  }

}
