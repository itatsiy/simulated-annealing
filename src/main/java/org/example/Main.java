package org.example;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class Main {
    private static final Random RANDOM = new Random();
    private static final int MARGIN = 20;
    private static final int WIDTH = 900;
    private static final int HEIGHT = 500;
    private static final int N = 40;
    private static final ExecutorService CACHED_EXECUTOR_SERVICE = Executors.newCachedThreadPool();

    private static void generateGui() {
        AtomicReference<IntVector2D> priority = new AtomicReference<>();
        var points = new ArrayList<IntVector2D>();
        var path = new ArrayList<IntVector2D>();
        var generateButton = new JButton("Generate");
        var resolveButton = new JButton("Resolve");

        var buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));
        buttonPanel.add(generateButton);
        buttonPanel.add(Box.createHorizontalStrut(5));
        buttonPanel.add(resolveButton);

        var bottomPanel = new JPanel();
        bottomPanel.add(buttonPanel);

        var panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                var size = getSize();
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, (int) size.getWidth(), (int) size.getHeight());
                g.setColor(Color.GREEN);
                IntVector2D previous = null;
                for (var current : new ArrayList<>(path)) {
                    if (previous != null) {
                        g.drawLine(previous.x, previous.y, current.x, current.y);
                    }
                    previous = current;
                }
                g.setColor(Color.RED);
                for (var p : points) {
                    if (priority.get().equals(p)) {
                        g.setColor(Color.BLUE);
                    }
                    g.fillRect(p.x - 2, p.y - 2, 5, 5);
                    if (priority.get().equals(p)) {
                        g.setColor(Color.RED);
                    }
                }
            }
        };
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panel.setLayout(new BorderLayout());
        panel.add(bottomPanel, BorderLayout.SOUTH);

        var frame = new JFrame("Simulated annealing");
        frame.setMinimumSize(new Dimension(WIDTH, HEIGHT + 100));
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.add(panel);
        frame.pack();
        frame.setVisible(true);
        generateButton.addActionListener(e -> {
            path.clear();
            points.clear();
            for (var i = 0; i < N; i++) {
                points.add(new IntVector2D(MARGIN + RANDOM.nextInt(WIDTH - MARGIN * 2), MARGIN + RANDOM.nextInt(HEIGHT - MARGIN * 2)));
            }
            priority.set(points.get(RANDOM.nextInt(N)));
            frame.repaint();
        });
        resolveButton.addActionListener(e ->
                CACHED_EXECUTOR_SERVICE.submit(() ->
                        route(points, priority, x -> {
                            path.clear();
                            path.addAll(x);
                            frame.repaint();
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException ex) {
                                throw new RuntimeException(ex);
                            }
                        })
                )
        );
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::generateGui);
    }

    public static void route(List<IntVector2D> points, AtomicReference<IntVector2D> priority, Consumer<List<IntVector2D>> onProgress) {
        var start = System.currentTimeMillis();
        var current = new ArrayList<>(points);
        Collections.shuffle(current); // Начальное состояние
        var tCurrent = calculateTemperature(current, priority);
        var best = new ArrayList<>(current);
        var tBest = tCurrent;
        var t = tCurrent;
        while (t > 1) {
            var permutation = mutate(current);
            var tCandidate = calculateTemperature(current, priority);
            var delta = tCandidate - tCurrent;
            if (delta < 0) {
                tCurrent = tCandidate;
                if (tCurrent < tBest) {
                    best = new ArrayList<>(current);
                    tBest = tCurrent;
                    onProgress.accept(best); // Найдено более оптимальное решение
                }
            } else {
                if (calcTransitionProbability(delta, t) > RANDOM.nextDouble()) {
                    tCurrent = tCandidate; // Приняли менее оптимальное решение, как текущее, в нажежде, что оно приведет к более оптимальному
                } else {
                    rollback(current, permutation);
                }
            }
            t *= 0.99999;
        }
        System.out.println((System.currentTimeMillis() - start) / 1000d + " secs, tmp: " + tBest);
    }

    public static Permutation mutate(List<IntVector2D> path) {
        int i = RANDOM.nextInt(path.size());
        int j;
        do {
            j = RANDOM.nextInt(path.size());
        } while (i == j);
        Collections.swap(path, i, j);
        return new Permutation(i, j);
    }

    public static void rollback(List<IntVector2D> path, Permutation permutation) {
        Collections.swap(path, permutation.i(), permutation.j());
    }

    private static double calcTransitionProbability(Double delta, Double t0) {
        return Math.exp(-delta / t0);
    }

    private static double calculateTemperature(List<IntVector2D> state, AtomicReference<IntVector2D> priority) {
        var previous = state.get(0);
        var temperature = priority.get().equals(previous) ? 0 : (WIDTH + HEIGHT);
        for (var i = 1; i < state.size(); i++) {
            var current = state.get(i);
            temperature += current.distance(previous);
            previous = current;
        }
        return temperature;
    }

    private record Permutation(int i, int j) {
    }

    private record IntVector2D(int x, int y) {
        public double distance(IntVector2D other) {
            return Math.sqrt((other.y - y) * (other.y - y) + (other.x - x) * (other.x - x));
        }
    }
}