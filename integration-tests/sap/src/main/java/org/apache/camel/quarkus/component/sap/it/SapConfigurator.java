/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.quarkus.component.sap.it;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.fusesource.camel.component.sap.SapConnectionConfiguration;
import org.fusesource.camel.component.sap.model.rfc.RfcFactory;

@ApplicationScoped
public class SapConfigurator {

    static final String DEST_NAME = "testDest";
    static final String SERVER_NAME = "testServer";

    @Inject
    CamelContext context;

    @ConfigProperty(name = "sap.ashost")
    Optional<String> ashost;

    @ConfigProperty(name = "sap.sysnr", defaultValue = "01")
    String sysnr;

    @ConfigProperty(name = "sap.client", defaultValue = "000")
    String client;

    @ConfigProperty(name = "sap.user")
    Optional<String> user;

    @ConfigProperty(name = "sap.passwd")
    Optional<String> passwd;

    @ConfigProperty(name = "sap.lang", defaultValue = "en")
    String lang;

    @ConfigProperty(name = "sap.gwserv", defaultValue = "3301")
    String gwserv;

    public boolean isSapAvailable() {
        return ashost.isPresent() && !ashost.get().isEmpty();
    }

    public synchronized void ensureSapConfigured() {
        if (context.getRegistry().lookupByName("sap-configuration") == null) {
            String host = ashost.orElse("");
            SapConnectionConfiguration sapConfig = new SapConnectionConfiguration();
            var destData = RfcFactory.eINSTANCE.createDestinationData();
            destData.setAshost(host);
            destData.setSysnr(sysnr);
            destData.setClient(client);
            destData.setUser(user.orElse(""));
            destData.setPasswd(passwd.orElse(""));
            destData.setLang(lang);
            sapConfig.getDestinationDataStore().put(DEST_NAME, destData);

            var serverData = RfcFactory.eINSTANCE.createServerData();
            serverData.setGwhost(host);
            serverData.setGwserv(gwserv);
            serverData.setProgid("QUICKSTART");
            serverData.setRepositoryDestination(DEST_NAME);
            serverData.setConnectionCount("2");
            sapConfig.getServerDataStore().put(SERVER_NAME, serverData);

            context.getRegistry().bind("sap-configuration", sapConfig);
        }
    }
}
