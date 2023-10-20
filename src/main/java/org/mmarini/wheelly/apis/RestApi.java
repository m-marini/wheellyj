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
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;
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
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class RestApi {
    private static final Logger logger = LoggerFactory.getLogger(RestApi.class);

    /**
     * Returns the network configuration
     *
     * @param address the robot address
     * @throws IOException in case of error
     */
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

    /**
     * Returns the networks list
     *
     * @param address the robot address
     * @throws IOException in case of error
     */
    public static List<String> getNetworks(String address) throws IOException {
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
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        JsonNode jsonSchemeNode = Utils.fromResource("/network-list-schema.yml");
        JsonSchema jsonSchema = factory.getSchema(jsonSchemeNode);
        Set<ValidationMessage> errors = jsonSchema.validate(netList);
        if (!errors.isEmpty()) {
            String text = errors.stream()
                    .map(ValidationMessage::toString)
                    .collect(Collectors.joining(", "));
            throw new RuntimeException(format("Errors: %s", text));
        }
        Locator locator = Locator.root();
        return locator.path("networks").elements(netList)
                .map(l -> l.getNode(netList))
                .map(JsonNode::asText)
                .collect(Collectors.toList());
    }

    /**
     * Changes the configuration and returns the new configuration
     *
     * @param address  the robot address
     * @param active   true if activated configuration
     * @param ssid     the network ssid
     * @param password the pass phrase of network
     * @throws IOException in case of error
     */
    public static NetworkConfig postConfig(Object address, boolean active, String ssid, String password) throws IOException {
        requireNonNull(address);
        requireNonNull(ssid);
        requireNonNull(password);
        String apiUrl = format("http://%s/api/v1/wheelly/networks/network", address);
        NetworkConfig body = new NetworkConfig(active, ssid, password);
        logger.info("POST {}", apiUrl);
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
