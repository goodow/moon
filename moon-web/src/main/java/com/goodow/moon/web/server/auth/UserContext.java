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
import com.google.inject.servlet.RequestScoped;

import java.util.Map;

@RequestScoped
public class UserContext extends com.google.walkaround.wave.server.auth.UserContext {
  private final Map<String, OAuthProvider> oAuthProviders;
  private OAuthProvider oAuthProvider;

  @Inject
  UserContext(Map<String, OAuthProvider> oAuthProviders) {
    this.oAuthProviders = oAuthProviders;
  }

  @Override
  public boolean isUserAdmin() {
    if ("goodow.com".equals(super.getParticipantId().getDomain())) {
      return true;
    }
    return super.isUserAdmin();
  }

  public void setOAuthProvider(OAuthProvider oAuthProvider) {
    this.oAuthProvider = oAuthProvider;
  }

  OAuthProvider getOAuthProvider() {
    if (oAuthProvider != null) {
      return oAuthProvider;
    }
    String providerName = null;
    for (String provider : oAuthProviders.keySet()) {
      if (provider.startsWith("" + getUserId().getId().charAt(0))) {
        providerName = provider;
        break;
      }
    }
    return providerName == null ? null : oAuthProviders.get(providerName);
  }
}
