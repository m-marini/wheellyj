/*
 *
 * Copyright (c) 2022 Marco Marini, marco.marini@mmarini.org
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 *    END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini.wheelly.swing;

import org.mmarini.swing.GridLayoutHelper;
import org.mmarini.wheelly.apis.NetworkConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.awt.*;
import java.io.IOException;

import static java.lang.String.format;

public class Setup {

    public static final String DEFAULT_SERVER = "http://192.168.4.1";
    //public static final String DEFAULT_SERVER = "http://192.168.1.11";
    static final String NETWORKS_URI = "/api/v1/wheelly/networks";
    static final String NETWORK_URI = "/api/v1/wheelly/networks/network";
    private static final Logger logger = LoggerFactory.getLogger(Setup.class);

    public static void main(String[] args) {
        new Setup().start();
    }

    private final JFrame frame;
    private final JTextField server;
    private final JButton refresh;
    private final JButton save;
    private final JCheckBox active;
    private final JTextField password;
    private final DefaultListModel<String> networks;
    private final JList<String> networksList;

    protected Setup() {
        frame = new JFrame("Setup");
        server = new JTextField(30);
        refresh = new JButton("Refresh");
        save = new JButton("Save");
        networks = new DefaultListModel<>();
        active = new JCheckBox("Active");
        password = new JTextField(30);
        networksList = new JList<>(networks);

        refresh.addActionListener(ev -> {
            loadNetworks();
        });

        save.addActionListener(ev -> {
            saveConfig();
        });

        networksList.addListSelectionListener(ev -> {
            save.setEnabled(networksList.getSelectedIndex() >= 0);
        });

        server.setText(DEFAULT_SERVER);
        active.setSelected(true);
        save.setEnabled(false);

        frame.getContentPane().add(createContent(), BorderLayout.CENTER);
    }

    private Container createContent() {
        return new GridLayoutHelper<>(new JPanel())
                .modify("insets,4 at,0,0 e weight,1,0 fill").add(new JLabel("Server"))
                .modify("at,1,0 w weight,1,0 fill").add(server)
                .modify("at,1,1 center weight,1,0 fill").add(new JScrollPane(networksList))
                .modify("at,2,1 n weight,0,0 nofill").add(refresh)
                .modify("at,1,2 w weight,0,0 nofill").add(active)
                .modify("at,0,3 e weight,0,0 nofill").add(new JLabel("Password"))
                .modify("at,1,3 w weight,1,0 fill").add(password)
                .modify("at,2,4 center weight,0,0 nofill").add(save)
                .getContainer();
    }

    private Networks getNetworks() throws IOException {
        Client client = ClientBuilder.newClient();
        String url = server.getText() + NETWORKS_URI;
        WebTarget target = client.target(url);
        Response response = target.request(MediaType.APPLICATION_JSON).get();
        if (response.getStatus() != 200) {
            throw new IOException(format("Http Status %d", response.getStatus()));
        }
        return response.readEntity(Networks.class);
    }

    private void loadNetworks() {
        networks.removeAllElements();
        try {
            Networks t = getNetworks();
            for (String network : t.getNetworks()) {
                networks.addElement(network);
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    NetworkConfig postNetwork(NetworkConfig cfg) throws IOException {
        Client client = ClientBuilder.newClient();
        String url = server.getText() + NETWORK_URI;
        WebTarget target = client.target(url);
        Response response = target.request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(cfg, MediaType.APPLICATION_JSON));
        if (response.getStatus() != 200) {
            throw new IOException(format("Http Status %d", response.getStatus()));
        }
        return response.readEntity(NetworkConfig.class);
    }

    private void saveConfig() {
        NetworkConfig cfg = new NetworkConfig(active.isSelected(), networksList.getSelectedValue(), password.getText());
        try {
            postNetwork(cfg);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private void start() {
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
        loadNetworks();
    }

}
