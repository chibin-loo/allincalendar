package com.artlu;

import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JButton;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

public class Window {
    static List<Event> currentEvents = new ArrayList<>();
    static List<Event> visibleEvents = new ArrayList<>();
    static boolean showPast = false;
    static JTextArea detailsArea = new JTextArea(5, 40);

    public static void main(String[] args) {
        try {
            com.formdev.flatlaf.FlatLightLaf.setup();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        JFrame frame = new JFrame("AllInCalendar");
        frame.setSize(1180, 820);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        DefaultListModel<String> model = new DefaultListModel<>();
        JList<String> list = new JList<>(model);
        list.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 14));
        list.setFixedCellHeight(28);
        JScrollPane scroll = new JScrollPane(list);

        JButton addButton = new JButton("Add Task");
        JButton removeButton = new JButton("Remove");
        JButton doneButton = new JButton("Mark Done");
        JButton refreshButton = new JButton("Full Refresh");
        JButton togglePastButton = new JButton("Show Past");
        JButton settingsButton = new JButton("Settings");
        JPanel buttonPanel = new JPanel(new java.awt.GridLayout(0, 3, 5, 5));
        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(doneButton);
        buttonPanel.add(refreshButton);
        buttonPanel.add(togglePastButton);
        buttonPanel.add(settingsButton);

        // Details panel below the list
        detailsArea.setEditable(false);
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);
        detailsArea.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 13));
        detailsArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane detailsScroll = new JScrollPane(detailsArea);
        detailsScroll.setBorder(BorderFactory.createTitledBorder("Details"));

        // List on top, details below — a split you can drag
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scroll, detailsScroll);
        split.setResizeWeight(0.7); // list gets 70% of the space

        JPanel listPanel = new JPanel(new BorderLayout());
        listPanel.add(split, BorderLayout.CENTER);
        listPanel.add(buttonPanel, BorderLayout.SOUTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("List", listPanel);
        tabs.addTab("Day", DayWindow.panel);
        tabs.addTab("Week", WeekWindow.panel);
        tabs.addTab("Month", MonthWindow.panel);

        frame.add(tabs, BorderLayout.CENTER);

        refreshButton.addActionListener(clickEvent -> reload(model));
        addButton.addActionListener(clickEvent -> addTask(frame, model));
        removeButton.addActionListener(clickEvent -> removeSelected(list, model));
        doneButton.addActionListener(clickEvent -> markDone(list, model));
        togglePastButton.addActionListener(clickEvent -> togglePast(model, togglePastButton));
        settingsButton.addActionListener(clickEvent -> SettingsWindow.open(frame, () -> reload(model)));
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent clickEvent) {
                if (clickEvent.getClickCount() == 2) {
                    openSelected(list);
                }
            }
        });
        list.addListSelectionListener(selectionEvent -> showDetails(list));

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
            Main.saveTasks(currentEvents); // write current tasks out
            redraw(model);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // Re-downloads everything from the internet, then redraws. Slow — only on
    // demand.
    static void reload(DefaultListModel<String> model) {
        model.clear();
        model.addElement("Loading...");

        new javax.swing.SwingWorker<List<Event>, Void>() {
            // Runs on a BACKGROUND thread — slow work goes here
            protected List<Event> doInBackground() throws Exception {
                return Main.buildEventList();
            }

            // Runs on the DRAWING thread once the background work finishes
            protected void done() {
                try {
                    currentEvents = get(); // the result from doInBackground
                    redraw(model);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    model.clear();
                    model.addElement("Failed to load - see console");
                }
            }
        }.execute();
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
        MonthWindow.build(currentEvents);
        DayWindow.build(currentEvents);
        WeekWindow.build(currentEvents);
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
                Main.saveTasks(currentEvents);
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

    static void showDetails(JList<String> list) {
        int row = list.getSelectedIndex();
        if (row < 0 || row >= visibleEvents.size()) {
            detailsArea.setText("");
            return;
        }

        Event e = visibleEvents.get(row);
        StringBuilder text = new StringBuilder();
        text.append(e.name).append("\n\n");

        String when = e.time.isBlank() ? e.date : (e.date + " " + e.time);
        if (!e.endTime.isBlank()) {
            when += " - " + e.endTime;
        }
        text.append(when).append("\n");

        if (e.done) {
            text.append("[done]\n");
        }
        if (!e.url.isBlank()) {
            text.append("\nDouble-click to open: ").append(e.url).append("\n");
        }
        if (!e.description.isBlank()) {
            text.append("\n").append(e.description);
        }

        detailsArea.setText(text.toString());
        detailsArea.setCaretPosition(0); // scroll back to the top
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