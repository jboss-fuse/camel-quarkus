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

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@QuarkusTest
class SapTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "sap-srfc-destination",
            "sap-trfc-destination",
            "sap-qrfc-destination",
            "sap-srfc-server",
            "sap-trfc-server",
            "sap-idoc-destination",
            "sap-idoclist-destination",
            "sap-qidoc-destination",
            "sap-qidoclist-destination",
            "sap-idoclist-server",
    })
    public void loadComponent(String scheme) {
        RestAssured.get("/sap/load/component/" + scheme)
                .then()
                .statusCode(200);
    }

    @Test
    public void loadAllComponents() {
        RestAssured.get("/sap/load/components/all")
                .then()
                .statusCode(200);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SAP_ASHOST", matches = ".+")
    public void jcoInit() {
        RestAssured.get("/sap/jco/init")
                .then()
                .statusCode(200);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SAP_ASHOST", matches = ".+")
    public void rfcSystemInfo() {
        RestAssured.get("/sap/jco/rfc-system-info")
                .then()
                .statusCode(200);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SAP_ASHOST", matches = ".+")
    public void stfcConnection() {
        RestAssured.get("/sap/jco/stfc-connection")
                .then()
                .statusCode(200);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SAP_ASHOST", matches = ".+")
    public void camelSrfcDestination() {
        RestAssured.get("/sap/camel/srfc-destination")
                .then()
                .statusCode(200);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SAP_ASHOST", matches = ".+")
    public void camelSrfcBapiFlcustGetlist() {
        RestAssured.get("/sap/camel/srfc-bapi-flcust-getlist")
                .then()
                .statusCode(200);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SAP_ASHOST", matches = ".+")
    public void camelTrfcDestination() {
        RestAssured.get("/sap/camel/trfc-destination")
                .then()
                .statusCode(200);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SAP_ASHOST", matches = ".+")
    public void camelQrfcDestination() {
        RestAssured.get("/sap/camel/qrfc-destination")
                .then()
                .statusCode(200);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SAP_ASHOST", matches = ".+")
    public void camelClearCache() {
        RestAssured.get("/sap/camel/clear-cache")
                .then()
                .statusCode(200);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SAP_ASHOST", matches = ".+")
    public void camelIDocDestination() {
        RestAssured.get("/sap/camel/idoc-destination")
                .then()
                .statusCode(200);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SAP_ASHOST", matches = ".+")
    public void camelIDocListDestination() {
        RestAssured.get("/sap/camel/idoclist-destination")
                .then()
                .statusCode(200);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SAP_ASHOST", matches = ".+")
    public void camelQIDocDestination() {
        RestAssured.get("/sap/camel/qidoc-destination")
                .then()
                .statusCode(200);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SAP_ASHOST", matches = ".+")
    public void camelQIDocListDestination() {
        RestAssured.get("/sap/camel/qidoclist-destination")
                .then()
                .statusCode(200);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SAP_ASHOST", matches = ".+")
    public void camelSrfcServer() {
        RestAssured.get("/sap/camel/srfc-server")
                .then()
                .statusCode(200);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SAP_ASHOST", matches = ".+")
    public void camelTrfcServer() {
        RestAssured.get("/sap/camel/trfc-server")
                .then()
                .statusCode(200);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SAP_ASHOST", matches = ".+")
    public void camelIDocListServer() {
        RestAssured.get("/sap/camel/idoclist-server")
                .then()
                .statusCode(200);
    }

    @Test
    public void emfModelInstantiation() {
        RestAssured.get("/sap/model/emf")
                .then()
                .statusCode(200);
    }

}
