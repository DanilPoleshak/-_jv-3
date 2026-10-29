import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Board extends JPanel {

    private Game game;
    private boolean reveal;

    public Board() {
        setBackground(Theme.BG);
        setPreferredSize(new Dimension(650, 650));
    }

    public void setGame(Game game) {
        this.game = game;
        repaint();
    }

    public void setReveal(boolean reveal) {
        this.reveal = reveal;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        if (game == null) {
            return;
        }

        Graphics2D g = (Graphics2D) graphics.create();

        try {
            g.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            g.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON
            );

            int n = game.world.n;

            int cellSize = Math.min(
                    (getWidth() - 28) / n,
                    (getHeight() - 28) / n
            );

            if (cellSize < 16) {
                return;
            }

            int offsetX = (getWidth() - cellSize * n) / 2;
            int offsetY = (getHeight() - cellSize * n) / 2;

            for (int row = 0; row < n; row++) {
                for (int column = 0; column < n; column++) {
                    Cell cell = new Cell(row, column);

                    int x = offsetX + column * cellSize;
                    int y = offsetY + row * cellSize;

                    drawCell(g, cell, x, y, cellSize);
                }
            }
        } finally {
            g.dispose();
        }
    }

    private void drawCell(
            Graphics2D g,
            Cell cell,
            int x,
            int y,
            int size
    ) {
        Knowledge kb = game.kb;

        boolean visited = kb.observations.containsKey(cell);
        boolean safe = kb.safe.contains(cell);

        Color background = Theme.PANEL;

        if (safe) {
            background = new Color(29, 76, 69);
        }

        if (visited) {
            background = new Color(45, 72, 87);
        }

        if (kb.certainPit.contains(cell)) {
            background = new Color(109, 48, 63);
        }

        g.setColor(background);

        g.fillRoundRect(
                x + 3,
                y + 3,
                size - 6,
                size - 6,
                12,
                12
        );

        // Выделение стартовой комнаты.
        if (cell.equals(game.start)) {
            g.setColor(Theme.ACCENT);

            g.drawRoundRect(
                    x + 4,
                    y + 4,
                    size - 8,
                    size - 8,
                    12,
                    12
            );
        }

        String mark = "?";
        Color textColor = Theme.TEXT;

        if (safe) {
            mark = "Безопасно";
            textColor = Theme.ACCENT;
        }

        if (kb.possiblePit.contains(cell)) {
            mark = "Яма?";
            textColor = new Color(255, 193, 110);
        }

        if (kb.certainPit.contains(cell)) {
            mark = "Яма!";
            textColor = new Color(255, 135, 150);
        }

        if (!kb.monsterDead
                && kb.wCandidates.contains(cell)
                && (kb.wCandidates.size() == 1 || nearVisited(cell))) {

            String wampusMark = kb.wCandidates.size() == 1
                    ? "Вампус!"
                    : "Вампус?";

            mark = mark.equals("?")
                    ? wampusMark
                    : mark + " / " + wampusMark;
        }

        List<String> signals = new ArrayList<>();

        if (visited) {
            Percept percept = kb.observations.get(cell);

            if (percept.stench()) {
                signals.add("Запах");
            }

            if (percept.breeze()) {
                signals.add("Ветерок");
            }

            if (!game.hasGold && percept.glitter()) {
                signals.add("Блеск");
            }
        }

        // Настоящее содержимое комнаты.
        if (reveal) {
            if (game.world.pits.contains(cell)) {
                mark = "Яма";
                textColor = new Color(255, 130, 150);

            } else if (cell.equals(game.world.wampus)) {
                mark = game.world.wAlive
                        ? "Вампус"
                        : "Вампус убит";

                textColor = new Color(255, 163, 96);

            } else if (!game.hasGold && cell.equals(game.world.gold)) {
                mark = "Золото";
                textColor = new Color(255, 216, 100);

            } else {
                mark = "Пусто";
                textColor = Theme.TEXT;
            }
        }

        if (cell.equals(game.hunter)) {
            String direction = new String[]{
                    "↑", "→", "↓", "←"
            }[game.direction];

            mark = "Охотник " + direction;
            textColor = Theme.ACCENT;
        }

        /*
         * Разделяем внутреннюю область клетки на три зоны:
         * координаты, основная надпись, сигналы датчиков.
         */
        int padding = Math.max(5, size / 12);

        int innerX = x + padding;
        int innerY = y + padding;

        int innerWidth = size - padding * 2;
        int innerHeight = size - padding * 2;

        int topHeight = innerHeight / 4;
        int middleHeight = innerHeight / 2;
        int bottomHeight = innerHeight - topHeight - middleHeight;

        // Координаты.
        drawFittedText(
                g,
                cell.toString(),
                new Rectangle(
                        innerX,
                        innerY,
                        innerWidth,
                        topHeight
                ),
                Font.PLAIN,
                Math.min(14f, size * 0.16f),
                new Color(155, 175, 198)
        );

        // Основная надпись.
        drawFittedText(
                g,
                mark,
                new Rectangle(
                        innerX,
                        innerY + topHeight,
                        innerWidth,
                        middleHeight
                ),
                Font.BOLD,
                Math.min(28f, size * 0.25f),
                textColor
        );

        // Показания датчиков.
        drawFittedText(
                g,
                String.join(" · ", signals),
                new Rectangle(
                        innerX,
                        innerY + topHeight + middleHeight,
                        innerWidth,
                        bottomHeight
                ),
                Font.PLAIN,
                Math.min(14f, size * 0.15f),
                new Color(255, 211, 135)
        );
    }

    /**
     * Уменьшает шрифт, пока текст не поместится
     * по ширине и высоте в выделенную область.
     */
    private void drawFittedText(
            Graphics2D graphics,
            String text,
            Rectangle area,
            int style,
            float preferredSize,
            Color color
    ) {
        if (text == null
                || text.isEmpty()
                || area.width <= 0
                || area.height <= 0) {
            return;
        }

        Graphics2D g = (Graphics2D) graphics.create();

        try {
            // Текст не может выйти за свою область.
            g.clip(area);

            Font baseFont = new Font(
                    "SansSerif",
                    style,
                    12
            );

            float fontSize = Math.max(1f, preferredSize);

            Font font;
            FontMetrics metrics;

            while (true) {
                font = baseFont.deriveFont(fontSize);
                metrics = g.getFontMetrics(font);

                boolean fitsWidth =
                        metrics.stringWidth(text) <= area.width;

                boolean fitsHeight =
                        metrics.getHeight() <= area.height;

                if (fitsWidth && fitsHeight) {
                    break;
                }

                // Если даже минимальный шрифт не помещается,
                // пропускаем надпись, а не рисуем её поверх соседей.
                if (fontSize <= 1f) {
                    return;
                }

                fontSize = Math.max(1f, fontSize - 0.5f);
            }

            g.setFont(font);
            g.setColor(color);

            int textX = area.x
                    + (area.width - metrics.stringWidth(text)) / 2;

            int textY = area.y
                    + (area.height - metrics.getHeight()) / 2
                    + metrics.getAscent();

            g.drawString(text, textX, textY);

        } finally {
            g.dispose();
        }
    }

    private boolean nearVisited(Cell cell) {
        return game.kb.neighbors(cell)
                .stream()
                .anyMatch(game.kb.observations::containsKey);
    }
}