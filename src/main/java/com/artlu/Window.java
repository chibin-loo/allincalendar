package com.artlu;

import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JButton;
import javax.swing.DefaultListModel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

public class Window {
    static List<Event> currentEvents = new ArrayList<>();
    static List<Event> visibleEvents = new ArrayList<>();
    static boolean showPast = false;

    public static void main(String[] args) {
        try {
            javax.swing.UIManager.setLookAndFeel(
                    javax.swing.UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        JFrame frame = new JFrame("My To-Do App");
        frame.setSize(400, 500);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        DefaultListModel<String> model = new DefaultListModel<>();
        JList<String> list = new JList<>(model);
        JScrollPane scroll = new JScrollPane(list);
        frame.add(scroll, BorderLayout.CENTER);

        JButton addButton = new JButton("Add Task");
        JButton removeButton = new JButton("Remove");
        JButton doneButton = new JButton("Mark Done");
        JButton refreshButton = new JButton("Full Refresh");
        JButton togglePastButton = new JButton("Show Past");
        JPanel buttonPanel = new JPanel(new java.awt.GridLayout(0, 3, 5, 5));
        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(doneButton);
        buttonPanel.add(refreshButton);
        buttonPanel.add(togglePastButton);
        frame.add(buttonPanel, BorderLayout.SOUTH);

        refreshButton.addActionListener(clickEvent -> reload(model));
        addButton.addActionListener(clickEvent -> addTask(frame, model));
        removeButton.addActionListener(clickEvent -> removeSelected(list, model));
        doneButton.addActionListener(clickEvent -> markDone(list, model));
        togglePastButton.addActionListener(clickEvent -> togglePast(model, togglePastButton));
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent clickEvent) {
                if (clickEvent.getClickCount() == 2) { // only react to double-clicks
                    openSelected(list);
                }
            }
        });

        frame.setVisible(true);
        javax.swing.SwingUtilities.invokeLater(() -> reload(model));
    }

    static void addTask(JFrame frame, DefaultListModel<String> model) {
        try {
            String name = JOptionPane.showInputDialog(frame, "Task name:");
            if (name == null || name.isBlank()) {
                return;
            }

            String date = JOptionPane.showInputDialog(frame, "Date (like 2026-01-20), or leave blank:");
            String time = JOptionPane.showInputDialog(frame, "Time (like 18:00), or leave blank:");

            Event e = new Event();
            e.name = name.trim();
            e.date = (date == null || date.isBlank()) ? "no date" : date.trim();
            e.time = (time == null) ? "" : time.trim();
            e.userAdded = true;

            Main.saveNewTask(e);
            currentEvents.add(e);
            redraw(model);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // Saves the user's tasks to match the current list, then redraws
    static void saveAndRefresh(DefaultListModel<String> model) {
        try {
            Main.saveTasks(currentEvents, new ArrayList<>()); // write current tasks out
            redraw(model);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // Re-downloads everything from the internet, then redraws. Slow — only on
    // demand.
    static void reload(DefaultListModel<String> model) {
        try {
            currentEvents = Main.buildEventList(); // the slow network part
            redraw(model);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // Just redraws the list from data we already have. Fast — no network.
    static void redraw(DefaultListModel<String> model) {
        model.clear();
        visibleEvents.clear();
        for (Event e : currentEvents) {
            if (!showPast && Main.isPast(e.date, e.time)) {
                continue;
            }
            visibleEvents.add(e);
            String when = e.time.isBlank() ? e.date : (e.date + " " + e.time);
            String mark = e.done ? " [done]" : "";
            model.addElement(when + "   " + e.name + mark);
        }
    }

    static void removeSelected(JList<String> list, DefaultListModel<String> model) {
        int row = list.getSelectedIndex(); // which row is highlighted (-1 if none)
        if (row < 0) {
            return;
        }
        Event selected = visibleEvents.get(row);
        if (!selected.userAdded) {
            JOptionPane.showMessageDialog(null,
                    "That's a calendar event — it can only be changed in Brightspace or Google.");
            return;
        }
        currentEvents.remove(selected);
        saveAndRefresh(model);
    }

    static void markDone(JList<String> list, DefaultListModel<String> model) {
        int row = list.getSelectedIndex();
        if (row < 0) {
            return;
        }

        Event selected = visibleEvents.get(row);
        selected.done = !selected.done; // flip it, don't force true

        try {
            if (selected.userAdded) {
                Main.saveTasks(currentEvents, new ArrayList<>());
            } else if (selected.done) {
                Main.addDoneOverride(selected); // now done -> remember it
            } else {
                Main.removeDoneOverride(selected); // now not done -> forget it
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        redraw(model);
    }

    static void togglePast(DefaultListModel<String> model, JButton togglePastButton) {
        showPast = !showPast;
        togglePastButton.setText(showPast ? "Hide Past" : "Show Past");
        redraw(model);
    }

    static void openSelected(JList<String> list) {
        int row = list.getSelectedIndex();
        if (row < 0) {
            return;
        }
        Event selected = visibleEvents.get(row);
        if (selected.url.isBlank()) {
            JOptionPane.showMessageDialog(null, "This item has no link to open.");
            return;
        }

        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI(selected.url));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}