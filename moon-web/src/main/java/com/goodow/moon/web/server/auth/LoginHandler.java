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

import com.goodow.moon.web.server.gxp.Login;

import com.google.inject.Inject;
import com.google.walkaround.util.server.servlet.AbstractHandler;
import com.google.walkaround.wave.server.servlet.PageSkinWriter;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LoginHandler extends AbstractHandler {

  private static final Logger log = Logger.getLogger(LoginHandler.class.getName());

  @Inject OAuthProvider.Helper oAuthProvider;
  @Inject PageSkinWriter pageSkinWriter;

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String originalRequest = requireParameter(req, "originalRequest");
    String state = optionalParameter(req, "state", null);
    String authorizeUrl = oAuthProvider.getAuthorizationEndpointUrl("google", state);
    String authorizeUrlQq = oAuthProvider.getAuthorizationEndpointUrl("qq", state);

    log.info("originalRequest=" + originalRequest + ", authorizeUrl=" + authorizeUrl);

    resp.setContentType("text/html");
    resp.setCharacterEncoding("UTF-8");
    pageSkinWriter.write("登入", "", Login.getGxpClosure(authorizeUrl, authorizeUrlQq,
        originalRequest));
  }

}
