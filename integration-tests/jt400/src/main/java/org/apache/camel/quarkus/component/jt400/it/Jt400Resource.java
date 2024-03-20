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
package org.apache.camel.quarkus.component.jt400.it;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.ibm.as400.access.QueuedMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.jt400.Jt400Endpoint;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/jt400")
@ApplicationScoped
public class Jt400Resource {

    @ConfigProperty(name = "cq.jt400.url")
    String jt400Url;

    @ConfigProperty(name = "cq.jt400.username")
    String jt400USername;

    @ConfigProperty(name = "cq.jt400.password")
    String jt400Password;

    @ConfigProperty(name = "cq.jt400.keyed-queue")
    String jt400KeyedQueue;

    @ConfigProperty(name = "cq.jt400.library")
    String jt400Library;

    @ConfigProperty(name = "cq.jt400.lifo-queue")
    String jt400LifoQueue;

    @ConfigProperty(name = "cq.jt400.message-queue")
    String jt400MessageQueue;

    @ConfigProperty(name = "cq.jt400.user-space")
    String jt400UserSpace;

    @Inject
    ProducerTemplate producerTemplate;

    @Inject
    ConsumerTemplate consumerTemplate;

    @Path("/dataQueue/read/")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response keyedDataQueueRead(String key, @QueryParam("format") String format,
            @QueryParam("searchType") String searchType) {

        boolean keyed = key != null && !key.isEmpty();
        String _format = Optional.ofNullable(format).orElse("text");
        String _searchType = Optional.ofNullable(searchType).orElse("EQ");
        StringBuilder suffix = new StringBuilder();

        if (keyed) {
            suffix.append(jt400KeyedQueue)
                    .append(String.format("?keyed=true&format=%s&searchKey=%s&searchType=%s", _format, key, _searchType));
        } else {
            suffix.append(jt400LifoQueue).append(String.format("?readTimeout=100&format=%s", _format));
        }

        Exchange ex = consumerTemplate.receive(getUrlForLibrary(suffix.toString()));

        if ("binary".equals(format)) {
            return generateResponse(new String(ex.getIn().getBody(byte[].class), Charset.forName("Cp037")), ex);
        }
        return generateResponse(ex.getIn().getBody(String.class), ex);

    }

    @Path("/dataQueue/write/")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public Response keyedDataQueueWrite(@QueryParam("key") String key,
            @QueryParam("searchType") String searchType,
            String data) {
        boolean keyed = key != null;
        StringBuilder suffix = new StringBuilder();
        Map<String, Object> headers = new HashMap<>();

        if (keyed) {
            suffix.append(jt400KeyedQueue).append("?keyed=true");
            headers.put(Jt400Endpoint.KEY, key);
        } else {
            suffix.append(jt400LifoQueue);
        }

        Object ex = producerTemplate.requestBodyAndHeaders(
                getUrlForLibrary(suffix.toString()),
                "Hello " + data,
                headers);
        return Response.ok().entity(ex).build();
    }

    @Path("/messageQueue/write/")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response messageQueueWrite(String data) {
        Object ex = producerTemplate.requestBody(getUrlForLibrary(jt400MessageQueue), "Hello " + data);

        return Response.ok().entity(ex).build();
    }

    @Path("/messageQueue/read/")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response messageQueueRead() {
        Exchange ex = consumerTemplate.receive(getUrlForLibrary(jt400MessageQueue));

        return generateResponse(ex.getIn().getBody(String.class), ex);
    }

    @Path("/programCall")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    public Response programCall() throws Exception {
        Exchange ex = producerTemplate.request(getUrl("/qsys.lib/QUSRTVUS.PGM?fieldsLength=20,4,4,16&outputFieldsIdx=3"),
                exchange -> {
                    String userSpace = String.format("%-10s", jt400UserSpace);
                    String userLib = String.format("%-10s", jt400Library);

                    Object[] parms = new Object[] {
                            userSpace + userLib, // Qualified user space name
                            1, // starting position
                            16, // length of data
                            "" // output
                    };
                    exchange.getIn().setBody(parms);
                });

        return Response.ok().entity(ex.getIn().getBody(Object[].class)[3]).build();
    }

    private String getUrlForLibrary(String suffix) {
        return String.format("jt400://%s:%s@%s%s", jt400USername, jt400Password, jt400Url,
                "/QSYS.LIB/" + jt400Library + ".LIB/" + suffix);
    }

    private String getUrl(String suffix) {
        return String.format("jt400://%s:%s@%s%s", jt400USername, jt400Password, jt400Url, suffix);
    }

    Response generateResponse(String result, Exchange ex) {
        Map<String, Object> retVal = new HashMap<>();

        retVal.put("result", result);
        ex.getIn().getHeaders().entrySet().stream().forEach(e -> {
            if (e.getValue() instanceof QueuedMessage) {
                retVal.put(e.getKey(), "QueuedMessage: " + ((QueuedMessage) e.getValue()).getText());
            } else {
                retVal.put(e.getKey(), e.getValue());
            }
        });

        return Response.ok().entity(retVal).build();

    }
}
