import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            UIManager.put(
                    "Label.foreground",
                    Theme.TEXT
            );

            UIManager.put(
                    "Panel.background",
                    Theme.PANEL
            );

            new GameWindow().setVisible(true);
        });
    }
}