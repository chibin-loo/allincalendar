package com.artlu;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;

public class TaskDialog {

    // Shows the form. Returns the new task, or null if cancelled.
    static Event open(JFrame parent, LocalDate defaultDate) {
        JDialog dialog = new JDialog(parent, "New Task", true);
        dialog.setLayout(new BorderLayout());

        JTextField nameField = new JTextField();
        JTextField dateField = new JTextField(defaultDate == null ? "" : defaultDate.toString());
        JTextField timeField = new JTextField();
        JSpinner durationSpinner = new JSpinner(new SpinnerNumberModel(90, 0, 1440, 15));
        JTextArea descArea = new JTextArea(4, 20);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;

        addRow(form, c, 0, "Task name", nameField);
        addRow(form, c, 1, "Due date (2026-08-20)", dateField);
        addRow(form, c, 2, "Due time (18:00, optional)", timeField);
        addRow(form, c, 3, "How long will it take? (min)", durationSpinner);
        addRow(form, c, 4, "Notes (optional)", new JScrollPane(descArea));

        dialog.add(form, BorderLayout.CENTER);

        JButton save = new JButton("Add Task");
        JButton cancel = new JButton("Cancel");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(save);
        dialog.add(buttons, BorderLayout.SOUTH);

        // Holder so the listener can hand a result back out
        final Event[] result = new Event[1];

        save.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Please give the task a name.");
                return;
            }

            String date = dateField.getText().trim();
            if (!date.isEmpty()) {
                try {
                    LocalDate.parse(date);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(dialog, "Date should look like 2026-08-20.");
                    return;
                }
            }

            Event task = new Event();
            task.name = name;
            task.date = date.isEmpty() ? "no date" : date;
            task.time = timeField.getText().trim();
            task.durationMin = (Integer) durationSpinner.getValue();
            task.description = descArea.getText().trim();
            task.userAdded = true;
            task.kind = "task";

            result[0] = task;
            dialog.dispose();
        });

        cancel.addActionListener(e -> dialog.dispose());

        dialog.getRootPane().setDefaultButton(save); // Enter saves
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);

        return result[0];
    }

    static void addRow(JPanel form, GridBagConstraints c, int row, String label, Component field) {
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        form.add(new JLabel(label + ":"), c);

        c.gridx = 1;
        c.weightx = 1;
        form.add(field, c);
    }
}