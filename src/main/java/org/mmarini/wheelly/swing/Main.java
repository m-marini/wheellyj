package org.mmarini.wheelly.swing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    /**
     * @param args the command arguments
     */
    public static void main(String[] args) {
        logger.info("Wheelly started");
        UIController.create().start();
        logger.info("Wheelly completed");
    }
}
