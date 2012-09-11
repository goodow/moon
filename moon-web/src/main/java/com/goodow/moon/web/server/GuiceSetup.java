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
package com.goodow.moon.web.server;

import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.servlet.ServletModule;
import com.google.inject.util.Modules;
import com.google.walkaround.wave.server.DatastoreTimeoutMillis;

import java.util.LinkedHashSet;
import java.util.logging.Logger;

import javax.servlet.Filter;

public class GuiceSetup {
  private static final long INTERACTIVE_DATASTORE_TIMEOUT_MILLIS = 10000L;
  @SuppressWarnings("unused") private static final Logger log = Logger.getLogger(GuiceSetup.class
      .getName());

  private static final LinkedHashSet<Module> extraModules = Sets.newLinkedHashSet();
  private static final LinkedHashSet<ServletModule> extraServletModules = Sets.newLinkedHashSet();
  private static final LinkedHashSet<Filter> extraFilters = Sets.newLinkedHashSet();

  /** Hacky hook to add extra filters. */
  public static void addExtraFilter(Filter f) {
    extraFilters.add(f);
  }

  /** Hacky hook to add extra modules. */
  public static void addExtraModule(Module m) {
    extraModules.add(m);
  }

  /** Hacky hook to add extra servlet modules. */
  public static void addExtraServletModule(ServletModule m) {
    extraServletModules.add(m);
  }

  public static Module getRootModule() {
    return Modules.combine(Modules.combine(extraModules),
        com.google.walkaround.wave.server.GuiceSetup.getRootModule(), new MoonServerModule());
  }

  public static Module getServletModule() {
    return Modules.combine(Modules.combine(extraServletModules),
        new MoonServletModule(extraFilters), new AbstractModule() {
          @Override
          public void configure() {
            bind(Long.class).annotatedWith(DatastoreTimeoutMillis.class).toInstance(
                INTERACTIVE_DATASTORE_TIMEOUT_MILLIS);
          }
        });
  }
}
