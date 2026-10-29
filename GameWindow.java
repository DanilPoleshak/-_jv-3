import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class GameWindow extends JFrame {
    private final JTextArea journal=new JTextArea(), facts=new JTextArea();
    private final JLabel status=new JLabel(), sensors=new JLabel();
    private final JSpinner size=new JSpinner(new SpinnerNumberModel(4,4,12,1));
    private final JCheckBox reveal=new JCheckBox("Показать настоящий мир");
    private final Board board=new Board();
    private final Agent agent=new Agent();
    private final Timer timer;
    private Game game;
    private int actions;

    public GameWindow() {
        super("World of Wampus • Логический агент");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(12,12)); getContentPane().setBackground(Theme.BG);
        ((JPanel)getContentPane()).setBorder(new EmptyBorder(16,16,16,16));
        timer=new Timer(500,e->autoStep());
        JPanel header=new JPanel(new FlowLayout(FlowLayout.LEFT,10,4)); header.setBackground(Theme.PANEL);
        JLabel title=new JLabel("WAMPUS / Лаборатория знаний");
        title.setFont(new Font("SansSerif",Font.BOLD,20)); title.setForeground(Theme.ACCENT);
        header.add(title); header.add(new JLabel("Размер:")); header.add(size);
        header.add(button("Новый мир", this::reset));
        reveal.setOpaque(false); reveal.setForeground(Theme.TEXT);
        reveal.addActionListener(e->board.setReveal(reveal.isSelected())); header.add(reveal);
        add(header,BorderLayout.NORTH); add(board,BorderLayout.CENTER);
        JPanel right=new JPanel(new BorderLayout(8,8)); right.setOpaque(false);
        right.setPreferredSize(new Dimension(380,600));
        configureText(facts); configureText(journal);
        JSplitPane split=new JSplitPane(JSplitPane.VERTICAL_SPLIT,new JScrollPane(facts),new JScrollPane(journal));
        split.setResizeWeight(0.60); split.setBorder(null); right.add(split); add(right,BorderLayout.EAST);
        JPanel bottom=new JPanel(); bottom.setBackground(Theme.PANEL);
        bottom.setLayout(new BoxLayout(bottom,BoxLayout.Y_AXIS));
        status.setForeground(Theme.TEXT); status.setBorder(new EmptyBorder(8,10,4,10));
        sensors.setForeground(Theme.ACCENT); sensors.setBorder(new EmptyBorder(4,10,8,10));
        bottom.add(status); bottom.add(sensors);
        JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEFT)); controls.setOpaque(false);
        controls.add(button("↶ Налево [A/Home]",()->manual("left")));
        controls.add(button("Вперёд [W/PgUp]",()->manual("forward")));
        controls.add(button("Вправо ↷ [D/End]",()->manual("right")));
        controls.add(button("Выстрел [Space]",()->manual("shoot")));
        controls.add(button("Взять золото [G]",()->manual("grab")));
        controls.add(button("Шаг агента",()->{ timer.stop(); autoStep(); }));
        controls.add(button("Авто / Пауза",()->{
            if(timer.isRunning()) timer.stop(); else if(!game.finished()) timer.start();
        })); bottom.add(controls);
        JLabel legend = new JLabel(
                "  ? — неизвестно"
                        + " • Яма? / Вампус? — предположение"
                        + " • Яма! / Вампус! — доказано"
        );
        legend.setForeground(Theme.TEXT); legend.setBorder(new EmptyBorder(4,4,10,4)); bottom.add(legend);
        add(bottom,BorderLayout.SOUTH);
        bind("PAGE_UP", "forward");
        bind("HOME", "left");
        bind("END", "right");

        bind("W", "forward");
        bind("A", "left");
        bind("D", "right");

        bind("SPACE", "shoot");
        bind("G", "grab");
        setMinimumSize(new Dimension(1080,800));
        setSize(1320,930);
        setLocationRelativeTo(null);
        reset();
    }
    private JButton button(String text,Runnable action) {
        JButton b=new JButton(text); b.setFocusPainted(false);
        b.setBackground(new Color(47,64,87)); b.setForeground(Theme.TEXT);
        b.addActionListener(e->action.run()); return b;
    }
    private void configureText(JTextArea area) {
        area.setEditable(false); area.setLineWrap(true); area.setWrapStyleWord(true);
        area.setBackground(Theme.PANEL); area.setForeground(Theme.TEXT); area.setCaretColor(Theme.TEXT);
        area.setFont(new Font("SansSerif",Font.PLAIN,14)); area.setMargin(new Insets(12,12,12,12));
    }
    private void bind(String key,String action) {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key),action);
        getRootPane().getActionMap().put(action,new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { manual(action); }
        });
    }
    private void reset() {
        timer.stop();

        actions = 0;
        agent.reset();

        int n = (Integer) size.getValue();

        game = new Game(n, new Random());

        board.setGame(game);

        journal.setText("");

        log("Создан случайный мир " + n + " × " + n + ".");
        log("Стартовая комната: " + game.start + ".");
        log("Цель: найти золото и вернуться на старт.");
        log("Убийство Вампуса не является обязательным.");
        log("На старте нет запаха, ветерка и блеска.");

        updateView();
    }
    private void log(String text) { journal.append(text+"\n\n"); journal.setCaretPosition(journal.getDocument().getLength()); }
    private void manual(String command) { timer.stop(); execute(command); }
    private void execute(String command) {
        if(game.finished()) return;
        Set<Cell> oldSafe=new HashSet<>(game.kb.safe),oldPits=new HashSet<>(game.kb.certainPit);
        Cell oldW=game.kb.knownWampus();
        log(++actions+". "+game.perform(command));
        if(!game.dead) {
            Set<Cell> added=new HashSet<>(game.kb.safe); added.removeAll(oldSafe);
            if(!added.isEmpty()) log("Вывод: безопасны "+Knowledge.sorted(added));
            added=new HashSet<>(game.kb.certainPit); added.removeAll(oldPits);
            if(!added.isEmpty()) log("Вывод: ямы в "+Knowledge.sorted(added));
            Cell w=game.kb.knownWampus();
            if(!game.kb.monsterDead && w!=null && !w.equals(oldW)) log("Вывод: Вампус точно в "+w);
        }
        if(game.finished()) timer.stop(); updateView();
    }
    private void autoStep() {
        if(game.finished()) { timer.stop(); return; }
        String command=agent.nextAction(game);
        if(command==null) {
            timer.stop(); log("Агент остановился: нет доказанно безопасного нового пути или позиции для выстрела. "
                    +"Это не доказательство отсутствия решения. Можно продолжить вручную или создать новый мир.");
        } else execute(command);
    }
    private void updateView() {
        status.setText("Комната "+game.hunter+"   |   Направление: "+Rules.DIR[game.direction]
                +"   |   Стрела: "+(game.arrow?"1":"0")+"   |   Действий: "+actions
                +"   |   "+(game.won?"ПОБЕДА":game.dead?"ПОРАЖЕНИЕ":"Исследование"));
        sensors.setText(game.percept.toString()); facts.setText(game.kb.description());
        facts.setCaretPosition(0); board.repaint();
    }
}
