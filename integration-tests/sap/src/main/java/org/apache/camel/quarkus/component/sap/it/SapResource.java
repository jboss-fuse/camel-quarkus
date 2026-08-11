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

import java.util.ArrayList;
import java.util.List;

import com.sap.conn.jco.JCo;
import com.sap.conn.jco.JCoDestination;
import com.sap.conn.jco.JCoDestinationManager;
import com.sap.conn.jco.JCoFunction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.fusesource.camel.component.sap.SapQueuedIDocDestinationEndpoint;
import org.fusesource.camel.component.sap.SapQueuedIDocListDestinationEndpoint;
import org.fusesource.camel.component.sap.SapQueuedRfcDestinationEndpoint;
import org.fusesource.camel.component.sap.SapSynchronousRfcDestinationEndpoint;
import org.fusesource.camel.component.sap.SapSynchronousRfcServerEndpoint;
import org.fusesource.camel.component.sap.SapTransactionalIDocDestinationEndpoint;
import org.fusesource.camel.component.sap.SapTransactionalIDocListDestinationEndpoint;
import org.fusesource.camel.component.sap.SapTransactionalIDocListServerEndpoint;
import org.fusesource.camel.component.sap.SapTransactionalRfcDestinationEndpoint;
import org.fusesource.camel.component.sap.SapTransactionalRfcServerEndpoint;
import org.fusesource.camel.component.sap.model.idoc.Document;
import org.fusesource.camel.component.sap.model.idoc.DocumentList;
import org.fusesource.camel.component.sap.model.idoc.IdocFactory;
import org.fusesource.camel.component.sap.model.idoc.Segment;
import org.fusesource.camel.component.sap.model.rfc.RfcFactory;
import org.fusesource.camel.component.sap.model.rfc.Structure;
import org.fusesource.camel.component.sap.model.rfc.Table;
import org.jboss.logging.Logger;

@Path("/sap")
@ApplicationScoped
public class SapResource {

    private static final Logger LOG = Logger.getLogger(SapResource.class);

    private static final String[] ALL_COMPONENT_SCHEMES = {
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
    };

    private static final String IDOC_TYPE = "FLCUSTOMER_CREATEFROMDATA01";
    private static final String QUEUE_NAME = "TESTQUEUE";

    @Inject
    CamelContext context;

    @Inject
    ProducerTemplate template;

    @Inject
    SapConfigurator sapConfigurator;

    // --- Component loading tests (no SAP instance needed) ---

    @Path("/load/component/{scheme}")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response loadComponent(@PathParam("scheme") String scheme) {
        if (context.getComponent(scheme) != null) {
            return Response.ok().build();
        }
        LOG.warnf("Could not load [%s] from the Camel context", scheme);
        return Response.status(500, scheme + " could not be loaded from the Camel context").build();
    }

    @Path("/load/components/all")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response loadAllComponents() {
        List<String> failed = new ArrayList<>();
        for (String scheme : ALL_COMPONENT_SCHEMES) {
            if (context.getComponent(scheme) == null) {
                failed.add(scheme);
            }
        }
        if (failed.isEmpty()) {
            return Response.ok(String.valueOf(ALL_COMPONENT_SCHEMES.length)).build();
        }
        LOG.warnf("Could not load components: %s", failed);
        return Response.status(500, "Failed to load: " + failed).build();
    }

    // --- JCo init (needs native library, no SAP instance) ---

    @Path("/jco/init")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response jcoInit() {
        try {
            String version = JCo.getVersion();
            return Response.ok("JCo " + version).build();
        } catch (Throwable e) {
            LOG.errorf(e, "JCo initialization failed");
            return Response.status(500, "JCo init failed: " + e.getMessage()).build();
        }
    }

    // --- JCo direct RFC calls (needs SAP instance) ---

    @Path("/jco/rfc-system-info")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response rfcSystemInfo() {
        if (!sapConfigurator.isSapAvailable()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("sap.ashost not set").build();
        }
        try {
            sapConfigurator.ensureSapConfigured();
            JCoDestination dest = JCoDestinationManager.getDestination(SapConfigurator.DEST_NAME);
            JCoFunction func = dest.getRepository().getFunction("RFC_SYSTEM_INFO");
            func.execute(dest);

            String rfcHost = func.getExportParameterList()
                    .getStructure("RFCSI_EXPORT")
                    .getString("RFCHOST");

            return Response.ok("RFC_SYSTEM_INFO OK, host=" + rfcHost).build();
        } catch (Exception e) {
            LOG.errorf(e, "RFC_SYSTEM_INFO failed");
            return Response.status(500).entity("RFC_SYSTEM_INFO failed: " + e.getMessage()).build();
        }
    }

    @Path("/jco/stfc-connection")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response stfcConnection() {
        if (!sapConfigurator.isSapAvailable()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("sap.ashost not set").build();
        }
        try {
            sapConfigurator.ensureSapConfigured();
            JCoDestination dest = JCoDestinationManager.getDestination(SapConfigurator.DEST_NAME);
            JCoFunction func = dest.getRepository().getFunction("STFC_CONNECTION");
            func.getImportParameterList().setValue("REQUTEXT", "Hello from Camel Quarkus Native");
            func.execute(dest);

            String echoText = func.getExportParameterList().getString("ECHOTEXT");
            String respText = func.getExportParameterList().getString("RESPTEXT");

            return Response.ok("STFC_CONNECTION OK, echo=" + echoText + ", resp=" + respText).build();
        } catch (Exception e) {
            LOG.errorf(e, "STFC_CONNECTION failed");
            return Response.status(500).entity("STFC_CONNECTION failed: " + e.getMessage()).build();
        }
    }

    // --- Camel sRFC destination (needs SAP instance) ---

    @Path("/camel/srfc-destination")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response camelSrfcDestination() {
        if (!sapConfigurator.isSapAvailable()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("sap.ashost not set").build();
        }
        try {
            sapConfigurator.ensureSapConfigured();
            String uri = "sap-srfc-destination:" + SapConfigurator.DEST_NAME + ":STFC_CONNECTION";
            SapSynchronousRfcDestinationEndpoint endpoint = context.getEndpoint(uri,
                    SapSynchronousRfcDestinationEndpoint.class);
            Structure request = endpoint.createRequest();
            request.put("REQUTEXT", "Hello from Camel Quarkus sRFC");

            Structure response = (Structure) template.requestBody(uri, request);
            String echoText = response.get("ECHOTEXT", String.class);
            return Response.ok("Camel sRFC OK, echo=" + echoText).build();
        } catch (Exception e) {
            LOG.errorf(e, "Camel sRFC destination failed");
            return Response.status(500).entity("Camel sRFC failed: " + e.getMessage()).build();
        }
    }

    // --- Camel sRFC with BAPI_FLCUST_GETLIST (needs SAP instance + Flight Data) ---

    @Path("/camel/srfc-bapi-flcust-getlist")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response camelSrfcBapiFlcustGetlist() {
        if (!sapConfigurator.isSapAvailable()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("sap.ashost not set").build();
        }
        try {
            sapConfigurator.ensureSapConfigured();
            String uri = "sap-srfc-destination:" + SapConfigurator.DEST_NAME + ":BAPI_FLCUST_GETLIST";
            SapSynchronousRfcDestinationEndpoint endpoint = context.getEndpoint(uri,
                    SapSynchronousRfcDestinationEndpoint.class);
            Structure request = endpoint.createRequest();
            request.put("CUSTOMER_NAME", "*");
            request.put("MAX_ROWS", 10);

            Structure response = (Structure) template.requestBody(uri, request);

            @SuppressWarnings("unchecked")
            Table<? extends Structure> customerList = response.get("CUSTOMER_LIST", Table.class);
            int count = customerList != null ? customerList.size() : 0;
            return Response.ok("BAPI_FLCUST_GETLIST OK, customers=" + count).build();
        } catch (Exception e) {
            LOG.errorf(e, "BAPI_FLCUST_GETLIST failed");
            return Response.status(500).entity("BAPI_FLCUST_GETLIST failed: " + e.getMessage()).build();
        }
    }

    // --- Camel tRFC destination (needs SAP instance) ---

    @Path("/camel/trfc-destination")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response camelTrfcDestination() {
        if (!sapConfigurator.isSapAvailable()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("sap.ashost not set").build();
        }
        try {
            sapConfigurator.ensureSapConfigured();
            String uri = "sap-trfc-destination:" + SapConfigurator.DEST_NAME + ":STFC_CONNECTION";
            SapTransactionalRfcDestinationEndpoint endpoint = context.getEndpoint(uri,
                    SapTransactionalRfcDestinationEndpoint.class);
            Structure request = endpoint.createRequest();
            request.put("REQUTEXT", "Hello from Camel Quarkus tRFC");

            template.sendBody(uri, request);
            return Response.ok("Camel tRFC OK").build();
        } catch (Exception e) {
            LOG.errorf(e, "Camel tRFC destination failed");
            return Response.status(500).entity("Camel tRFC failed: " + e.getMessage()).build();
        }
    }

    // --- Camel qRFC destination (needs SAP instance) ---

    @Path("/camel/qrfc-destination")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response camelQrfcDestination() {
        if (!sapConfigurator.isSapAvailable()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("sap.ashost not set").build();
        }
        try {
            sapConfigurator.ensureSapConfigured();
            String uri = "sap-qrfc-destination:" + SapConfigurator.DEST_NAME + ":TESTQUEUE:STFC_CONNECTION";
            SapQueuedRfcDestinationEndpoint endpoint = context.getEndpoint(uri,
                    SapQueuedRfcDestinationEndpoint.class);
            Structure request = endpoint.createRequest();
            request.put("REQUTEXT", "Hello from Camel Quarkus qRFC");

            template.sendBody(uri, request);
            return Response.ok("Camel qRFC OK").build();
        } catch (Exception e) {
            LOG.errorf(e, "Camel qRFC destination failed");
            return Response.status(500).entity("Camel qRFC failed: " + e.getMessage()).build();
        }
    }

    // --- Camel clear-cache (needs JCo init) ---

    @Path("/camel/clear-cache")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response camelClearCache() {
        if (!sapConfigurator.isSapAvailable()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("sap.ashost not set").build();
        }
        try {
            sapConfigurator.ensureSapConfigured();
            template.sendBody("sap-clear-cache://cache", "");
            return Response.ok("Camel clear-cache OK").build();
        } catch (Exception e) {
            LOG.errorf(e, "Camel clear-cache failed");
            return Response.status(500).entity("Camel clear-cache failed: " + e.getMessage()).build();
        }
    }

    // --- IDoc destination tests (needs SAP instance) ---

    @Path("/camel/idoc-destination")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response camelIDocDestination() {
        if (!sapConfigurator.isSapAvailable()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("sap.ashost not set").build();
        }
        try {
            sapConfigurator.ensureSapConfigured();
            String uri = "sap-idoc-destination:" + SapConfigurator.DEST_NAME + ":" + IDOC_TYPE;
            SapTransactionalIDocDestinationEndpoint endpoint = context.getEndpoint(uri,
                    SapTransactionalIDocDestinationEndpoint.class);
            Document document = endpoint.createDocument();
            document.setMessageType("FLCUSTOMER_CREATEFROMDATA");
            document.setRecipientPartnerNumber("QUICKCLNT");
            document.setRecipientPartnerType("LS");
            document.setSenderPartnerNumber("QUICKSTART");
            document.setSenderPartnerType("LS");

            Segment rootSegment = document.getRootSegment();
            Segment headerSegment = rootSegment.getChildren("E1SCU_CRE").add();
            Segment newCustomerSegment = headerSegment.getChildren("E1BPSCUNEW").add();
            newCustomerSegment.put("CUSTNAME", "Test Customer CQ");
            newCustomerSegment.put("FORM", "Mr.");
            newCustomerSegment.put("STREET", "1 Quarkus Ave");
            newCustomerSegment.put("POSTCODE", "00001");
            newCustomerSegment.put("CITY", "NativeCity");
            newCustomerSegment.put("COUNTR", "US");
            newCustomerSegment.put("PHONE", "800-555-0001");
            newCustomerSegment.put("EMAIL", "test@quarkus.io");
            newCustomerSegment.put("CUSTTYPE", "P");
            newCustomerSegment.put("DISCOUNT", "000");
            newCustomerSegment.put("LANGU", "E");

            template.sendBody(uri, document);
            return Response.ok("Camel IDoc destination OK").build();
        } catch (Exception e) {
            LOG.errorf(e, "Camel IDoc destination failed");
            return Response.status(500).entity("Camel IDoc destination failed: " + e.getMessage()).build();
        }
    }

    @Path("/camel/idoclist-destination")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response camelIDocListDestination() {
        if (!sapConfigurator.isSapAvailable()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("sap.ashost not set").build();
        }
        try {
            sapConfigurator.ensureSapConfigured();
            String uri = "sap-idoclist-destination:" + SapConfigurator.DEST_NAME + ":" + IDOC_TYPE;
            SapTransactionalIDocListDestinationEndpoint endpoint = context.getEndpoint(uri,
                    SapTransactionalIDocListDestinationEndpoint.class);
            DocumentList documentList = endpoint.createDocumentList();

            Document doc = documentList.add();
            doc.setMessageType("FLCUSTOMER_CREATEFROMDATA");
            doc.setRecipientPartnerNumber("QUICKCLNT");
            doc.setRecipientPartnerType("LS");
            doc.setSenderPartnerNumber("QUICKSTART");
            doc.setSenderPartnerType("LS");

            Segment rootSegment = doc.getRootSegment();
            Segment headerSegment = rootSegment.getChildren("E1SCU_CRE").add();
            Segment newCustomerSegment = headerSegment.getChildren("E1BPSCUNEW").add();
            newCustomerSegment.put("CUSTNAME", "Test ListCustomer CQ");
            newCustomerSegment.put("FORM", "Mrs.");
            newCustomerSegment.put("STREET", "2 Quarkus Ave");
            newCustomerSegment.put("POSTCODE", "00002");
            newCustomerSegment.put("CITY", "NativeCity");
            newCustomerSegment.put("COUNTR", "US");
            newCustomerSegment.put("PHONE", "800-555-0002");
            newCustomerSegment.put("EMAIL", "testlist@quarkus.io");
            newCustomerSegment.put("CUSTTYPE", "P");
            newCustomerSegment.put("DISCOUNT", "000");
            newCustomerSegment.put("LANGU", "E");

            template.sendBody(uri, documentList);
            return Response.ok("Camel IDocList destination OK, docs=" + documentList.size()).build();
        } catch (Exception e) {
            LOG.errorf(e, "Camel IDocList destination failed");
            return Response.status(500).entity("Camel IDocList destination failed: " + e.getMessage()).build();
        }
    }

    @Path("/camel/qidoc-destination")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response camelQIDocDestination() {
        if (!sapConfigurator.isSapAvailable()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("sap.ashost not set").build();
        }
        try {
            sapConfigurator.ensureSapConfigured();
            String uri = "sap-qidoc-destination:" + SapConfigurator.DEST_NAME + ":" + QUEUE_NAME + ":" + IDOC_TYPE;
            SapQueuedIDocDestinationEndpoint endpoint = context.getEndpoint(uri,
                    SapQueuedIDocDestinationEndpoint.class);
            Document document = endpoint.createDocument();
            document.setMessageType("FLCUSTOMER_CREATEFROMDATA");
            document.setRecipientPartnerNumber("QUICKCLNT");
            document.setRecipientPartnerType("LS");
            document.setSenderPartnerNumber("QUICKSTART");
            document.setSenderPartnerType("LS");

            Segment rootSegment = document.getRootSegment();
            Segment headerSegment = rootSegment.getChildren("E1SCU_CRE").add();
            Segment newCustomerSegment = headerSegment.getChildren("E1BPSCUNEW").add();
            newCustomerSegment.put("CUSTNAME", "Test QueuedIDoc CQ");
            newCustomerSegment.put("FORM", "Mr.");
            newCustomerSegment.put("STREET", "3 Quarkus Ave");
            newCustomerSegment.put("POSTCODE", "00003");
            newCustomerSegment.put("CITY", "NativeCity");
            newCustomerSegment.put("COUNTR", "US");
            newCustomerSegment.put("PHONE", "800-555-0003");
            newCustomerSegment.put("EMAIL", "testq@quarkus.io");
            newCustomerSegment.put("CUSTTYPE", "P");
            newCustomerSegment.put("DISCOUNT", "000");
            newCustomerSegment.put("LANGU", "E");

            template.sendBody(uri, document);
            return Response.ok("Camel qIDoc destination OK").build();
        } catch (Exception e) {
            LOG.errorf(e, "Camel qIDoc destination failed");
            return Response.status(500).entity("Camel qIDoc destination failed: " + e.getMessage()).build();
        }
    }

    @Path("/camel/qidoclist-destination")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response camelQIDocListDestination() {
        if (!sapConfigurator.isSapAvailable()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("sap.ashost not set").build();
        }
        try {
            sapConfigurator.ensureSapConfigured();
            String uri = "sap-qidoclist-destination:" + SapConfigurator.DEST_NAME + ":" + QUEUE_NAME + ":" + IDOC_TYPE;
            SapQueuedIDocListDestinationEndpoint endpoint = context.getEndpoint(uri,
                    SapQueuedIDocListDestinationEndpoint.class);
            DocumentList documentList = endpoint.createDocumentList();

            Document doc = documentList.add();
            doc.setMessageType("FLCUSTOMER_CREATEFROMDATA");
            doc.setRecipientPartnerNumber("QUICKCLNT");
            doc.setRecipientPartnerType("LS");
            doc.setSenderPartnerNumber("QUICKSTART");
            doc.setSenderPartnerType("LS");

            Segment rootSegment = doc.getRootSegment();
            Segment headerSegment = rootSegment.getChildren("E1SCU_CRE").add();
            Segment newCustomerSegment = headerSegment.getChildren("E1BPSCUNEW").add();
            newCustomerSegment.put("CUSTNAME", "Test QueuedIDocList CQ");
            newCustomerSegment.put("FORM", "Mrs.");
            newCustomerSegment.put("STREET", "4 Quarkus Ave");
            newCustomerSegment.put("POSTCODE", "00004");
            newCustomerSegment.put("CITY", "NativeCity");
            newCustomerSegment.put("COUNTR", "US");
            newCustomerSegment.put("PHONE", "800-555-0004");
            newCustomerSegment.put("EMAIL", "testqlist@quarkus.io");
            newCustomerSegment.put("CUSTTYPE", "P");
            newCustomerSegment.put("DISCOUNT", "000");
            newCustomerSegment.put("LANGU", "E");

            template.sendBody(uri, documentList);
            return Response.ok("Camel qIDocList destination OK, docs=" + documentList.size()).build();
        } catch (Exception e) {
            LOG.errorf(e, "Camel qIDocList destination failed");
            return Response.status(500).entity("Camel qIDocList destination failed: " + e.getMessage()).build();
        }
    }

    // --- Server registration tests (needs SAP instance + gateway) ---

    @Path("/camel/srfc-server")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response camelSrfcServer() {
        if (!sapConfigurator.isSapAvailable()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("sap.ashost not set").build();
        }
        try {
            sapConfigurator.ensureSapConfigured();
            String uri = "sap-srfc-server:" + SapConfigurator.SERVER_NAME + ":BAPI_FLCUST_GETLIST";
            SapSynchronousRfcServerEndpoint endpoint = context.getEndpoint(uri,
                    SapSynchronousRfcServerEndpoint.class);
            Structure response = endpoint.createResponse();
            @SuppressWarnings("unchecked")
            Table<Structure> customerList = response.get("CUSTOMER_LIST", Table.class);
            Structure customer = customerList.add();
            customer.put("CUSTOMERID", "00000099");
            customer.put("CUSTNAME", "Quarkus Native Test");

            return Response.ok("Camel sRFC server endpoint OK, response fields="
                    + response.size()).build();
        } catch (Exception e) {
            LOG.errorf(e, "Camel sRFC server failed");
            return Response.status(500).entity("Camel sRFC server failed: " + e.getMessage()).build();
        }
    }

    @Path("/camel/trfc-server")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response camelTrfcServer() {
        if (!sapConfigurator.isSapAvailable()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("sap.ashost not set").build();
        }
        try {
            sapConfigurator.ensureSapConfigured();
            String uri = "sap-trfc-server:" + SapConfigurator.SERVER_NAME + ":BAPI_FLCUST_GETLIST";
            SapTransactionalRfcServerEndpoint endpoint = context.getEndpoint(uri,
                    SapTransactionalRfcServerEndpoint.class);
            Structure response = endpoint.createResponse();
            @SuppressWarnings("unchecked")
            Table<Structure> customerList = response.get("CUSTOMER_LIST", Table.class);
            Structure customer = customerList.add();
            customer.put("CUSTOMERID", "00000099");
            customer.put("CUSTNAME", "Quarkus tRFC Test");

            return Response.ok("Camel tRFC server endpoint OK, response fields="
                    + response.size()).build();
        } catch (Exception e) {
            LOG.errorf(e, "Camel tRFC server failed");
            return Response.status(500).entity("Camel tRFC server failed: " + e.getMessage()).build();
        }
    }

    @Path("/camel/idoclist-server")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response camelIDocListServer() {
        if (!sapConfigurator.isSapAvailable()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("sap.ashost not set").build();
        }
        try {
            sapConfigurator.ensureSapConfigured();
            String uri = "sap-idoclist-server:" + SapConfigurator.SERVER_NAME + ":" + IDOC_TYPE;
            SapTransactionalIDocListServerEndpoint endpoint = context.getEndpoint(uri,
                    SapTransactionalIDocListServerEndpoint.class);

            return Response.ok("Camel IDocList server endpoint OK, uri=" + endpoint.getEndpointUri()).build();
        } catch (Exception e) {
            LOG.errorf(e, "Camel IDocList server failed");
            return Response.status(500).entity("Camel IDocList server failed: " + e.getMessage()).build();
        }
    }

    // --- EMF model tests (no SAP instance needed) ---

    @Path("/model/emf")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response testEmfModels() {
        try {
            var destData = RfcFactory.eINSTANCE.createDestinationData();
            destData.setAshost("testhost");
            String host = destData.getAshost();

            var serverData = RfcFactory.eINSTANCE.createServerData();
            serverData.setGwhost("testgw");
            String gw = serverData.getGwhost();

            var document = IdocFactory.eINSTANCE.createDocument();
            document.setIDocType("TEST_TYPE");
            String type = document.getIDocType();

            if ("testhost".equals(host) && "testgw".equals(gw) && "TEST_TYPE".equals(type)) {
                return Response.ok().build();
            }
            return Response.status(500, "EMF model values mismatch").build();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to instantiate EMF models");
            return Response.status(500, "EMF model error: " + e.getMessage()).build();
        }
    }
}
