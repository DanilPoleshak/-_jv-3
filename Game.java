import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class Game {

    final World world;
    final Knowledge kb;

    // Стартовая комната и текущая позиция охотника.
    final Cell start;
    Cell hunter;

    // 0 — север, 1 — восток, 2 — юг, 3 — запад.
    int direction = 0;

    boolean arrow = true;
    boolean dead = false;
    boolean won = false;
    boolean hasGold = false;

    Percept percept;

    public Game(int n, Random random) {
        world = new World(n, random);
        kb = new Knowledge(n);

        start = new Cell(n - 1, 0);
        hunter = start;

        observe(false, false);
    }

    /**
     * Партия завершена, если охотник погиб
     * или вернулся на старт с золотом.
     */
    boolean finished() {
        return dead || won;
    }

    /**
     * Получает показания датчиков и обновляет базу знаний.
     */
    void observe(boolean bump, boolean scream) {
        Percept measured = world.sense(hunter, bump, scream);

        percept = new Percept(
                measured.stench(),
                measured.breeze(),
                !hasGold && measured.glitter(),
                measured.bump(),
                measured.scream()
        );

        if (!dead) {
            kb.observe(hunter, percept, !world.wAlive);
        }
    }

    /**
     * Перемещение на одну клетку вперёд.
     */
    String forward() {
        Cell next = new Cell(
                hunter.r() + Rules.DR[direction],
                hunter.c() + Rules.DC[direction]
        );

        if (!world.inside(next)) {
            observe(true, false);
            return "Удар о стену.";
        }

        hunter = next;

        dead = world.pits.contains(hunter)
                || (world.wAlive && hunter.equals(world.wampus));

        observe(false, false);

        if (dead) {
            return "Охотник погиб в комнате " + hunter + ".";
        }

        if (hasGold && hunter.equals(start)) {
            won = true;

            return "ПОБЕДА! Охотник забрал золото "
                    + "и вернулся на старт " + start + ".";
        }

        return "Переход в " + hunter + ".";
    }

    /**
     * Поворот:
     * delta = -1 — налево;
     * delta = 1 — направо.
     */
    String turn(int delta) {
        direction = (direction + delta + 4) % 4;

        observe(false, false);

        return "Поворот: " + Rules.DIR[direction] + ".";
    }

    /**
     * Все клетки на траектории стрелы до границы поля.
     */
    List<Cell> ray(Cell from, int dir) {
        List<Cell> cells = new ArrayList<>();

        Cell current = new Cell(
                from.r() + Rules.DR[dir],
                from.c() + Rules.DC[dir]
        );

        while (world.inside(current)) {
            cells.add(current);

            current = new Cell(
                    current.r() + Rules.DR[dir],
                    current.c() + Rules.DC[dir]
            );
        }

        return cells;
    }

    /**
     * Выстрел единственной стрелой.
     * Убийство Вампуса — возможность, а не условие победы.
     */
    String shoot() {
        if (!arrow) {
            return "Стрела уже использована.";
        }

        arrow = false;

        List<Cell> trajectory = ray(hunter, direction);

        boolean hit = world.wAlive
                && trajectory.contains(world.wampus);

        if (hit) {
            world.wAlive = false;
        } else {
            kb.missedRay.addAll(trajectory);
            kb.infer();
        }

        observe(false, hit);

        return hit
                ? "Выстрел. Крик! Вампус убит."
                : "Выстрел. Промах — стрела потеряна.";
    }

    /**
     * Получение золота в текущей комнате.
     * Жив ли Вампус, для этого действия не важно.
     */
    String grab() {
        observe(false, false);

        if (hasGold) {
            return "Золото уже у охотника. Вернитесь в " + start + ".";
        }

        if (!hunter.equals(world.gold)) {
            return "В этой комнате золота нет.";
        }

        hasGold = true;

        // После получения золота датчик блеска выключается.
        observe(false, false);

        // Учитываем также случай, если золото когда-нибудь
        // будет размещено прямо на старте.
        if (hunter.equals(start)) {
            won = true;
            return "ПОБЕДА! Охотник находится на старте с золотом.";
        }

        return "Золото получено! Теперь вернитесь "
                + "в стартовую комнату " + start + ".";
    }

    /**
     * Выполняет команду пользователя или агента.
     */
    String perform(String command) {
        if (finished()) {
            return "Партия завершена.";
        }

        return switch (command) {
            case "forward" -> forward();
            case "left" -> turn(-1);
            case "right" -> turn(1);
            case "shoot" -> shoot();
            case "grab" -> grab();

            default -> throw new IllegalArgumentException(
                    "Неизвестная команда: " + command
            );
        };
    }
}