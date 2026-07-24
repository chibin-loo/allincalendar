package com.artlu;

import javax.swing.*;
import java.awt.*;
import java.time.DayOfWeek;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class SettingsWindow {

    // Each setting registers a function that reads its current value back as text
    static Map<String, Supplier<String>> readers;

    static void open(JFrame parent, Runnable onSave) {
        readers = new LinkedHashMap<>();

        JDialog dialog = new JDialog(parent, "Settings", true);
        dialog.setLayout(new BorderLayout());

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Calendars", wrap(calendarsSection()));
        tabs.addTab("Gradescope", wrap(gradescopeSection()));
        tabs.addTab("Scheduling", wrap(schedulingSection()));
        tabs.addTab("Display", wrap(displaySection()));
        dialog.add(tabs, BorderLayout.CENTER);

        JButton save = new JButton("Save");
        JButton cancel = new JButton("Cancel");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.add(cancel);
        buttons.add(save);
        dialog.add(buttons, BorderLayout.SOUTH);

        save.addActionListener(e -> {
            try {
                Map<String, String> values = Settings.load();
                for (Map.Entry<String, Supplier<String>> entry : readers.entrySet()) {
                    values.put(entry.getKey(), entry.getValue().get());
                }
                Settings.save(values);
                dialog.dispose();
                onSave.run();
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(dialog, "Could not save settings.");
            }
        });
        cancel.addActionListener(e -> dialog.dispose());

        dialog.getRootPane().setDefaultButton(save);
        dialog.setSize(640, 480);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    // ---------- the four sections ----------

    static JPanel calendarsSection() {
        JPanel p = newPanel();
        addNote(p, "Paste the iCal/ICS link from Brightspace, Canvas, or Google Calendar.");
        for (int i = 1; i <= 5; i++) {
            addText(p, "calendar" + i, "Calendar link " + i);
        }
        return p;
    }

    static JPanel gradescopeSection() {
        JPanel p = newPanel();
        addNote(p, "Leave blank to skip Gradescope entirely.");
        addText(p, "gradescope_email", "Email");
        addPassword(p, "gradescope_password", "Password");
        return p;
    }

    static JPanel schedulingSection() {
        JPanel p = newPanel();
        addBool(p, "auto_schedule", "Automatically schedule work blocks", true);
        addTime(p, "day_start", "Earliest work time", "08:00");
        addTime(p, "day_end", "Latest work time", "22:00");
        addInt(p, "default_task_minutes", "Default task length (min)", 90, 15, 600, 15);
        addInt(p, "max_work_minutes_per_day", "Max work per day (min)", 240, 0, 960, 30);
        addInt(p, "max_block_minutes", "Longest single block (min)", 120, 15, 480, 15);
        addInt(p, "break_minutes", "Break between blocks (min)", 15, 0, 120, 5);
        addInt(p, "min_gap_minutes", "Ignore gaps shorter than (min)", 30, 5, 180, 5);
        addInt(p, "schedule_lead_days", "Start work within (days) of due date", 7, 1, 90, 1);
        addInt(p, "schedule_days_ahead", "Plan ahead (days)", 60, 7, 365, 7);
        addDays(p, "no_work_days", "Never schedule on");
        return p;
    }

    static JPanel displaySection() {
        JPanel p = newPanel();
        addInt(p, "months_back", "Months of history to load", 1, 0, 24, 1);
        addInt(p, "months_ahead", "Months ahead to load", 4, 1, 24, 1);
        return p;
    }

    // ---------- building blocks ----------

    static JPanel newPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        p.putClientProperty("row", 0);
        return p;
    }

    // Puts the section in a scroll pane, pinned to the top
    static JScrollPane wrap(JPanel p) {
        JPanel holder = new JPanel(new BorderLayout());
        holder.add(p, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder);
        scroll.setBorder(null);
        return scroll;
    }

    static int nextRow(JPanel p) {
        int row = (Integer) p.getClientProperty("row");
        p.putClientProperty("row", row + 1);
        return row;
    }

    static void addRow(JPanel p, String label, Component field) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 4, 5, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridy = nextRow(p);

        c.gridx = 0;
        c.weightx = 0;
        p.add(new JLabel(label + ":"), c);

        c.gridx = 1;
        c.weightx = 1;
        p.add(field, c);
    }

    static void addNote(JPanel p, String text) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 10, 4);
        c.gridy = nextRow(p);
        c.gridx = 0;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;

        JLabel note = new JLabel(text);
        note.setForeground(new Color(110, 110, 110));
        note.setFont(note.getFont().deriveFont(Font.ITALIC, 12f));
        p.add(note, c);
    }

    static void addText(JPanel p, String key, String label) {
        JTextField box = new JTextField(Settings.get(key, ""));
        addRow(p, label, box);
        readers.put(key, () -> box.getText().trim());
    }

    static void addPassword(JPanel p, String key, String label) {
        JPasswordField box = new JPasswordField(Settings.get(key, ""));
        addRow(p, label, box);
        readers.put(key, () -> new String(box.getPassword()).trim());
    }

    static void addBool(JPanel p, String key, String label, boolean fallback) {
        boolean on = Settings.get(key, String.valueOf(fallback)).equalsIgnoreCase("true");
        JCheckBox box = new JCheckBox(label, on);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 4, 5, 4);
        c.gridy = nextRow(p);
        c.gridx = 0;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        p.add(box, c);
        readers.put(key, () -> String.valueOf(box.isSelected()));
    }

    static void addInt(JPanel p, String key, String label, int fallback, int min, int max, int step) {
        JSpinner spinner = new JSpinner(
                new SpinnerNumberModel(Settings.getInt(key, fallback), min, max, step));
        addRow(p, label, spinner);
        readers.put(key, () -> String.valueOf(spinner.getValue()));
    }

    static void addTime(JPanel p, String key, String label, String fallback) {
        String current = Settings.get(key, fallback);
        JComboBox<String> box = new JComboBox<>();
        for (int h = 0; h < 24; h++) {
            box.addItem(String.format("%02d:00", h));
            box.addItem(String.format("%02d:30", h));
        }
        box.setSelectedItem(current);
        addRow(p, label, box);
        readers.put(key, () -> String.valueOf(box.getSelectedItem()));
    }

    static void addDays(JPanel p, String key, String label) {
        String current = Settings.get(key, "");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JCheckBox[] boxes = new JCheckBox[7];
        DayOfWeek[] order = { DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY };
        String[] shortNames = { "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat" };

        for (int i = 0; i < 7; i++) {
            boxes[i] = new JCheckBox(shortNames[i], current.contains(order[i].toString()));
            row.add(boxes[i]);
        }
        addRow(p, label, row);

        readers.put(key, () -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 7; i++) {
                if (boxes[i].isSelected()) {
                    if (sb.length() > 0)
                        sb.append(",");
                    sb.append(order[i]);
                }
            }
            return sb.toString();
        });
    }

}
