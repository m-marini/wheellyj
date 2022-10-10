/*
 * MIT License
 *
 * Copyright (c) 2022 Marco Marini
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.mmarini.wheelly.apis;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.yaml.schema.Validator.*;

public class RestApi {
    private static final Logger logger = LoggerFactory.getLogger(RestApi.class);

    private static final Validator NET_LIST = objectPropertiesRequired(Map.of(
            "networks", arrayItems(string())
    ), List.of("networks"));

    public static NetworkConfig getNetworkConfig(String address) throws IOException {
        requireNonNull(address);
        String apiUrl = format("http://%s/api/v1/wheelly/networks/network", address);
        logger.info("GET {}", apiUrl);
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(apiUrl);
        Response response = target.request(MediaType.APPLICATION_JSON).get();
        if (response.getStatus() != 200) {
            throw new IOException(format("Http Status %d", response.getStatus()));
        }
        return response.readEntity(NetworkConfig.class);
    }

    public static List<String> getNetworkList(String address) throws IOException {
        requireNonNull(address);
        String apiUrl = format("http://%s/api/v1/wheelly/networks", address);
        logger.info("GET {}", apiUrl);
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(apiUrl);
        Response response = target.request(MediaType.APPLICATION_JSON).get();
        if (response.getStatus() != 200) {
            throw new IOException(format("Http Status %d", response.getStatus()));
        }
        JsonNode netList = response.readEntity(JsonNode.class);
        Locator locator = Locator.root();
        NET_LIST.apply(locator).accept(netList);
        return locator.path("networks").elements(netList)
                .map(l -> l.getNode(netList))
                .map(JsonNode::asText)
                .collect(Collectors.toList());
    }

    public static NetworkConfig postConfig(Object address, boolean b, String ssid, String password) throws IOException {
        requireNonNull(address);
        requireNonNull(ssid);
        requireNonNull(password);
        String apiUrl = format("http://%s/api/v1/wheelly/networks/network", address);
        NetworkConfig body = new NetworkConfig(b, ssid, password);
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(apiUrl);
        Response response = target.request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(body, MediaType.APPLICATION_JSON));
        if (response.getStatus() != 200) {
            throw new IOException(format("Http Status %d", response.getStatus()));
        }
        return response.readEntity(NetworkConfig.class);
    }
}
