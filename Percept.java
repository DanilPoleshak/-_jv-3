public record Percept(boolean stench, boolean breeze, boolean glitter, boolean bump, boolean scream) {
    private static String yes(boolean value) { return value ? "ДА" : "нет"; }
    @Override public String toString() {
        return "Запах: " + yes(stench) + "   Ветер: " + yes(breeze)
                + "   Блеск: " + yes(glitter) + "   Удар: " + yes(bump) + "   Крик: " + yes(scream);
    }
}