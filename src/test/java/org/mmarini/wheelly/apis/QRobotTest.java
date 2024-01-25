package org.mmarini.wheelly.apis;

import org.mmarini.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

public class QRobotTest {
    public static final int NUM_ITER = 10;
    private static final double MAX_SIGNAL_DISTANCE = 3;
    private static final Logger logger = LoggerFactory.getLogger(QRobotTest.class);

    public static void main(String[] args) {
        QRobotTest test = new QRobotTest();
        logger.atInfo().log("Warming up ...");
        test.test("test0", 10, test::test0);
        test.test("testq", 10, test::testq);

        logger.atInfo().log("Testing ...");
        test.test("test0", 1000, test::test0);
        test.test("testq", 1000, test::testq);
    }

    private final MapCell[] cells;
    private final QVect[] qVects;
    private final double gridSize;
    private final RadarMap.SensorSignal signal;
    private final Complex receptiveAngle;
    private final UnaryOperator<MapCell> oper;
    private final Predicate<QVect> qFilter;

    public QRobotTest() {
        int width = 51;
        int height = 51;
        this.gridSize = 0.2;
        this.cells = new MapCell[width * height];
        this.qVects = new QVect[width * height];
        this.receptiveAngle = Complex.fromDeg(15);
        this.signal = new RadarMap.SensorSignal(
                new Point2D.Double(2, 2),
                Complex.fromDeg(45),
                0.5,
                NUM_ITER
        );
        this.oper = cell -> update(cell, signal, MAX_SIGNAL_DISTANCE, gridSize, receptiveAngle);
        this.qFilter = QIneq.circle(signal.sensorLocation(), MAX_SIGNAL_DISTANCE)
                .and(QIneq.angle(signal.sensorLocation(), signal.sensorDirection(), receptiveAngle));

        int idx = 0;
        for (int i = 0; i < height; i++) {
            double y = i * gridSize;
            for (int j = 0; j < width; j++) {
                double x = j * gridSize;
                Point2D location = new Point2D.Double(x, y);
                cells[idx] = MapCell.unknown(location);
                qVects[idx] = QVect.from(location);
                idx++;
            }
        }
    }

    void test(String name, int count, Runnable test) {
        long t0 = System.nanoTime();
        for (int i = 0; i < count; i++) {
            test.run();
        }
        long elaps = System.nanoTime() - t0;
        logger.atInfo().log("Test {} average elapsed {} ms", name, 1e-6 * elaps / count);
    }

    void test0() {
        MapCell[] newCells = Arrays.copyOf(cells, cells.length);
        IntStream.range(0, cells.length)
                .forEach(idx ->
                        newCells[idx] = oper.apply(cells[idx])
                );
    }

    void testq() {
        MapCell[] newCells = Arrays.copyOf(cells, cells.length);
        IntStream.range(0, cells.length)
                .filter(idx -> qFilter.test(qVects[idx]))
                .forEach(idx ->
                        newCells[idx] = oper.apply(cells[idx])
                );
    }

    public MapCell update(MapCell cell, RadarMap.SensorSignal signal, double maxDistance, double gridSize, Complex receptiveAngle) {
        Point2D q = signal.sensorLocation();
        Tuple2<Point2D, Point2D> interval = Geometry.squareArcInterval(cell.location(), gridSize,
                q,
                signal.sensorDirection(),
                receptiveAngle);
        if (interval == null) {
            return cell;
        }
        double distance = signal.distance();
        long t0 = signal.timestamp();
        double near = interval._1.distance(q);
        double far = interval._2.distance(q);
        return near == 0 || near > maxDistance || (near > distance && signal.isEcho())
                ? cell
                : far >= distance && signal.isEcho()
                ? cell.addEchogenic(t0)
                : cell.addAnechoic(t0);
    }
}
