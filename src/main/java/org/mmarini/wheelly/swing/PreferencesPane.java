package org.mmarini.wheelly.swing;

import net.java.games.input.Controller;
import net.java.games.input.ControllerEnvironment;

import javax.swing.*;

import static org.mmarini.wheelly.swing.RxJoystick.NONE_CONTROLLER;

/**
 *
 */
public class PreferencesPane extends JPanel {
    private final JList<String> joystickPort;
    private final JTextField baseUrl;

    /**
     *
     */
    public PreferencesPane() {
        this.joystickPort = new JList<>(new DefaultListModel<>());
        this.baseUrl = new JTextField(64);
        add(new JLabel("Joystick"));
        add(new JScrollPane(joystickPort));
        add(new JLabel("Wheelly URL"));
        add(baseUrl);
    }

    /**
     *
     */
    public String getBaseUrl() {
        return baseUrl.getText();
    }

    /**
     * @param baseUrl the base url
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl.setText(baseUrl);
    }

    /**
     *
     */
    public String getJoystickPort() {
        return joystickPort.getSelectedValue();
    }

    /**
     * @param joystickPort the joystick port
     */
    public PreferencesPane setJoystickPort(String joystickPort) {
        loadPorts();
        this.joystickPort.setSelectedValue(joystickPort, true);
        if (this.joystickPort.getSelectedIndex() < 0) {
            this.joystickPort.setSelectedValue(NONE_CONTROLLER, true);
        }
        return this;
    }

    /**
     *
     */
    private void loadPorts() {
        Controller[] controllers = ControllerEnvironment.getDefaultEnvironment().getControllers();
        DefaultListModel<String> model = (DefaultListModel<String>) joystickPort.getModel();
        model.clear();
        model.addElement(NONE_CONTROLLER);
        for (Controller controller : controllers) {
            if (controller.getType() == Controller.Type.STICK) {
                model.addElement(controller.getName());
            }
        }
        joystickPort.setSelectedValue(NONE_CONTROLLER, true);
    }
}
