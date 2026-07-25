package com.artlu;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;

/**
 * The "new item" form. It makes two things:
 * a TASK — a deadline plus an estimate of how long it will take, which the
 * auto-scheduler then finds free time for; and
 * an EVENT — a fixed block with a start and an end, which simply sits on the
 * calendar and counts as busy time.
 */
public class TaskDialog {

    // Shows the form. Returns the new item, or null if cancelled.
    static Event open(JFrame parent, LocalDate defaultDate) {
        return open(parent, defaultDate, "");
    }

    static Event open(JFrame parent, LocalDate defaultDate, String defaultTime) {
        return open(parent, defaultDate, defaultTime, "", false);
    }

    /** asEvent decides which of the two modes the form opens in. */
    static Event open(JFrame parent, LocalDate defaultDate, String defaultTime,
            String defaultEndTime, boolean asEvent) {
        JDialog dialog = new JDialog(parent, "New Task", true);
        dialog.setLayout(new BorderLayout());

        JRadioButton taskMode = new JRadioButton("Task (deadline)", !asEvent);
        JRadioButton eventMode = new JRadioButton("Event (time block)", asEvent);
        ButtonGroup group = new ButtonGroup(); // makes the two mutually exclusive
        group.add(taskMode);
        group.add(eventMode);

        JTextField nameField = new JTextField();
        JTextField dateField = new JTextField(defaultDate == null ? "" : defaultDate.toString());
        JTextField timeField = new JTextField(defaultTime == null ? "" : defaultTime);
        JTextField endField = new JTextField(defaultEndTime == null ? "" : defaultEndTime);
        JSpinner durationSpinner = new JSpinner(new SpinnerNumberModel(90, 0, 1440, 15));
        JTextArea descArea = new JTextArea(4, 20);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;

        JPanel modes = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        modes.add(taskMode);
        modes.add(eventMode);
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        form.add(modes, c);
        c.gridwidth = 1;

        addRow(form, c, 1, "Name", nameField);
        JLabel dateLabel = addRow(form, c, 2, "Date", dateField);
        JLabel timeLabel = addRow(form, c, 3, "Time", timeField);
        JLabel endLabel = addRow(form, c, 4, "End time", endField);
        JLabel durLabel = addRow(form, c, 5, "How long will it take? (min)", durationSpinner);
        addRow(form, c, 6, "Notes (optional)", new JScrollPane(descArea));

        dialog.add(form, BorderLayout.CENTER);

        JButton save = new JButton("Add Task");
        JButton cancel = new JButton("Cancel");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(save);
        dialog.add(buttons, BorderLayout.SOUTH);

        // Relabels and greys out whichever fields the current mode doesn't use.
        Runnable syncMode = () -> {
            boolean ev = eventMode.isSelected();
            dialog.setTitle(ev ? "New Event" : "New Task");
            dateLabel.setText(ev ? "Date (2026-08-20):" : "Due date (2026-08-20):");
            timeLabel.setText(ev ? "Start time (18:00):" : "Due time (18:00, optional):");
            endLabel.setEnabled(ev);
            endField.setEnabled(ev);
            durLabel.setEnabled(!ev);
            durationSpinner.setEnabled(!ev);
            save.setText(ev ? "Add Event" : "Add Task");
        };
        taskMode.addActionListener(e -> syncMode.run());
        eventMode.addActionListener(e -> syncMode.run());
        syncMode.run(); // set the labels up for the mode we opened in

        // Holder so the listener can hand a result back out
        final Event[] result = new Event[1];

        save.addActionListener(e -> {
            boolean isEvent = eventMode.isSelected();

            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Please give it a name.");
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
            } else if (isEvent) {
                JOptionPane.showMessageDialog(dialog, "An event needs a date.");
                return;
            }

            String start = normalizeTime(timeField.getText());
            if (start == null) {
                JOptionPane.showMessageDialog(dialog, "Time should look like 18:00.");
                return;
            }

            String end = "";
            if (isEvent && !start.isEmpty()) {
                end = normalizeTime(endField.getText());
                if (end == null) {
                    JOptionPane.showMessageDialog(dialog, "End time should look like 19:00.");
                    return;
                }
                if (end.isEmpty()) {
                    end = Main.fmtMinutes(Main.minutesOf(start) + 60); // default: one hour
                }
                if (Main.minutesOf(end) <= Main.minutesOf(start)) {
                    JOptionPane.showMessageDialog(dialog, "The end time has to be after the start time.");
                    return;
                }
            }

            Event item = new Event();
            item.name = name;
            item.date = date.isEmpty() ? "no date" : date;
            item.time = start;
            item.description = descArea.getText().trim();
            item.userAdded = true;
            if (isEvent) {
                item.kind = "event";
                item.endDate = item.date;
                item.endTime = end; // blank end = an all-day event
            } else {
                item.kind = "task";
                item.durationMin = (Integer) durationSpinner.getValue();
            }

            result[0] = item;
            dialog.dispose();
        });

        cancel.addActionListener(e -> dialog.dispose());

        dialog.getRootPane().setDefaultButton(save); // Enter saves
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);

        return result[0];
    }

    /**
     * Tidies up whatever the user typed into "HH:mm". Blank stays blank;
     * null means "that isn't a time".
     */
    static String normalizeTime(String raw) {
        String s = raw.trim();
        if (s.isEmpty()) {
            return "";
        }
        try {
            int colon = s.indexOf(':');
            int hour = Integer.parseInt(colon < 0 ? s : s.substring(0, colon));
            int minute = colon < 0 ? 0 : Integer.parseInt(s.substring(colon + 1));
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return null;
            }
            return String.format("%02d:%02d", hour, minute);
        } catch (Exception ex) {
            return null;
        }
    }

    static JLabel addRow(JPanel form, GridBagConstraints c, int row, String label, Component field) {
        JLabel l = new JLabel(label + ":");
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        form.add(l, c);

        c.gridx = 1;
        c.weightx = 1;
        form.add(field, c);
        return l; // handed back so syncMode can retitle it later
    }
}
