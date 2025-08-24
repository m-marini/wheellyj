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

import java.util.StringJoiner;

public class RobotConfig {
    public static final String CURRENT_VERSION = "2";
    private String version;
    private boolean wifiActive;
    private String wifiSsid;
    private String wifiPassword;
    private String mqttBrokerHost;
    private int mqttBrokerPort;
    private String mqttUser;
    private String mqttPassword;

    public RobotConfig() {
    }

    public RobotConfig(boolean wifiActive, String wifiSsid, String wifiPassword, String mqttBrokerHost, int mqttBrokerPort, String mqttUser, String mqttPassword) {
        this.version = CURRENT_VERSION;
        this.wifiActive = wifiActive;
        this.wifiSsid = wifiSsid;
        this.wifiPassword = wifiPassword;
        this.mqttBrokerHost = mqttBrokerHost;
        this.mqttBrokerPort = mqttBrokerPort;
        this.mqttUser = mqttUser;
        this.mqttPassword = mqttPassword;
    }

    public String getMqttBrokerHost() {
        return mqttBrokerHost;
    }

    public void setMqttBrokerHost(String mqttBrokerHost) {
        this.mqttBrokerHost = mqttBrokerHost;
    }

    public int getMqttBrokerPort() {
        return mqttBrokerPort;
    }

    public void setMqttBrokerPort(int mqttBrokerPort) {
        this.mqttBrokerPort = mqttBrokerPort;
    }

    public String getMqttPassword() {
        return mqttPassword;
    }

    public void setMqttPassword(String mqttPassword) {
        this.mqttPassword = mqttPassword;
    }

    public String getMqttUser() {
        return mqttUser;
    }

    public void setMqttUser(String mqttUser) {
        this.mqttUser = mqttUser;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getWifiPassword() {
        return wifiPassword;
    }

    public void setWifiPassword(String wifiPassword) {
        this.wifiPassword = wifiPassword;
    }

    public String getWifiSsid() {
        return wifiSsid;
    }

    public void setWifiSsid(String wifiSsid) {
        this.wifiSsid = wifiSsid;
    }

    public boolean isWifiActive() {
        return wifiActive;
    }

    public void setWifiActive(boolean wifiActive) {
        this.wifiActive = wifiActive;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", RobotConfig.class.getSimpleName() + "[", "]")
                .add("wifiActive=" + wifiActive)
                .add("wifiSsid='" + wifiSsid + "'")
                .add("wifiPassword='" + wifiPassword + "'")
                .toString();
    }
}
