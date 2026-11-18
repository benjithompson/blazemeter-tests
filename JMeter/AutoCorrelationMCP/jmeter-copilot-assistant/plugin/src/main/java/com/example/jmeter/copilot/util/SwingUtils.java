import javax.swing.*;
import java.awt.*;

public class SwingUtils {

    public static void setComponentFont(Component component, Font font) {
        component.setFont(font);
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                setComponentFont(child, font);
            }
        }
    }

    public static void showMessageDialog(Component parent, String message, String title) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    public static int showConfirmDialog(Component parent, String message, String title) {
        return JOptionPane.showConfirmDialog(parent, message, title, JOptionPane.YES_NO_OPTION);
    }

    public static void setComponentVisibility(Component component, boolean isVisible) {
        component.setVisible(isVisible);
    }

    public static void setComponentEnabled(Component component, boolean isEnabled) {
        component.setEnabled(isEnabled);
    }
}