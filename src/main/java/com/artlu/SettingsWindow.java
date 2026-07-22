package com.artlu;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.Map;

public class SettingsWindow {

    // The settings we show, in order: key -> label shown to the user
    static final String[][] FIELDS = {
            { "calendar1", "Calendar link 1" },
            { "calendar2", "Calendar link 2" },
            { "calendar3", "Calendar link 3" },
            { "gradescope_email", "Gradescope email" },
            { "gradescope_password", "Gradescope password" },
            { "months_back", "Months of history to show" },
            { "months_ahead", "Months ahead to show" },
    };

    static void open(JFrame parent, Runnable onSave) {
        JDialog dialog = new JDialog(parent, "Settings", true);
        dialog.setSize(600, 350);
        dialog.setLayout(new BorderLayout());

        JPanel grid = new JPanel(new GridLayout(0, 2, 5, 5));
        Map<String, JTextField> boxes = new LinkedHashMap<>();

        for (String[] field : FIELDS) {
            String key = field[0];
            String label = field[1];

            grid.add(new JLabel(label + ":"));
            JTextField box = new JTextField(Settings.get(key, ""));
            grid.add(box);
            boxes.put(key, box);
        }

        dialog.add(grid, BorderLayout.CENTER);

        JButton saveButton = new JButton("Save");
        dialog.add(saveButton, BorderLayout.SOUTH);

        saveButton.addActionListener(clickEvent -> {
            try {
                Map<String, String> values = Settings.load(); // keep any settings not shown here
                for (Map.Entry<String, JTextField> entry : boxes.entrySet()) {
                    values.put(entry.getKey(), entry.getValue().getText().trim());
                }
                Settings.save(values);
                dialog.dispose();
                onSave.run(); // tell the main window to reload
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(dialog, "Could not save settings.");
            }
        });

        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }
}