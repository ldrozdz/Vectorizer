package pl.net.lkd.vectorizer.gui;

import javax.swing.*;
import java.awt.*;

public class StripeRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list,
                                                  Object value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
        JLabel label = (JLabel) super.getListCellRendererComponent(list,
              value,
              index,
              isSelected,
              cellHasFocus);

        if (index % 2 == 0) {
            label.setBackground(new Color(247, 247, 247));
        }

        label.setVerticalAlignment(SwingConstants.TOP);
        label.setText(String.format("[%s] %s", index, label.getText()));
        return label;
    }
}