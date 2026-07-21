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
        JButton refreshButton = new JButton("Refresh");
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(doneButton);
        buttonPanel.add(refreshButton);
        frame.add(buttonPanel, BorderLayout.SOUTH);

        refreshButton.addActionListener(clickEvent -> refresh(model));
        addButton.addActionListener(clickEvent -> addTask(frame, model));
        removeButton.addActionListener(clickEvent -> removeSelected(list, model));
        doneButton.addActionListener(clickEvent -> markDone(list, model));

        frame.setVisible(true);
        javax.swing.SwingUtilities.invokeLater(() -> refresh(model));
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
            refresh(model);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // Saves the user's tasks to match the current list, then redraws
    static void saveAndRefresh(DefaultListModel<String> model) {
        try {
            Main.saveTasks(currentEvents, new ArrayList<>()); // write current tasks out
            refresh(model); // reload + redraw
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // Fills the list model with the current events
    static void refresh(DefaultListModel<String> model) {
        try {
            currentEvents = Main.buildEventList();
            model.clear();
            for (Event e : currentEvents) {
                String when = e.time.isBlank() ? e.date : (e.date + " " + e.time);
                String mark = e.done ? " [done]" : "";
                model.addElement(when + "   " + e.name + mark);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    static void removeSelected(JList<String> list, DefaultListModel<String> model) {
        int row = list.getSelectedIndex(); // which row is highlighted (-1 if none)
        if (row < 0) {
            return;
        }
        if (!currentEvents.get(row).userAdded) {
            JOptionPane.showMessageDialog(null,
                    "That's a calendar event — it can only be changed in Brightspace or Google.");
            return;
        }
        currentEvents.remove(row);
        saveAndRefresh(model);
    }

    static void markDone(JList<String> list, DefaultListModel<String> model) {
        int row = list.getSelectedIndex();
        if (row < 0) {
            return;
        }
        if (!currentEvents.get(row).userAdded) {
            JOptionPane.showMessageDialog(null,
                    "That's a calendar event — it can only be changed in Brightspace or Google.");
            return;
        }
        currentEvents.get(row).done = true;
        saveAndRefresh(model);
    }

}