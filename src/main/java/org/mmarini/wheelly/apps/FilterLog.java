package org.mmarini.wheelly.apps;

import com.fasterxml.jackson.databind.JsonNode;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.mmarini.Tuple2;
import org.mmarini.swing.Messages;
import org.mmarini.wheelly.apis.*;
import org.mmarini.yaml.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;


/**
 * Filter the status log
 * Parses the status log seeking for specific records
 */
public class FilterLog {
    public static final double RANGE = 4.9;
    public static final int NUM_RECORDS = 10;
    private static final Logger logger = LoggerFactory.getLogger(FilterLog.class);

    /**
     * Returns the command line arguments parser
     */
    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(MatrixMonitor.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("Wheelly.title"))
                .description("Filter robot status log.");
        parser.addArgument("--version")
                .action(Arguments.version())
                .help("show current version");
        parser.addArgument("inference")
                .required(true)
                .help("specify the inference file");
        return parser;
    }

    static SimRobot createRobot(RobotStatus status) throws IOException {
        File file = new File("conf/robots/simRobot0Obstacles.yml");
        JsonNode root = Utils.fromFile(file);
        SimRobot robot = SimRobot.create(root, file);
        robot.simulationTime(status.simulationTime());
        robot.robotDir(status.direction());
        Point2D location = status.location();
        robot.robotPos(location.getX(), location.getY());
        return robot;
    }

    static List<Tuple2<WorldModel, RobotCommands>> filter(File file1, int start, int size) {
        // Seek inference
        List<Tuple2<WorldModel, RobotCommands>> result = new ArrayList<>();
        try (InferenceFileReader file = InferenceFileReader.fromFile(file1)) {
            int recordNumber = 0;
            while (recordNumber < start) {
                file.readRecord();
                recordNumber++;
            }
            for (int i = 0; i < size; i++) {
                result.add(file.readRecord());
            }
        } catch (IOException e) {
        }
        return result;
    }

    /**
     * Runs the application
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            Namespace args1 = createParser().parseArgs(args);
            new FilterLog(args1).run();
        } catch (ArgumentParserException ignored) {
            System.exit(4);
        } catch (Throwable e) {
            logger.atError().setCause(e).log("Error running filter log");
            System.exit(4);
        }
    }

    static int seek(File file1) {
        // Read inference
        try (InferenceFileReader file = InferenceFileReader.fromFile(file1)) {
            for (int recordNumber = 0; ; recordNumber++) {
                Point2D loc = file.readRecord()
                        ._1.robotStatus().location();
                if ((abs(loc.getX()) > RANGE || abs(loc.getY()) > RANGE)) {
                    return recordNumber;
                }
            }
        } catch (IOException e) {
            return -1;
        }
    }

    private static void simulate(List<Tuple2<WorldModel, RobotCommands>> list, int numRecord) throws IOException {
        for (int i = 0; i < list.size(); i++) {
            Tuple2<WorldModel, RobotCommands> record = list.get(i);
            RobotStatus robotStatus = record._1.robotStatus();
            Point2D location = robotStatus.location();
            logger.atInfo().log("Record {}: RobotStatus @{}, R{}, t={} ms Backward={} Forward={}",
                    numRecord + i, location,
                    robotStatus.direction().toIntDeg(),
                    robotStatus.simulationTime(),
                    robotStatus.canMoveBackward(),
                    robotStatus.canMoveForward());

            SimRobot robot = createRobot(robotStatus);
            logger.atInfo().log("    SimRobot @{}, R{}, t={} ms",
                    robot.location(),
                    robot.direction().toIntDeg(),
                    robot.simulationTime());
            RobotCommands cmd = record._2;
            logger.atInfo().log("    Command {}",
                    cmd);

            Collection<Obstacle> map = robot.obstacleMap();

            map.stream().min(Comparator.comparingDouble(obs ->
                            obs.centre().distance(location)))
                    .ifPresent(obs ->
                            logger.atInfo().log("    Obstacle {} D{}",
                                    obs.centre(),
                                    obs.centre().distance(location)));
            /*
                    // Validates the command
        int scanDeg = command.scanDirection();
        if (abs(scanDeg) > 90) {
            logger.atError().log("Wrong scan direction {}", scanDeg);
            return;
        }
        robot.scan(scanDeg);
        RobotControllerStatus prevStatus = status.getAndUpdate(s -> s.command(command));
        if (!Objects.equals(prevStatus.command(), command)) {
            // command changed
            syncActions(status.get().robotStatus());
        }
        commands.onNext(command);

             */
        }
    }

    private final Namespace args;

    /**
     * Creates the application
     *
     * @param args the parsed arguments
     */
    protected FilterLog(Namespace args) {
        this.args = requireNonNull(args);
    }

    private void run() throws IOException {
        logger.atInfo().log("Running FilterLog...");
        String filename = args.getString("inference");
        logger.atInfo().log("{}", filename);
        File file1 = new File(filename);

        int numRecord = seek(file1);
        numRecord = max(0, numRecord - NUM_RECORDS + 3);
        List<Tuple2<WorldModel, RobotCommands>> list = filter(file1, numRecord, NUM_RECORDS);

        logger.atInfo().log("from {} size {}", numRecord, list.size());

        simulate(list, numRecord);

        logger.atInfo().log("Completed FilterLog.");
    }
}
