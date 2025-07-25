package org.mmarini.wheelly.apis;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.Math.abs;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.QVect.from;

/**
 * The tree of area expression
 */
public interface AreaExpression {


    /**
     * Returns the intersection (and) of the given expressions
     *
     * @param children the expressions
     */
    static AreaExpression and(AreaExpression... children) {
        requireNonNull(children);
        if (children.length == 0) {
            throw new IllegalArgumentException("Missing children");
        }
        return new Tree() {
            @Override
            public AreaExpression[] children() {
                return children;
            }

            @Override
            public Predicate<boolean[]> createCellPredicate(List<Leaf> leaves) {
                Predicate<boolean[]> result = null;
                for (AreaExpression child : children) {
                    Predicate<boolean[]> pred = child.createCellPredicate(leaves);
                    result = result != null
                            ? result.and(pred)
                            : pred;
                }
                return result;
            }
        };
    }

    /**
     * Returns the angle area from point A to the given direction and angle width
     *
     * @param a         the point
     * @param direction the direction
     * @param width     the direction width
     */
    static AreaExpression angle(Point2D a, Complex direction, Complex width) {
        return and(rightHalfPlane(a, direction.sub(width)),
                rightHalfPlane(a, direction.add(width).opposite()));
    }

    /**
     * Returns the inequality predicate of circle area
     *
     * @param center the circle centre
     * @param radius the circle radius (m)
     */
    static AreaExpression circle(Point2D center, double radius) {
        double xc = center.getX();
        double yc = center.getY();
        QVect matrix = QVect.create(radius * radius - xc * xc - yc * yc,
                2 * xc, 2 * yc,
                -1, -1
        );
        return ineq(matrix);
    }

    private static IntFunction<boolean[]> createCellFunction(int[][] verticesByCell, int n, boolean[][] matrix) {
        int m = verticesByCell[0].length; // number of vertices
        return idx -> {
            int[] vertexIndices = verticesByCell[idx];
            boolean[] result = new boolean[n];
            // for each evidence
            for (int i = 0; i < n; i++) {
                boolean cellEvidence = false;
                // For each vertex
                for (int j = 0; j < m; j++) {
                    cellEvidence = matrix[vertexIndices[j]][i];
                    if (cellEvidence) {
                        break;
                    }
                }
                result[i] = cellEvidence;
            }

            return result;
        };
    }

    /**
     * Returns the quadratic vertices
     *
     * @param centre   the centre
     * @param width    the width
     * @param height   the height
     * @param gridSize the grid size (m)
     */
    static QVect[] createQVertices(Point2D centre, int width, int height, double gridSize) {
        width++;
        height++;
        QVect[] result = new QVect[width * height];
        int idx = 0;
        double x0 = centre.getX() - gridSize * (width - 1) / 2;
        double y0 = centre.getY() - gridSize * (height - 1) / 2;
        for (int i = 0; i < height; i++) {
            double y = y0 + i * gridSize;
            for (int j = 0; j < width; j++) {
                double x = x0 + j * gridSize;
                result[idx] = from(x, y);
                idx++;
            }
        }
        return result;
    }

    /**
     * Returns the vertex indices by cell (no cell x 4)
     *
     * @param width  the width
     * @param height the height
     */
    static int[][] createVerticesIndices(int width, int height) {
        int[][] result = new int[width * height][4];
        int idx = 0;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int offset = j + i * (width + 1);
                result[idx][0] = offset;
                result[idx][1] = offset + width + 1;
                result[idx][2] = offset + width + 2;
                result[idx][3] = offset + 1;
                idx++;
            }
        }
        return result;
    }

    static IntPredicate filterByArea(AreaExpression f, QVect[] vertices, int[][] verticesByCell) {
        AreaExpression.Parser parser = f.createParser();
        // Creates the matrix of quadratic inequality results by vertex
        boolean[][] matrix = parser.apply(vertices);
        // Creates the function converting the matrix to quadratic inequality results by cell
        // using the matrix of indices by cell
        int n = parser.leaves.size(); //number of evidences
        IntFunction<boolean[]> cellQIneqFunc = createCellFunction(verticesByCell, n, matrix);
        Predicate<boolean[]> cellPredicate = parser.cellPredicate();

        // Creates the predicate of area by quadratic inequality results
        return i -> cellPredicate.test(cellQIneqFunc.apply(i));
    }

    static AreaExpression.Leaf ineq(QVect params) {
        return new Leaf(params);
    }

    /**
     * Returns the negated predicate
     */
    static AreaExpression not(AreaExpression expression) {
        requireNonNull(expression);
        AreaExpression[] children = new AreaExpression[]{expression};
        return new Tree() {
            @Override
            public AreaExpression[] children() {
                return children;
            }

            @Override
            public Predicate<boolean[]> createCellPredicate(List<Leaf> leaves) {
                return children[0].createCellPredicate(leaves).negate();
            }
        };
    }

    /**
     * Returns the union (or) of the given expression
     *
     * @param children the children expressions
     */
    static AreaExpression or(AreaExpression... children) {
        requireNonNull(children);
        if (children.length == 0) {
            throw new IllegalArgumentException("Missing children");
        }
        return new Tree() {
            @Override
            public AreaExpression[] children() {
                return children;
            }

            @Override
            public Predicate<boolean[]> createCellPredicate(List<Leaf> leaves) {
                Predicate<boolean[]> result = null;
                for (AreaExpression child : children) {
                    Predicate<boolean[]> pred = child.createCellPredicate(leaves);
                    result = result != null
                            ? result.or(pred)
                            : pred;
                }
                return result;
            }
        };
    }

    /**
     * Returns the union (or) of the given expression
     *
     * @param children the children expressions
     */
    static AreaExpression or(Stream<AreaExpression> children) {
        return or(children.toArray(AreaExpression[]::new));
    }

    /**
     * Returns the rectangular area from point A to point B for the given width
     *
     * @param a     A point
     * @param b     B point
     * @param width the width (m)
     */
    static AreaExpression rectangle(Point2D a, Point2D b, double width) {
        Complex direction = Complex.direction(a, b);
        Complex left = direction.add(Complex.DEG270);
        Point2D leftPoint = new Point2D.Double(
                a.getX() + left.x() * width,
                a.getY() + left.y() * width
        );
        Point2D rightPoint = new Point2D.Double(
                a.getX() - left.x() * width,
                a.getY() - left.y() * width
        );
        return and(
                rightHalfPlane(leftPoint, direction),
                rightHalfPlane(rightPoint, direction.opposite()),
                rightHalfPlane(a, left),
                rightHalfPlane(b, left.opposite()));
    }

    /**
     * Returns the inequality predicate of right half planes for the given point to the given directions
     *
     * @param point     the point
     * @param direction the direction
     */
    static AreaExpression.Leaf rightHalfPlane(Point2D point, Complex direction) {
        QVect matrix = QVect.line(point, direction);
        return ineq(matrix);
    }

    /**
     * Returns the indices of cells intersected by segment from given extremes
     *
     * @param topology the grid topology
     * @param p0       the extreme
     * @param p1       the extreme
     */
    static IntStream segment(GridTopology topology, Point2D p0, Point2D p1) {
        if (p0.equals(p1)) {
            int idx = topology.indexOf(p0);
            return idx >= 0 ? IntStream.of(idx) : IntStream.empty();
        }
        double dx = p1.getX() - p0.getX();
        double dy = p1.getY() - p0.getY();
        int w = topology.width();
        IntStream.Builder builder = IntStream.builder();
        if (abs(dx) >= abs(dy)) {
            // Scan horizontally along x
            if (dx < 0) {
                Point2D tmp = p1;
                p1 = p0;
                p0 = tmp;
                dy = -dy;
            }
            int idx = topology.indexOf(p0);
            if (idx >= 0) {
                builder.add(idx);
            }
            int i = idx % w;
            int j = idx / w;
            double grid = topology.gridSize();
            double ox = -w * grid / 2;
            double oy = -topology.height() * grid / 2;
            double x0 = p0.getX();
            double y0 = p0.getY();
            double x1 = p1.getX();
            double y1 = p1.getY();
            double m = (y1 - y0) / (x1 - x0);
            double p = -x0 * m + y0;
            for (; ; ) {
                i++;
                // Compute x left edge
                double x = i * grid + ox;
                if (x > x1) {
                    break;
                }
                x = (i + 1) * grid + ox;
                // Compute y intersection
                double y = x * m + p;
                if (topology.contains(i, j)) {
                    builder.add(i + j * w);
                }
                if (dy >= 0) {
                    // Compute upper y edge
                    double yc = (j + 1) * grid + oy;
                    if (y >= yc) {
                        // intersect in upper cell
                        j++;
                        if (topology.contains(i, j)) {
                            builder.add(i + j * w);
                        }
                    }
                } else {
                    // Compute lower y edge
                    double yc = j * grid + oy;
                    if (y <= yc) {
                        // intersect in upper cell
                        j--;
                        if (topology.contains(i, j)) {
                            builder.add(i + j * w);
                        }
                    }
                }
            }
        } else {
            // Scan vertically along y
            if (dy < 0) {
                Point2D tmp = p1;
                p1 = p0;
                p0 = tmp;
                dx = -dx;
            }
            int idx = topology.indexOf(p0);
            if (idx >= 0) {
                builder.add(idx);
            }
            int i = idx % w;
            int j = idx / w;
            double grid = topology.gridSize();
            double ox = -w * grid / 2;
            double oy = -topology.height() * grid / 2;
            double x0 = p0.getX();
            double y0 = p0.getY();
            double x1 = p1.getX();
            double y1 = p1.getY();
            double m = (x1 - x0) / (y1 - y0);
            double p = -y0 * m + x0;
            for (; ; ) {
                j++;
                // Compute y upper edge
                double y = j * grid + oy;
                if (y > y1) {
                    break;
                }
                y = (j + 1) * grid + oy;
                // Compute x intersection
                double x = y * m + p;
                if (topology.contains(i, j)) {
                    builder.add(i + j * w);
                }
                if (dx >= 0) {
                    // Compute left x edge
                    double xc = (i + 1) * grid + ox;
                    if (x >= xc) {
                        // intersect in upper cell
                        i++;
                        if (topology.contains(i, j)) {
                            builder.add(i + j * w);
                        }
                    }
                } else {
                    // Compute right x edge
                    double xc = i * grid + ox;
                    if (x <= xc) {
                        // intersect in upper cell
                        i--;
                        if (topology.contains(i, j)) {
                            builder.add(i + j * w);
                        }
                    }
                }
            }
        }
        return builder.build();
    }

    /**
     * Returns the cell predicate for the cell evidences
     *
     * @param leaves the leave expressions
     */
    Predicate<boolean[]> createCellPredicate(List<Leaf> leaves);

    /**
     * Returns the expression parser
     */
    default Parser createParser() {
        List<Leaf> leaves = leaves();
        return new Parser(this, leaves, createCellPredicate(leaves));
    }

    /**
     * Returns the list of leaves by traversing the expression tree
     */
    default List<Leaf> leaves() {
        return leaves(new ArrayList<>());
    }

    /**
     * Returns the list of leaves by accumulating during tree traversal
     *
     * @param accumulator the leave accumulator
     */
    List<Leaf> leaves(List<Leaf> accumulator);

    /**
     * A tree area expression
     */
    interface Tree extends AreaExpression {

        /**
         * Returns the children
         */
        AreaExpression[] children();

        @Override
        default List<Leaf> leaves(List<Leaf> accumulator) {
            for (AreaExpression child : children()) {
                child.leaves(accumulator);
            }
            return accumulator;
        }
    }

    /**
     * The tree expression leaf based on quadratic inequality
     *
     * @param params the quadratic inequality parameter
     */
    record Leaf(QVect params) implements AreaExpression, Predicate<QVect> {
        @Override
        public Predicate<boolean[]> createCellPredicate(List<Leaf> leaves) {
            // get the index of leaf
            int idx = leaves.indexOf(this);
            return cellEvidences -> cellEvidences[idx];
        }

        @Override
        public List<Leaf> leaves(List<Leaf> accumulator) {
            accumulator.add(this);
            return accumulator;
        }

        @Override
        public boolean test(QVect qVect) {
            return qVect.mmult(params) >= 0;
        }
    }

    /**
     * Parses the expression to build the binary array of ordered the leaves expression (# vertices, # leaves)
     * and the function of cell containment for the (n) quadratic inequalities
     *
     * @param expression    the cell containment expression
     * @param leaves        the leaves expression (vertex quadratic inequalities)
     * @param cellPredicate the function returning true if the expression matches the result of expression leaves
     */
    record Parser(AreaExpression expression, List<Leaf> leaves, Predicate<boolean[]> cellPredicate) {

        /**
         * Returns the binary array of ordered the leaves expression (# vertices, # leaves)
         *
         * @param vectors the quadratic vector of each node (# vertices)
         */
        boolean[][] apply(QVect... vectors) {
            return Arrays.stream(vectors).
                    map(this::applyPoint)
                    .toArray(boolean[][]::new);
        }

        /**
         * Returns the binary array of ordered the leaves expression (# leaves)
         *
         * @param point the vector
         */
        boolean[] applyPoint(QVect point) {
            boolean[] result = new boolean[leaves.size()];
            for (int i = 0; i < leaves.size(); i++) {
                result[i] = leaves.get(i).test(point);
            }
            return result;
        }

        /**
         * Returns true id the vector satisfies the expression
         *
         * @param point the point
         */
        boolean test(QVect point) {
            return cellPredicate.test(applyPoint(point));
        }

        /**
         * Returns true id the vector satisfies the expression
         *
         * @param point the point
         */
        boolean test(Point2D point) {
            return test(from(point));
        }
    }
}
