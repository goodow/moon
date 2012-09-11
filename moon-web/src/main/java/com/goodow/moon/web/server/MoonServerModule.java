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

package com.goodow.moon.web.server;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.walkaround.util.server.Util;
import com.google.walkaround.util.server.flags.FlagDeclaration;
import com.google.walkaround.util.server.flags.FlagFormatException;
import com.google.walkaround.util.server.flags.JsonFlags;

import java.util.Arrays;
import java.util.Map;
import java.util.logging.Logger;

public class MoonServerModule extends AbstractModule {

  @SuppressWarnings("unused") private static final Logger log = Logger
      .getLogger(MoonServerModule.class.getName());

  @Override
  protected void configure() {

    JsonFlags.bind(binder(), Arrays.asList(ConfigName.values()), binder().getProvider(
        Key.get(new TypeLiteral<Map<FlagDeclaration, Object>>() {
        }, Names.named("flag configuration map"))));
  }

  @Provides
  @Named("flag configuration map")
  @Singleton
  Map<FlagDeclaration, Object> provideFlagConfiguration(@Named("webinf root") String webinfRoot)
      throws FlagFormatException {
    return JsonFlags.parse(Arrays.asList(ConfigName.values()), Util.slurpRequired(webinfRoot
        + "/config.json"));
  }
}
