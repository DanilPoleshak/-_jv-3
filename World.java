import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class World {

    private static final int MAX_PLACEMENT_ATTEMPTS = 500;

    final int n;

    final Set<Cell> pits = new HashSet<>();

    Cell wampus;
    Cell gold;

    boolean wAlive = true;

    public World(int n, Random random) {
        if (n < 4 || n > 12) {
            throw new IllegalArgumentException(
                    "Размер поля должен быть от 4 до 12."
            );
        }

        this.n = n;

        Cell start = new Cell(n - 1, 0);

        /*
         * Стартовая клетка и её соседи защищены:
         * на старте не будет опасностей и сигналов датчиков.
         */
        Set<Cell> protectedCells = new HashSet<>(
                neighbors(start)
        );

        protectedCells.add(start);

        placeWampusAndGold(random, protectedCells);
        placePits(random, protectedCells);
    }

    /**
     * Размещает Вампуса и золото в соседних по стороне клетках.
     */
    private void placeWampusAndGold(
            Random random,
            Set<Cell> protectedCells
    ) {
        List<Cell> wampusOptions = all();

        wampusOptions.removeAll(protectedCells);
        Collections.shuffle(wampusOptions, random);

        for (Cell candidate : wampusOptions) {
            List<Cell> goldOptions = neighbors(candidate);

            goldOptions.removeAll(protectedCells);

            if (goldOptions.isEmpty()) {
                continue;
            }

            wampus = candidate;

            gold = goldOptions.get(
                    random.nextInt(goldOptions.size())
            );

            return;
        }

        throw new IllegalStateException(
                "Не удалось разместить Вампуса и золото."
        );
    }

    /**
     * Размещает заданное количество ям.
     *
     * Ямы не могут соседствовать по стороне.
     * Диагональное соседство разрешено.
     */
    private void placePits(
            Random random,
            Set<Cell> protectedCells
    ) {
        int requiredCount = (int) Math.round(
                n * n * Rules.PIT_DENSITY
        );

        List<Cell> available = all();

        available.removeAll(protectedCells);
        available.remove(wampus);
        available.remove(gold);

        if (requiredCount > available.size()) {
            throw new IllegalStateException(
                    "Недостаточно свободных клеток для ям."
            );
        }

        /*
         * Один случайный порядок может привести к тому,
         * что ранние ямы перекроют слишком много клеток.
         *
         * Поэтому при необходимости пробуем новый порядок.
         */
        for (int attempt = 0;
             attempt < MAX_PLACEMENT_ATTEMPTS;
             attempt++) {

            pits.clear();

            Collections.shuffle(available, random);

            for (Cell candidate : available) {
                if (pits.size() >= requiredCount) {
                    break;
                }

                if (canPlacePit(candidate)) {
                    pits.add(candidate);
                }
            }

            if (pits.size() == requiredCount) {
                return;
            }
        }

        pits.clear();

        throw new IllegalStateException(
                "Не удалось разместить "
                        + requiredCount
                        + " ям без соседства по стороне. "
                        + "Уменьшите плотность ям."
        );
    }

    /**
     * Проверяет отсутствие других ям
     * сверху, снизу, слева и справа.
     *
     * Диагонали намеренно не проверяются.
     */
    private boolean canPlacePit(Cell candidate) {
        for (Cell neighbor : neighbors(candidate)) {
            if (pits.contains(neighbor)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Возвращает все клетки поля.
     */
    List<Cell> all() {
        List<Cell> cells = new ArrayList<>();

        for (int row = 0; row < n; row++) {
            for (int column = 0; column < n; column++) {
                cells.add(
                        new Cell(row, column)
                );
            }
        }

        return cells;
    }

    /**
     * Проверяет нахождение клетки внутри поля.
     */
    boolean inside(Cell cell) {
        return cell.r() >= 0
                && cell.c() >= 0
                && cell.r() < n
                && cell.c() < n;
    }

    /**
     * Соседи по стороне.
     * Диагональные клетки не включаются.
     */
    List<Cell> neighbors(Cell cell) {
        List<Cell> result = new ArrayList<>();

        for (int direction = 0;
             direction < 4;
             direction++) {

            Cell neighbor = new Cell(
                    cell.r() + Rules.DR[direction],
                    cell.c() + Rules.DC[direction]
            );

            if (inside(neighbor)) {
                result.add(neighbor);
            }
        }

        return result;
    }

    /**
     * Формирует показания датчиков.
     */
    Percept sense(
            Cell cell,
            boolean bump,
            boolean scream
    ) {
        List<Cell> near = neighbors(cell);

        boolean stench = wAlive
                && near.contains(wampus);

        boolean breeze = near.stream()
                .anyMatch(pits::contains);

        boolean glitter = cell.equals(gold)
                || near.contains(gold);

        return new Percept(
                stench,
                breeze,
                glitter,
                bump,
                scream
        );
    }
}