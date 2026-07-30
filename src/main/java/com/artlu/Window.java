package com.artlu;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;

import com.artlu.Main.WorkBlock;

import javax.swing.JButton;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

public class Window {
    static List<Event> currentEvents = new ArrayList<>();
    static List<Event> visibleEvents = new ArrayList<>();
    static JList<String> mainList;
    static DefaultListModel<String> listModel;
    static JTabbedPane tabs;
    static boolean showPast = false;
    static JTextArea detailsArea = new JTextArea(5, 40);
    static JLabel shortfallBanner = new JLabel();

    static void redrawAll() {
        redraw(listModel);
    }

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
        listModel = model;
        JList<String> list = new JList<>(model);
        mainList = list;
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

        // Only shows up when the scheduler ran out of room — amber, not red,
        // because this is worth knowing rather than broken.
        shortfallBanner.setOpaque(true);
        shortfallBanner.setBackground(new java.awt.Color(255, 244, 214));
        shortfallBanner.setForeground(new java.awt.Color(120, 80, 0));
        shortfallBanner.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
        shortfallBanner.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        shortfallBanner.setVisible(false);

        JPanel listPanel = new JPanel(new BorderLayout());
        listPanel.add(shortfallBanner, BorderLayout.NORTH);
        listPanel.add(split, BorderLayout.CENTER);
        listPanel.add(buttonPanel, BorderLayout.SOUTH);

        tabs = new JTabbedPane();
        tabs.addTab("List", listPanel);
        tabs.addTab("Day", DayWindow.panel);
        tabs.addTab("Week", WeekWindow.panel);
        tabs.addTab("Month", MonthWindow.panel);

        frame.add(tabs, BorderLayout.CENTER);

        // A menu bar gives file-level actions a home, away from the tab buttons.
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem exportItem = new JMenuItem("Export .ics...");
        exportItem.addActionListener(clickEvent -> IcsExport.run(frame, currentEvents));
        fileMenu.add(exportItem);
        menuBar.add(fileMenu);
        frame.setJMenuBar(menuBar);

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
        Event e = TaskDialog.open(frame, java.time.LocalDate.now());
        if (e == null) {
            return;
        }
        try {
            Main.saveNewTask(e);
            currentEvents.add(e);
            redraw(model);

            int row = visibleEvents.indexOf(e);
            if (row >= 0) {
                mainList.setSelectedIndex(row);
                mainList.ensureIndexIsVisible(row);
            }
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

    // Called after a block is dragged: persist the tasks and redraw everything.
    static void commitEventEdit(Event e) {
        try {
            Main.saveTasks(currentEvents);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        redrawAll();
        CalendarUI.select(e); // keep the moved item selected in the details pane
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
        Main.applyWorkBlocks(currentEvents);
        model.clear();
        visibleEvents.clear();
        for (Event e : currentEvents) {
            if (!showPast && Main.isPast(e.date, e.time)) {
                continue;
            }
            visibleEvents.add(e);
            String when = e.time.isBlank() ? e.date : (e.date + " " + e.time);
            String mark = e.isDone() ? " [done]" : "";
            if (e.unscheduledMin > 0) {
                mark += "   [" + Main.fmtHours(e.unscheduledMin) + " unscheduled]";
            }
            model.addElement(when + "   " + e.name + mark);
        }
        updateShortfallBanner();
        MonthWindow.build(currentEvents);
        DayWindow.build(currentEvents);
        WeekWindow.build(currentEvents);
    }

    // Warns when the scheduler couldn't fit everything in. Two things cause it —
    // a genuinely full calendar, or a lead window / daily cap too tight for a big
    // task — and the number alone can't tell them apart, so the tooltip names both.
    static void updateShortfallBanner() {
        int tasks = 0;
        int minutes = 0;
        for (Event e : currentEvents) {
            if (e.unscheduledMin > 0) {
                tasks++;
                minutes += e.unscheduledMin;
            }
        }

        if (minutes > 0) {
            shortfallBanner.setText(tasks + (tasks == 1 ? " task doesn't" : " tasks don't")
                    + " fully fit before its deadline — "
                    + Main.fmtHours(minutes) + " unscheduled");
            shortfallBanner.setToolTipText("<html>There was no free time left inside the"
                    + " lead window before the deadline.<br>Free up time, or raise the lead"
                    + " days / daily work cap in Settings.</html>");
        }
        shortfallBanner.setVisible(minutes > 0);

        if (shortfallBanner.getParent() != null) {
            shortfallBanner.getParent().revalidate(); // the row appears or collapses
        }
    }

    static void removeSelected(JList<String> list, DefaultListModel<String> model) {
        int row = list.getSelectedIndex(); // which row is highlighted (-1 if none)
        if (row < 0) {
            return;
        }
        Event selected = visibleEvents.get(row);

        if (selected.sourceTask != null) {
            selected = selected.sourceTask;
        }

        if (!selected.userAdded) {
            JOptionPane.showMessageDialog(null,
                    "That's a calendar event — it can only be changed in Brightspace or Google.");
            return;
        }

        currentEvents.remove(selected);
        saveAndRefresh(model);
    }

    static void toggleDone(Event e) {
        Event target = e.sourceTask != null ? e.sourceTask : e;
        target.done = !target.done;
        try {
            if (target.userAdded) {
                Main.saveTasks(currentEvents);
            } else if (target.done) {
                Main.addDoneOverride(target);
            } else {
                Main.removeDoneOverride(target);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        redrawAll();
    }

    static void deleteEvent(Event e) {
        Event target = e.sourceTask != null ? e.sourceTask : e;
        if (!target.userAdded) {
            JOptionPane.showMessageDialog(null,
                    "That's a calendar event — it can only be changed in Brightspace or Google.");
            return;
        }
        currentEvents.remove(target);
        saveAndRefresh(listModel);
    }

    // Reopens the form on an item and saves whatever changed.
    static void editEvent(Event e) {
        Event target = e.sourceTask != null ? e.sourceTask : e;
        if (!target.userAdded) {
            JOptionPane.showMessageDialog(null,
                    "That's a calendar event — it can only be changed in Brightspace or Google.");
            return;
        }
        String oldName = target.name;
        if (!TaskDialog.edit(null, target)) {
            return; // cancelled — nothing was touched
        }
        try {
            if (!target.name.equals(oldName)) {
                Main.renamePins(oldName, target.name);
            }
            Main.saveTasks(currentEvents);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        redrawAll();
        CalendarUI.select(target);
    }

    static void goToDay(java.time.LocalDate d) {
        DayWindow.currentDay = d;
        DayWindow.build(currentEvents);
        if (tabs != null) {
            tabs.setSelectedIndex(1);
        }
    }

    /** Opens the dialog in task mode, pre-filled with a date/time. */
    static void newTaskAt(java.time.LocalDate date, String time) {
        addNew(TaskDialog.open(null, date, time));
    }

    /** Opens the dialog in event mode — used by drag-to-create on the grid. */
    static void newEventAt(java.time.LocalDate date, String startTime, String endTime) {
        addNew(TaskDialog.open(null, date, startTime, endTime, true));
    }

    // Same, but for a whole day rather than a spot on the grid: offers an hour
    // at the start of your day, since a button click carries no time with it.
    static void newEventOn(java.time.LocalDate date) {
        int startMin = Main.minutesOf(Settings.get("day_start", "08:00"));
        if (startMin < 0) {
            startMin = 9 * 60; // the setting was unreadable — 09:00 it is
        }
        newEventAt(date, Main.fmtMinutes(startMin), Main.fmtMinutes(startMin + 60));
    }

    // Saves whatever the dialog handed back, then redraws. null = cancelled.
    private static void addNew(Event e) {
        if (e == null) {
            return;
        }
        try {
            Main.saveNewTask(e);
            currentEvents.add(e);
            redrawAll();
            CalendarUI.select(e);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // replace the body of markDone
    static void markDone(JList<String> list, DefaultListModel<String> model) {
        int row = list.getSelectedIndex();
        if (row < 0) {
            return;
        }
        toggleDone(visibleEvents.get(row));
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
        if (e.isDone()) {
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
        Event target = selected.sourceTask != null ? selected.sourceTask : selected;
        if (target.userAdded) {
            editEvent(target); // your own item — open it for editing
            return;
        }
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

    // Moves a work block by re-pinning it: forget where it was, remember where
    // it landed. redrawAll then rebuilds work blocks with this pin honored.
    static void repinWork(Event task, java.time.LocalDate oldDate, int oldStart,
            java.time.LocalDate newDate, int newStart, int newEnd) {
        if (task == null) {
            return;
        }
        try {
            Main.removePin(task.name, oldDate, oldStart);
            Main.addPin(task.name, newDate, newStart, newEnd);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        redrawAll();
    }
}