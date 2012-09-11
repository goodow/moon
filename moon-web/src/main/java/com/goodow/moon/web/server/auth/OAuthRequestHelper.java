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

import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.inject.Inject;
import com.google.walkaround.wave.server.auth.OAuthCredentials;

import java.io.IOException;
import java.util.logging.Logger;

public class OAuthRequestHelper extends com.google.walkaround.wave.server.auth.OAuthRequestHelper {

  private static final Logger log = Logger.getLogger(OAuthRequestHelper.class.getName());

  private final UserContext userContext;

  private final OAuthProvider.Helper oAuthProvider;

  @Inject
  public OAuthRequestHelper(UserContext userContext, OAuthProvider.Helper oAuthProvider) {
    super("fakeId", "fakeSecret", userContext);
    this.userContext = userContext;
    this.oAuthProvider = oAuthProvider;
  }

  @Override
  public void authorize(HTTPRequest req) {
    req.setHeader(new HTTPHeader("Authorization", "Bearer " + getCredentials().getAccessToken()));
  }

  @Override
  public void refreshToken() throws IOException {

    OAuthCredentials oldCredentials = getCredentials();

    String refreshToken = oldCredentials.getRefreshToken();
    if (refreshToken == null || refreshToken.isEmpty()) {
      return;
    }
    String newAccessToken =
        oAuthProvider.refreshToken(userContext.getOAuthProvider().getProviderName(), refreshToken);

    log.info("New access token: " + newAccessToken);
    userContext.setOAuthCredentials(new OAuthCredentials(refreshToken, newAccessToken));
    log.info("Successfully refreshed token: " + userContext);
  }

  private OAuthCredentials getCredentials() {
    return userContext.getOAuthCredentials();
  }

}
