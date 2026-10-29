import java.util.*;

/** Наблюдения и выводы. Доступа к скрытому World нет. */
public class Knowledge {
    final int n;
    final Map<Cell,Percept> observations = new LinkedHashMap<>();
    final Set<Cell> noPit = new HashSet<>(), certainPit = new HashSet<>();
    final Set<Cell> wCandidates = new HashSet<>(), missedRay = new HashSet<>();
    final Set<Cell> safe = new HashSet<>(), possiblePit = new HashSet<>();
    final List<Set<Cell>> clauses = new ArrayList<>();
    boolean monsterDead;
    public Knowledge(int n) {
        this.n=n;
        for(int r=0;r<n;r++) for(int c=0;c<n;c++) wCandidates.add(new Cell(r,c));
    }
    List<Cell> neighbors(Cell p) {
        List<Cell> out = new ArrayList<>();
        for(int d=0;d<4;d++) {
            Cell q = new Cell(p.r()+Rules.DR[d],p.c()+Rules.DC[d]);
            if(q.r()>=0 && q.c()>=0 && q.r()<n && q.c()<n) out.add(q);
        }
        return out;
    }
    void observe(Cell p, Percept v, boolean dead) {
        observations.put(p,v); noPit.add(p);
        if(!dead) wCandidates.remove(p);
        if(!v.breeze()) noPit.addAll(neighbors(p));
        if(!dead) {
            if(v.stench()) wCandidates.retainAll(neighbors(p));
            else wCandidates.removeAll(neighbors(p));
        }
        monsterDead=dead; infer();
    }
    void infer() {
        wCandidates.removeAll(missedRay);
        clauses.clear(); certainPit.clear(); possiblePit.clear(); safe.clear();
        for(var entry : observations.entrySet()) if(entry.getValue().breeze()) {
            Set<Cell> options = new LinkedHashSet<>(neighbors(entry.getKey()));
            options.removeAll(noPit); clauses.add(options); possiblePit.addAll(options);
            if(options.size()==1) certainPit.addAll(options);
        }
        if(!monsterDead) wCandidates.removeAll(certainPit);
        for(Cell p : noPit) if(monsterDead || !wCandidates.contains(p)) safe.add(p);
    }
    Cell knownWampus() { return wCandidates.size()==1 ? wCandidates.iterator().next() : null; }
    static String sorted(Collection<Cell> cells) {
        List<Cell> list=new ArrayList<>(cells);
        list.sort(Comparator.comparingInt(Cell::r).thenComparingInt(Cell::c)); return list.toString();
    }
    String description() {
        StringBuilder b=new StringBuilder("БАЗА ЗНАНИЙ\n\n");
        b.append("Посещено: ").append(observations.size()).append("\n");
        b.append("Безопасные: ").append(sorted(safe)).append("\n\n");
        b.append("Ямы установлены: ").append(sorted(certainPit)).append("\n\n");
        b.append(monsterDead ? "Вампус мёртв.\n" : "Возможные клетки Вампуса: "+sorted(wCandidates)+"\n");
        b.append("\nПредложения о ямах:\n");
        for(Set<Cell> clause : clauses) {
            List<String> terms=new ArrayList<>();
            for(Cell p : clause) terms.add("Яма"+p);
            b.append(String.join(" ИЛИ ",terms)).append("\n");
        }
        b.append("\nНаблюдения:\n");
        observations.forEach((p,v)->b.append(p).append(": ")
                .append(v.stench()?"запах ":"нет запаха ")
                .append(v.breeze()?"ветер ":"нет ветра ")
                .append(v.glitter()?"блеск":"").append("\n"));
        return b.toString();
    }
}