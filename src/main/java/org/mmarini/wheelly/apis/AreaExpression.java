package org.mmarini.wheelly.apis;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

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
     * @param center the circle center
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
     * Returns the expression parser
     *
     * @param exp the expression
     */
    static Parser createParser(AreaExpression exp) {
        List<Leaf> leaves = exp.leaves(new ArrayList<>());
        return new Parser(exp, leaves);
    }

    /**
     * Returns the quadratic vertices
     *
     * @param topology the grid topology
     */
    static QVect[] createQVertices(GridTopology topology) {
        int w = topology.width() + 1;
        int h = topology.height() + 1;
        double gridSize = topology.gridSize();
        QVect[] result = new QVect[w * h];
        int idx = 0;
        double x0 = topology.center().getX() - gridSize * (w - 1) / 2;
        double y0 = topology.center().getY() - gridSize * (h - 1) / 2;
        for (int i = 0; i < h; i++) {
            double y = y0 + i * gridSize;
            for (int j = 0; j < w; j++) {
                double x = x0 + j * gridSize;
                result[idx] = QVect.from(x, y);
                idx++;
            }
        }
        return result;
    }

    /**
     * Returns the vertex indices by cell (no cell x 4)
     *
     * @param topology the grid topology
     */
    static int[][] createVerticesIndices(GridTopology topology) {
        int w = topology.width();
        int h = topology.height();
        int[][] result = new int[w * h][4];
        int idx = 0;
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                int offset = j + i * (w + 1);
                result[idx][0] = offset;
                result[idx][1] = offset + w + 1;
                result[idx][2] = offset + w + 2;
                result[idx][3] = offset + 1;
                idx++;
            }
        }
        return result;
    }

    static IntPredicate filterByArea(AreaExpression f, QVect[] vertices, int[][] verticesByCell) {
        AreaExpression.Parser parser = AreaExpression.createParser(f);
        // Creates the matrix of quadratic inequality results by vertex
        boolean[][] matrix = parser.apply(vertices);
        // Creates the function converting the matrix to quadratic inequality results by cell
        // using the martix of indices by cell
        int n = parser.leaves.size(); //number of evidences
        IntFunction<boolean[]> cellQIneqFunc = createCellFunction(verticesByCell, n, matrix);
        Predicate<boolean[]> cellPredicate = parser.createCellPredicate();

        // Creates the predicate of area by quadratic inequality results
        return i -> cellPredicate.test(cellQIneqFunc.apply(i));
    }

    static AreaExpression.Leaf ineq(QVect params) {
        return new Leaf(params);
    }

    /**
     * Returns the negate predicate
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
     * Returns the cell predicate for the cell evidences
     *
     * @param leaves the leaves expressions
     */
    Predicate<boolean[]> createCellPredicate(List<Leaf> leaves);

    /**
     * Returns the list of leaves by accumulating during tree traversal
     *
     * @param accumulator the leaves accumulator
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
     * Parses the expression to build binary array of ordered the leaves expression (# vertices, # leaves)
     * and the function of cell containment for the (n) quadratic inequalities
     *
     * @param expression the cell containment expression
     * @param leaves     the leaves expression (vertex quadratic inequalities)
     */
    record Parser(AreaExpression expression, List<Leaf> leaves) {

        /**
         * Returns the binary array of ordered the leaves expression (# vertices, # leaves)
         *
         * @param vectors the quadratic vector of each node (# vertices)
         */
        boolean[][] apply(QVect... vectors) {
            return Arrays.stream(vectors).
                    map(point -> {
                        boolean[] result = new boolean[leaves.size()];
                        for (int i = 0; i < leaves.size(); i++) {
                            result[i] = leaves.get(i).test(point);
                        }
                        return result;
                    }).toArray(boolean[][]::new);
        }

        /**
         * Returns function that is true if expression is true for the (n) quadratic inequality evidences
         */
        public Predicate<boolean[]> createCellPredicate() {
            return expression.createCellPredicate(leaves);
        }
    }
}
