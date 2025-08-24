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

package org.mmarini.wheelly.swing;

import com.fasterxml.jackson.databind.JsonNode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.mmarini.swing.GridLayoutHelper;
import org.mmarini.wheelly.apis.RestApi;
import org.mmarini.wheelly.apis.RobotConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;

public class WiFiFrame extends JFrame {
    public static final int VISIBLE_NETWORK_NUMBER = 10;
    private static final Dimension DEFAULT_SIZE = new Dimension(700, 450);

    private static final Logger logger = LoggerFactory.getLogger(WiFiFrame.class);
    private final JTextField address;
    private final JPasswordField wifiPassword;
    private final JTextField wheellyId;
    private final JTextField mqttBrokerUri;
    private final JTextField mqttUser;
    private final JFormattedTextField mqttPort;
    private final JPasswordField mqttPassword;
    private final JButton activateBtn;
    private final JButton inactivateBtn;
    private final JButton reloadBtn;
    private final DefaultListModel<String> networks;
    private final JList<String> netList;
    private final JLabel active;

    public WiFiFrame() throws HeadlessException {
        setTitle("WiFi Configuration");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        address = new JTextField();
        wheellyId = new JTextField();
        wifiPassword = new JPasswordField();
        activateBtn = new JButton("Activate");
        inactivateBtn = new JButton("Inactivate");
        reloadBtn = new JButton("Reload");
        active = new JLabel();
        networks = new DefaultListModel<>();
        this.netList = new JList<>(networks);
        this.mqttBrokerUri = new JTextField();
        this.mqttUser = new JTextField();
        this.mqttPort = new JFormattedTextField();
        this.mqttPassword = new JPasswordField();

        netList.setVisibleRowCount(VISIBLE_NETWORK_NUMBER);
        mqttPort.setValue(1883);
        wheellyId.setEditable(false);
        createContent();

        reloadBtn.addActionListener(ev -> reload());
        activateBtn.addActionListener(ev -> act());
        inactivateBtn.addActionListener(ev -> inact());
    }

    private void act() {
        enableUI(false);
        logger.debug("reload");
        Single<RobotConfig> confRequest = Single.fromCallable(() -> RestApi.postConfig(address.getText(), config(true)))
                .subscribeOn(Schedulers.io());
        Single<JsonNode> restartRequest = Single.fromCallable(() ->
                        RestApi.postRestart(address.getText()))
                .subscribeOn(Schedulers.io());
        confRequest.flatMap(ignored ->
                        restartRequest)
                .delay(1000, TimeUnit.MILLISECONDS)
                .subscribe(this::onActConfirm,
                        err -> {
                            enableUI(true);
                            handleError(err);
                        });
    }

    private RobotConfig config(boolean wifiActive) {
        return new RobotConfig(wifiActive,
                netList.getSelectedValue(),
                String.valueOf(wifiPassword.getPassword()),
                mqttBrokerUri.getText(),
                (Integer) mqttPort.getValue(),
                mqttUser.getText(),
                String.valueOf(mqttPassword.getPassword())
        );
    }

    private void createContent() {
        JPanel upper = new GridLayoutHelper<>(new JPanel())
                .modify("insets,2")
                .modify("at,0,0 noweight nofill").add("Address")
                .modify("at,1,0 weight,1,0 hfill").add(address)
                .modify("at,0,1 noweight nofill").add("Wheelly ID")
                .modify("at,1,1 weight,1,0 hfill").add(wheellyId)
                .modify("at,2,0 noweight nofill s vspan,2").add(reloadBtn)
                .getContainer();

        JPanel centerLeft = new GridLayoutHelper<>(new JPanel())
                .modify("insets,2 at,0,0 noweight nofill").add("Networks")
                .modify("insets,5 at,0,1 weight,1,1 fill n").add(new JScrollPane(netList))
                .getContainer();

        JPanel wifiConf = new GridLayoutHelper<>(new JPanel())
                .modify("insets,4 w")
                .modify("at,0,0 noweight nofill").add("Wifi Status")
                .modify("at,1,0 hw,1").add(active)
                .modify("at,0,1 noweight span,2,1").add("Wifi Password")
                .modify("at,0,2 hw,1 hfill").add(wifiPassword)
                .getContainer();
        wifiConf.setBorder(BorderFactory.createTitledBorder("WiFi Confgiuration"));

        JPanel mqttConf = new GridLayoutHelper<>(new JPanel())
                .modify("insets,4 w")
                .modify("at,0,0 noweight nofill").add("Broker host")
                .modify("at,0,1 hw,1 hfill").add(mqttBrokerUri)
                .modify("at,0,2 noweight nofill").add("Broker Port")
                .modify("at,0,3 hw,1 hfill").add(mqttPort)
                .modify("at,0,4 noweight nofill").add("MQTT User")
                .modify("at,0,5 hw,1 hfill").add(mqttUser)
                .modify("at,0,6 noweight nofill").add("MQTT Password")
                .modify("at,0,7 hw,1 hfill").add(mqttPassword)
                .getContainer();
        mqttConf.setBorder(BorderFactory.createTitledBorder("MQTT Configuration"));

        JPanel centerRight = new GridLayoutHelper<>(new JPanel())
                .modify("insets,2")
                .modify("at,0,0 noweight fill").add(wifiConf)
                .modify("at,0,1 weight,1,1 fill").add(mqttConf)
                .getContainer();
        JPanel center = new GridLayoutHelper<>(new JPanel())
                .modify("insets,2 at,0,0 weight,1,1 fill").add(centerLeft)
                .modify("at,1,0").add(centerRight)
                .getContainer();

        JPanel bottom = new GridLayoutHelper<>(new JPanel())
                .modify("insets,2 at,0,0 weight,1,1 nofill e").add(inactivateBtn)
                .modify("at,1,0 noweight").add(activateBtn)
                .getContainer();

        Container pane = this.getContentPane();
        new GridLayoutHelper<>(pane)
                .modify("insets,2 at,0,0 weight,1,0 fill").add(upper)
                .modify("at,0,1 weight,1,1 fill").add(center)
                .modify("at,0,2 weight,1,0 fill").add(bottom);
    }

    private void handleError(Throwable err) {
        logger.error(err.getMessage(), err);
        JOptionPane.showMessageDialog(this, err.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void enableUI(boolean enabled) {
        activateBtn.setEnabled(enabled);
        inactivateBtn.setEnabled(enabled);
        reloadBtn.setEnabled(enabled);
        address.setEnabled(enabled);
        netList.setEnabled(enabled);
        wifiPassword.setEnabled(enabled);
        mqttBrokerUri.setEnabled(enabled);
        mqttPort.setEnabled(enabled);
        mqttUser.setEnabled(enabled);
        mqttPassword.setEnabled(enabled);

        this.setCursor(Cursor.getPredefinedCursor(enabled
                ? Cursor.DEFAULT_CURSOR
                : Cursor.WAIT_CURSOR));
    }

    private void inact() {
        enableUI(false);
        logger.debug("reload");
        Single<RobotConfig> confRequest = Single.fromCallable(() -> RestApi.postConfig(address.getText(), config(false)))
                .subscribeOn(Schedulers.io());
        Single<JsonNode> restartRequest = Single.fromCallable(() -> RestApi.postRestart(address.getText()))
                .subscribeOn(Schedulers.io());

        confRequest.flatMap(ignored ->
                        restartRequest)
                .subscribe(this::onInactConfig,
                        err -> {
                            enableUI(true);
                            handleError(err);
                        });
    }

    /**
     * Handles the activation confirmation
     *
     * @param restart the json node response
     */
    private void onActConfirm(JsonNode restart) {
        enableUI(true);
        JOptionPane.showMessageDialog(this,
                new String[]{
                        format("Activated network: \"%s\"", netList.getSelectedValue()),
                        "Wheelly has been restarted to reload new configuration."
                },
                "Activated",
                JOptionPane.WARNING_MESSAGE);
        reload();
    }

    /**
     * Handles the inactivation confirmation
     *
     * @param restart the configuration
     */
    private void onInactConfig(JsonNode restart) {
        enableUI(true);
        JOptionPane.showMessageDialog(this,
                new String[]{
                        format("Inactivated network: \"%s\"", netList.getSelectedValue()),
                        "Wheelly has been restarted to reload new configuration.",
                        "Wheelly will act as access point for the \"Wheelly\" network without pass phrase",
                        "at default address 192.168.4.1."
                },
                "Inactivated",
                JOptionPane.WARNING_MESSAGE);
        reload();
    }

    private void reload() {
        enableUI(false);
        logger.debug("reload");
        Single<String> idRequest = Single.fromCallable(() -> RestApi.getWheellyId(address.getText()))
                .subscribeOn(Schedulers.io())
                .doOnSuccess(this::setWheellyId);
        Single<List<String>> listRequest = Single.fromCallable(() -> RestApi.getNetworks(address.getText()))
                .subscribeOn(Schedulers.io())
                .doOnSuccess(this::setNetworks);
        Single<RobotConfig> confRequest = Single.fromCallable(() -> RestApi.getConfig(address.getText()))
                .subscribeOn(Schedulers.io())
                .doOnSuccess(this::setConfig);

        Flowable.merge(
                idRequest.map(ignore -> true).toFlowable(),
                Flowable.concat(
                        listRequest.map(ignore -> true).toFlowable(),
                        confRequest.map(ignore -> true).toFlowable()
                )
        ).subscribe(
                t ->
                        enableUI(true),
                err -> {
                    enableUI(true);
                    handleError(err);
                });
    }

    private void setWheellyId(String id) {
        wheellyId.setText(id);
    }

    private void setConfig(RobotConfig conf) {
        wifiPassword.setText(conf.getWifiPassword());
        int index = -1;
        for (int i = 0; i < networks.size(); i++) {
            if (networks.getElementAt(i).equals(conf.getWifiSsid())) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            networks.insertElementAt(conf.getWifiSsid(), 0);
            index = 0;
        }
        netList.setSelectedIndex(index);
        active.setText(conf.isWifiActive() ? "Active" : "Inactive");
        mqttBrokerUri.setText(conf.getMqttBrokerHost());
        mqttPort.setValue(conf.getMqttBrokerPort());
        mqttUser.setText(conf.getMqttUser());
        mqttPassword.setText(conf.getMqttPassword());
    }

    private void setNetworks(List<String> list) {
        Collections.sort(list);
        networks.removeAllElements();
        networks.addAll(list);
    }

    public void start(String address) {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(DEFAULT_SIZE);
        Point location = new Point(
                (screenSize.width - DEFAULT_SIZE.width) / 2,
                (screenSize.height - DEFAULT_SIZE.height) / 2
        );
        setLocation(location);
        this.address.setText(address);
        reload();
        setVisible(true);
    }
}
