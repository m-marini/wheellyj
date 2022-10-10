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

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.mmarini.Tuple2;
import org.mmarini.swing.GridLayoutHelper;
import org.mmarini.wheelly.apis.NetworkConfig;
import org.mmarini.wheelly.apis.RestApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

import static java.lang.String.format;

public class WiFiFrame extends JFrame {
    public static final int VISIBLE_NETWORK_NUMBER = 10;
    private static final Dimension DEFAULT_SIZE = new Dimension(700, 450);

    private static final Logger logger = LoggerFactory.getLogger(WiFiFrame.class);
    private final JTextField address;
    private final JPasswordField password;
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
        password = new JPasswordField();
        activateBtn = new JButton("Activate");
        inactivateBtn = new JButton("Inactivate");
        reloadBtn = new JButton("Reload");
        active = new JLabel();
        networks = new DefaultListModel<>();
        this.netList = new JList<>(networks);

        netList.setVisibleRowCount(VISIBLE_NETWORK_NUMBER);
        createContent();

        reloadBtn.addActionListener(ev -> reload());
        activateBtn.addActionListener(ev -> act());
        inactivateBtn.addActionListener(ev -> inact());
    }

    private void act() {
        blockUI();
        logger.debug("reload");
        String ssid = netList.getSelectedValue();
        String psw = String.valueOf(password.getPassword());
        Single<NetworkConfig> confRequest = Single.fromCallable(() -> RestApi.postConfig(address.getText(), true, ssid, psw))
                .subscribeOn(Schedulers.io());
        Single<List<String>> listRequest = Single.fromCallable(() -> RestApi.getNetworks(address.getText()))
                .subscribeOn(Schedulers.io());


        confRequest.flatMap(
                l -> listRequest.map(c -> Tuple2.of(l, c))
        ).subscribe(
                t -> {
                    setNetworks(t._2);
                    setConfig(t._1);
                    releaseUI();
                    JOptionPane.showMessageDialog(this,
                            new String[]{
                                    format("Activated network: \"%s\"", t._1.getSsid()),
                                    "Wheelly restart required to reload new configuration."
                            },
                            "Activated",
                            JOptionPane.WARNING_MESSAGE);
                },
                err -> {
                    releaseUI();
                    handleError(err);
                });
    }

    private void blockUI() {
        activateBtn.setEnabled(false);
        inactivateBtn.setEnabled(false);
        reloadBtn.setEnabled(false);
        address.setEnabled(false);
        netList.setEnabled(false);
        password.setEnabled(false);
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    }

    private void createContent() {
        JPanel upper = new GridLayoutHelper<>(new JPanel())
                .modify("insets,2 at,0,0 noweight nofill").add("Address")
                .modify("at,1,0 weight,1,0 hfill").add(address)
                .modify("at,2,0 noweight nofill").add(reloadBtn)
                .getContainer();

        JPanel centerLeft = new GridLayoutHelper<>(new JPanel())
                .modify("insets,2 at,0,0 noweight nofill").add("Networks")
                .modify("insets,5 at,0,1 weight,1,1 fill n").add(new JScrollPane(netList))
                .getContainer();

        JPanel centerRight = new GridLayoutHelper<>(new JPanel())
                .modify("insets,4 at,0,0 noweight nofill nw").add("Status")
                .modify("at,1,0 weight,1,0").add(active)
                .modify("at,0,1 span,2,1").add("Password")
                .modify("at,0,2 weight,1,1 hfill").add(password)
                .getContainer();
        centerRight.setBorder(BorderFactory.createEtchedBorder());

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

        /**
         pane.setLayout(new FlowLayout());
         pane.add(address);
         pane.add(new JScrollPane(netList));
         pane.add(active);
         pane.add(password);
         pane.add(activateBtn);
         pane.add(inactivateBtn);
         pane.add(reloadBtn);
         */
    }

    private void handleError(Throwable err) {
        logger.error(err.getMessage(), err);
        JOptionPane.showMessageDialog(this, err.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void inact() {
        blockUI();
        logger.debug("reload");
        String ssid = netList.getSelectedValue();
        String psw = String.valueOf(password.getPassword());
        Single<NetworkConfig> confRequest = Single.fromCallable(() -> RestApi.postConfig(address.getText(), false, ssid, psw))
                .subscribeOn(Schedulers.io());
        Single<List<String>> listRequest = Single.fromCallable(() -> RestApi.getNetworks(address.getText()))
                .subscribeOn(Schedulers.io());


        confRequest.flatMap(
                l -> listRequest.map(c -> Tuple2.of(l, c))
        ).subscribe(
                t -> {
                    setNetworks(t._2);
                    setConfig(t._1);
                    releaseUI();
                    JOptionPane.showMessageDialog(this,
                            new String[]{
                                    format("Inactivated network: \"%s\"", t._1.getSsid()),
                                    "Wheelly restart required to reload new configuration.",
                                    "Wheelly will act as access point for the \"Wheelly\" network without pass phrase",
                                    "at default address 192.168.4.1."
                            },
                            "Inactivated",
                            JOptionPane.WARNING_MESSAGE);
                },
                err -> {
                    releaseUI();
                    handleError(err);
                });
    }

    private void releaseUI() {
        activateBtn.setEnabled(true);
        inactivateBtn.setEnabled(true);
        reloadBtn.setEnabled(true);
        address.setEnabled(true);
        netList.setEnabled(true);
        password.setEnabled(true);
        this.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

    private void reload() {
        blockUI();
        logger.debug("reload");
        Single<List<String>> listRequest = Single.fromCallable(() -> RestApi.getNetworks(address.getText()))
                .subscribeOn(Schedulers.io());

        Single<NetworkConfig> confRequest = Single.fromCallable(() -> RestApi.getNetworkConfig(address.getText()))
                .subscribeOn(Schedulers.io());

        listRequest.flatMap(
                l -> confRequest.map(c -> Tuple2.of(l, c))
        ).subscribe(
                t -> {
                    setNetworks(t._1);
                    setConfig(t._2);
                    releaseUI();
                },
                err -> {
                    releaseUI();
                    handleError(err);
                });
    }

    private void setConfig(NetworkConfig conf) {
        password.setText(conf.getPassword());
        int index = -1;
        for (int i = 0; i < networks.size(); i++) {
            if (networks.getElementAt(i).equals(conf.getSsid())) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            networks.insertElementAt(conf.getSsid(), 0);
            index = 0;
        }
        netList.setSelectedIndex(index);
        active.setText(conf.isActive() ? "Active" : "Inactive");
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
