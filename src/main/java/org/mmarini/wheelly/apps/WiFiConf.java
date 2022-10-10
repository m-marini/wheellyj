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

package org.mmarini.wheelly.apps;


import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.jetbrains.annotations.NotNull;
import org.mmarini.wheelly.apis.NetworkConfig;
import org.mmarini.wheelly.apis.RestApi;
import org.mmarini.wheelly.swing.Messages;
import org.mmarini.wheelly.swing.WiFiFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static java.lang.String.format;

public class WiFiConf {
    private static final Logger logger = LoggerFactory.getLogger(WiFiConf.class);

    @NotNull
    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(WiFiConf.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("Wheelly.title"))
                .description("Configure wifi.");
        parser.addArgument("-v", "--version")
                .action(Arguments.version())
                .help("show current version");
        parser.addArgument("-s", "--ssid")
                .help("specify the SSID (network identification)");
        parser.addArgument("-a", "--address")
                .setDefault("192.168.4.1")
                .help("specify the host api address");
        parser.addArgument("-p", "--password")
                .help("specify the network pass phrase");
        parser.addArgument("action")
                .choices("list", "show", "act", "inact", "win")
                .help("specify the action");

        return parser;
    }

    public static void main(String[] args) {
        new WiFiConf().run(args);
    }

    private Namespace args;

    private void act() throws IOException {
        String ssid = args.getString("ssid");
        if (ssid == null) {
            throw new IllegalArgumentException("Missing SSID");
        }
        String password = args.getString("password");
        if (password == null) {
            throw new IllegalArgumentException("Missing pass phrase");
        }
        NetworkConfig conf = RestApi.postConfig(args.get("address"), true, ssid, password);
        System.out.printf("Activated network: \"%s\"%n", conf.getSsid());
        System.out.println("Wheelly restart required to reload new configuration.");
    }

    private void inact() throws IOException {
        String ssid = args.getString("ssid");
        if (ssid == null) {
            throw new IllegalArgumentException("Missing SSID");
        }
        String password = args.getString("password");
        if (password == null) {
            throw new IllegalArgumentException("Missing pass phrase");
        }
        NetworkConfig conf = RestApi.postConfig(args.get("address"), false, ssid, password);
        System.out.printf("Inactivated network: \"%s\"%n", conf.getSsid());
        System.out.println("Wheelly restart required to reload new configuration.");
        System.out.println("Wheelly will act as access point for the \"Wheelly\" network without pass phrase at default address 192.168.4.1.");
    }

    private void list() throws IOException {
        List<String> netList = RestApi.getNetworks(args.getString("address"));
        System.out.println("Networks");
        for (String network : netList) {
            System.out.println("  " + network);
        }
    }

    private void run(String[] args) {
        ArgumentParser parser = createParser();
        try {
            this.args = parser.parseArgs(args);
            String action = this.args.getString("action");
            switch (action) {
                case "list":
                    list();
                    break;
                case "show":
                    show();
                    break;
                case "act":
                    act();
                    break;
                case "inact":
                    inact();
                    break;
                case "win":
                    win();
                    break;
                default:
                    throw new IllegalArgumentException(format("Wrong action \"%s\"", action));
            }
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            System.exit(1);
        }
    }

    private void show() throws IOException {
        NetworkConfig conf = RestApi.getNetworkConfig(args.getString("address"));
        System.out.println("Configuration:");
        System.out.printf(" Status: %s%n", conf.isActive() ? "active" : "inactive");
        System.out.printf(" Network SSID: \"%s\"%n", conf.getSsid());
        System.out.println(" Password: ***");
    }

    private void win() throws IOException {
        new WiFiFrame().start(args.getString("address"));
    }
}