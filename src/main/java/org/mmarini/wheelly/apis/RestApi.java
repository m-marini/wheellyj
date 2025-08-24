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
import com.networknt.schema.ValidationMessage;
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.yaml.Locator;
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
    public static RobotConfig getConfig(String address) throws IOException {
        requireNonNull(address);
        String apiUrl = format("http://%s/api/v2/wheelly/config", address);
        logger.info("GET {}", apiUrl);
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(apiUrl);
        Response response = target.request(MediaType.APPLICATION_JSON).get();
        if (response.getStatus() != 200) {
            throw new IOException(format("Http Status %d", response.getStatus()));
        }
        return response.readEntity(RobotConfig.class);
    }

    /**
     * Returns the network configuration
     *
     * @param address the robot address
     * @throws IOException in case of error
     */
    public static String getWheellyId(String address) throws IOException {
        requireNonNull(address);
        String apiUrl = format("http://%s/api/v2/wheelly/id", address);
        logger.info("GET {}", apiUrl);
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(apiUrl);
        Response response = target.request(MediaType.APPLICATION_JSON).get();
        if (response.getStatus() != 200) {
            throw new IOException(format("Http Status %d", response.getStatus()));
        }
        return response.readEntity(JsonNode.class).path("id").asText();
    }

    /**
     * Returns the networks list
     *
     * @param address the robot address
     * @throws IOException in case of error
     */
    public static List<String> getNetworks(String address) throws IOException {
        requireNonNull(address);
        String apiUrl = format("http://%s/api/v2/wheelly/networks", address);
        logger.info("GET {}", apiUrl);
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(apiUrl);
        Response response = target.request(MediaType.APPLICATION_JSON).get();
        if (response.getStatus() != 200) {
            throw new IOException(format("Http Status %d", response.getStatus()));
        }
        JsonNode netList = response.readEntity(JsonNode.class);
        Set<ValidationMessage> errors = JsonSchemas.instance().validate(netList, "https://mmarini.org/wheelly/network-list-schema");
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
     * @param address the robot address
     * @param config  the configuration
     * @throws IOException in case of error
     */
    public static RobotConfig postConfig(String address, RobotConfig config) throws IOException {
        requireNonNull(config);
        String apiUrl = format("http://%s/api/v2/wheelly/config", address);
        logger.info("POST {}", apiUrl);
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(apiUrl);
        Response response = target.request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(config, MediaType.APPLICATION_JSON));
        if (response.getStatus() != 200) {
            throw new IOException(format("Http Status %d", response.getStatus()));
        }
        return response.readEntity(RobotConfig.class);
    }

    /**
     * Changes the configuration and returns the new configuration
     *
     * @param address the robot address
     * @throws IOException in case of error
     */
    public static JsonNode postRestart(String address) throws IOException {
        requireNonNull(address);
        String apiUrl = format("http://%s/api/v2/wheelly/restart", address);
        logger.info("POST {}", apiUrl);
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(apiUrl);
        Response response = target.request(MediaType.APPLICATION_JSON)
                .post(Entity.entity("", MediaType.APPLICATION_JSON));
        if (response.getStatus() != 200) {
            throw new IOException(format("Http Status %d", response.getStatus()));
        }
        return response.readEntity(JsonNode.class);
    }
}
