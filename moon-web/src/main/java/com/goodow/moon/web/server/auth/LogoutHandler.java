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

import com.google.gxp.base.GxpContext;
import com.google.inject.Inject;
import com.google.walkaround.util.server.servlet.AbstractHandler;
import com.google.walkaround.wave.server.Flag;
import com.google.walkaround.wave.server.FlagName;
import com.google.walkaround.wave.server.gxp.AuthPopup;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LogoutHandler extends com.google.walkaround.wave.server.servlet.LogoutHandler {

  public static class SelfClosingPageHandler extends AbstractHandler {
    @Inject @Flag(FlagName.ANALYTICS_ACCOUNT) String analyticsAccount;

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      resp.setContentType("text/html");
      AuthPopup.write(resp.getWriter(), new GxpContext(getLocale(req)), analyticsAccount, null);
    }
  }

  @SuppressWarnings("unused") private static final Logger log = Logger
      .getLogger(LogoutHandler.class.getName());

  public LogoutHandler() {
  }

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    if (req.getParameter("switchUser") != null) {
      super.doGet(req, resp);
    } else {
      Cookie uid = new Cookie(TokenBasedAccountLookup.USER_ID_KEY, null);
      uid.setMaxAge(0);
      resp.addCookie(uid);
      Cookie secret = new Cookie(TokenBasedAccountLookup.TOKEN_COOKIE_KEY, null);
      secret.setMaxAge(0);
      resp.addCookie(secret);
      resp.sendRedirect("/");
    }
  }
}
