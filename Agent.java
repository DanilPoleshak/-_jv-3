import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Автоматический агент.
 *
 * Цель:
 * 1. Найти и забрать золото.
 * 2. Вернуться на старт.
 *
 * Убийство Вампуса не обязательно.
 *
 * При отсутствии безопасных ходов агент оценивает
 * подозрительные клетки и выбирает наименее рискованную.
 *
 * Оценка риска — эвристический балл, а не точная вероятность.
 */
public class Agent {

    private static final double EPSILON = 0.000001;

    // Вес возможного присутствия Вампуса в оценке риска.
    private static final double WAMPUS_WEIGHT = 2.0;

    private final Random random = new Random();

    // Клетки, в которых уже пытались взять золото.
    private final Set<Cell> attemptedGrab = new HashSet<>();

    // Выбранная рискованная цель.
    // Сохраняется на время подхода и поворотов.
    private Cell riskTarget;

    /**
     * Сброс памяти агента перед новой партией.
     */
    public void reset() {
        attemptedGrab.clear();
        riskTarget = null;
    }

    /**
     * Выбирает одну следующую команду.
     *
     * Возможные команды:
     * forward, left, right, shoot, grab.
     *
     * null — подходящее действие не найдено
     * или партия завершена.
     */
    public String nextAction(Game game) {
        if (game.finished()) {
            return null;
        }

        Knowledge kb = game.kb;

        /*
         * 1. Золото уже получено:
         * прекращаем исследование и возвращаемся на старт.
         */
        if (game.hasGold) {
            riskTarget = null;

            List<Cell> homeRoute = routeTo(
                    game,
                    cell -> cell.equals(game.start)
            );

            if (homeRoute != null && homeRoute.size() > 1) {
                return goToward(game, homeRoute.get(1));
            }

            return null;
        }

        /*
         * 2. Если ощущаем блеск, пробуем взять золото.
         *
         * Блеск может ощущаться рядом с золотом,
         * поэтому попытка не всегда будет успешной.
         */
        if (game.percept.glitter()
                && attemptedGrab.add(game.hunter)) {

            return "grab";
        }

        /*
         * 3. Возвращаемся в известные комнаты с блеском,
         * в которых ещё не пытались взять золото.
         */
        List<Cell> goldRoute = routeTo(
                game,
                cell -> kb.observations.containsKey(cell)
                        && kb.observations.get(cell).glitter()
                        && !attemptedGrab.contains(cell)
        );

        if (goldRoute != null && goldRoute.size() > 1) {
            riskTarget = null;

            return goToward(game, goldRoute.get(1));
        }

        /*
         * 4. Исследуем доказанно безопасные
         * непосещённые клетки.
         */
        List<Cell> explorationRoute = routeTo(
                game,
                cell -> !kb.observations.containsKey(cell)
        );

        if (explorationRoute != null
                && explorationRoute.size() > 1) {

            riskTarget = null;

            return goToward(
                    game,
                    explorationRoute.get(1)
            );
        }

        /*
         * 5. Безопасные новые клетки закончились.
         * Если Вампус установлен точно, можно устранить его.
         */
        Cell wampus = kb.knownWampus();

        if (!kb.monsterDead
                && game.arrow
                && wampus != null) {

            List<Cell> shootingRoute = routeTo(
                    game,
                    cell -> cell.r() == wampus.r()
                            || cell.c() == wampus.c()
            );

            if (shootingRoute != null) {
                riskTarget = null;

                // Идём к безопасной позиции для выстрела.
                if (shootingRoute.size() > 1) {
                    return goToward(
                            game,
                            shootingRoute.get(1)
                    );
                }

                // Уже стоим на одной линии с Вампусом.
                int targetDirection = directionTo(
                        game.hunter,
                        wampus
                );

                if (game.direction == targetDirection) {
                    return "shoot";
                }

                return face(
                        game.direction,
                        targetDirection
                );
            }
        }

        /*
         * 6. Безопасного продолжения нет:
         * выбираем подозрительную клетку по оценке риска.
         */
        return chooseRiskAction(game);
    }

    /**
     * Выбирает рискованную цель и направляется к ней.
     *
     * Весь подход проходит по безопасным клеткам.
     * Рискованным является только последний шаг.
     */
    private String chooseRiskAction(Game game) {

        // Проверяем ранее выбранную цель.
        if (riskTarget != null) {
            boolean visited = game.kb.observations
                    .containsKey(riskTarget);

            boolean nowSafe = game.kb.safe
                    .contains(riskTarget);

            if (visited
                    || nowSafe
                    || isKnownDanger(game, riskTarget)) {

                riskTarget = null;
            }
        }

        /*
         * Сохраняем прежнее решение на время подхода.
         * Это предотвращает смену цели после каждого поворота.
         */
        if (riskTarget != null) {
            String action = actionTowardRisk(
                    game,
                    riskTarget
            );

            if (action != null) {
                return action;
            }

            riskTarget = null;
        }

        List<Cell> candidates = findRiskCandidates(game);

        if (candidates.isEmpty()) {
            return null;
        }

        riskTarget = chooseRationalTarget(
                game,
                candidates
        );

        if (riskTarget == null) {
            return null;
        }

        return actionTowardRisk(game, riskTarget);
    }

    /**
     * Находит подозрительные клетки,
     * к которым существует безопасный подход.
     */
    private List<Cell> findRiskCandidates(Game game) {
        List<Cell> candidates = new ArrayList<>();

        for (int row = 0; row < game.kb.n; row++) {
            for (int column = 0;
                 column < game.kb.n;
                 column++) {

                Cell cell = new Cell(row, column);

                if (game.kb.observations.containsKey(cell)) {
                    continue;
                }

                if (game.kb.safe.contains(cell)) {
                    continue;
                }

                if (isKnownDanger(game, cell)) {
                    continue;
                }

                List<Cell> approach = routeTo(
                        game,
                        position -> game.kb
                                .neighbors(position)
                                .contains(cell)
                );

                if (approach != null) {
                    candidates.add(cell);
                }
            }
        }

        return candidates;
    }

    /**
     * Сравнивает кандидатов:
     * 1. Меньше риск.
     * 2. При равном риске — короче подход.
     * 3. При полном равенстве — случайный выбор.
     */
    private Cell chooseRationalTarget(
            Game game,
            List<Cell> candidates
    ) {
        double bestRisk = Double.POSITIVE_INFINITY;
        int bestDistance = Integer.MAX_VALUE;

        List<Cell> bestCandidates = new ArrayList<>();

        for (Cell candidate : candidates) {
            double risk = riskScore(game, candidate);

            if (!Double.isFinite(risk)) {
                continue;
            }

            List<Cell> approach = routeTo(
                    game,
                    position -> game.kb
                            .neighbors(position)
                            .contains(candidate)
            );

            if (approach == null) {
                continue;
            }

            int distance = approach.size() - 1;

            boolean lowerRisk =
                    risk < bestRisk - EPSILON;

            boolean equalRisk =
                    Math.abs(risk - bestRisk) <= EPSILON;

            if (lowerRisk
                    || (equalRisk && distance < bestDistance)) {

                bestRisk = risk;
                bestDistance = distance;

                bestCandidates.clear();
                bestCandidates.add(candidate);

            } else if (equalRisk
                    && distance == bestDistance) {

                bestCandidates.add(candidate);
            }
        }

        if (bestCandidates.isEmpty()) {
            return null;
        }

        return bestCandidates.get(
                random.nextInt(bestCandidates.size())
        );
    }

    /**
     * Оценка опасности клетки.
     *
     * Чем меньше балл, тем предпочтительнее клетка.
     * Это не вероятность и не процент гибели.
     */
    private double riskScore(Game game, Cell cell) {
        Knowledge kb = game.kb;

        if (isKnownDanger(game, cell)) {
            return Double.POSITIVE_INFINITY;
        }

        double pitRisk = 0.0;

        /*
         * Если отсутствие ямы доказано,
         * штраф за яму не начисляется.
         */
        if (!kb.noPit.contains(cell)) {
            pitRisk = Rules.PIT_DENSITY;

            /*
             * Добавляем подозрение от каждого
             * логического предложения о ямах.
             *
             * Меньше кандидатов в предложении —
             * сильнее подозрение к каждому из них.
             */
            for (Set<Cell> clause : kb.clauses) {
                if (!clause.isEmpty()
                        && clause.contains(cell)) {

                    pitRisk += 1.0 / clause.size();
                }
            }
        }

        double wampusRisk = 0.0;

        if (!kb.monsterDead
                && kb.wCandidates.contains(cell)
                && !kb.wCandidates.isEmpty()) {

            wampusRisk = WAMPUS_WEIGHT
                    / kb.wCandidates.size();
        }

        return pitRisk + wampusRisk;
    }

    /**
     * Проверяет доказанную опасность.
     *
     * Агент не будет намеренно заходить в известную яму
     * или к точно установленному живому Вампусу.
     */
    private boolean isKnownDanger(
            Game game,
            Cell cell
    ) {
        if (game.kb.certainPit.contains(cell)) {
            return true;
        }

        Cell knownWampus = game.kb.knownWampus();

        return !game.kb.monsterDead
                && cell.equals(knownWampus);
    }

    /**
     * Сначала подходит к рискованной цели
     * по безопасным клеткам, затем входит в неё.
     */
    private String actionTowardRisk(
            Game game,
            Cell target
    ) {
        List<Cell> approach = routeTo(
                game,
                position -> game.kb
                        .neighbors(position)
                        .contains(target)
        );

        if (approach == null) {
            return null;
        }

        if (approach.size() > 1) {
            return goToward(
                    game,
                    approach.get(1)
            );
        }

        // Охотник уже находится рядом с целью.
        return goToward(game, target);
    }

    /**
     * Поиск маршрута в ширину.
     *
     * Маршрут проходит только через доказанно безопасные
     * клетки и начинается с текущей позиции охотника.
     */
    private List<Cell> routeTo(
            Game game,
            Predicate<Cell> goal
    ) {
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        Map<Cell, Cell> previous = new HashMap<>();

        queue.add(game.hunter);
        previous.put(game.hunter, null);

        while (!queue.isEmpty()) {
            Cell current = queue.remove();

            if (goal.test(current)) {
                List<Cell> path = new ArrayList<>();

                for (Cell cell = current;
                     cell != null;
                     cell = previous.get(cell)) {

                    path.add(cell);
                }

                Collections.reverse(path);

                return path;
            }

            for (Cell neighbor :
                    game.kb.neighbors(current)) {

                boolean safe = game.kb.safe
                        .contains(neighbor);

                boolean discovered = previous
                        .containsKey(neighbor);

                if (safe && !discovered) {
                    previous.put(neighbor, current);
                    queue.add(neighbor);
                }
            }
        }

        return null;
    }

    /**
     * Определяет направление к соседней клетке
     * или клетке на одной строке/столбце.
     */
    private int directionTo(
            Cell from,
            Cell to
    ) {
        if (to.r() < from.r()) {
            return 0; // Север.
        }

        if (to.c() > from.c()) {
            return 1; // Восток.
        }

        if (to.r() > from.r()) {
            return 2; // Юг.
        }

        return 3; // Запад.
    }

    /**
     * Выбирает действие для движения к соседней клетке:
     * поворот или шаг вперёд.
     */
    private String goToward(
            Game game,
            Cell next
    ) {
        int targetDirection = directionTo(
                game.hunter,
                next
        );

        if (game.direction == targetDirection) {
            return "forward";
        }

        return face(
                game.direction,
                targetDirection
        );
    }

    /**
     * Выбирает поворот на 90 градусов.
     * Разворот на 180 градусов требует двух действий.
     */
    private String face(
            int current,
            int target
    ) {
        int difference = (target - current + 4) % 4;

        return difference == 3
                ? "left"
                : "right";
    }
}