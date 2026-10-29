public record Cell(int r, int c) {
    @Override public String toString() { return "(" + (r + 1) + "," + (c + 1) + ")"; }
}