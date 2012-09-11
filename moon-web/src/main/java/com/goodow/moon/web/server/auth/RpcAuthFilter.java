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

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.walkaround.wave.server.auth.ServletAuthHelper;
import com.google.walkaround.wave.shared.SharedConstants;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class RpcAuthFilter extends com.google.walkaround.wave.server.auth.RpcAuthFilter {

  private final Provider<TokenBasedAccountLookup> accountLookup;
  private final Provider<ServletAuthHelper> helper;

  @Inject
  RpcAuthFilter(Provider<TokenBasedAccountLookup> accountLookup, Provider<ServletAuthHelper> helper) {
    super(null, null);
    this.accountLookup = accountLookup;
    this.helper = helper;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain)
      throws IOException, ServletException {
    final HttpServletRequest req = (HttpServletRequest) request;
    final HttpServletResponse resp = (HttpServletResponse) response;

    TokenBasedAccountLookup lookup = accountLookup.get();
    if (!lookup.isUserLoggedIn(req)) {
      needLogin(resp);
      return;
    }

    helper.get().filter(req, resp, filterChain, lookup,
        new ServletAuthHelper.NeedNewOAuthTokenHandler() {
          @Override
          public void sendNeedTokenResponse() throws IOException {
            needLogin(resp);
          }

        });
  }

  private void needLogin(final HttpServletResponse resp) throws IOException {
    resp.setStatus(HttpServletResponse.SC_OK);
    // TODO(ohler): Define a proper protocol between this and AjaxRpc. For now,
    // it treats any parse error as "need new token", so producing something
    // that is not valid json is enough.
    resp.setContentType("application/json");
    resp.setCharacterEncoding("UTF-8");
    resp.getWriter().write(SharedConstants.XSSI_PREFIX + "need new OAuth token");
  }

}
