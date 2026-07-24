package com.artlu;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;

public class MonthWindow {
    static LocalDate currentMonth = LocalDate.now();
    static JPanel panel = new JPanel(new BorderLayout());

    // Builds the whole month view into the shared panel
    static void build(List<Event> events) {
        panel.removeAll(); // clear whatever was there before

        // Header: prev/today/next and the month name
        JPanel header = new JPanel(new BorderLayout());
        JButton prev = new JButton("◀");
        JButton next = new JButton("▶");
        JButton today = new JButton("Today");
        JLabel title = new JLabel(currentMonth.getMonth() + " " + currentMonth.getYear(),
                SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));

        prev.addActionListener(e -> {
            currentMonth = currentMonth.minusMonths(1);
            build(events);
        });
        next.addActionListener(e -> {
            currentMonth = currentMonth.plusMonths(1);
            build(events);
        });
        today.addActionListener(e -> {
            currentMonth = LocalDate.now();
            build(events);
        });

        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        leftButtons.add(prev);
        leftButtons.add(today);

        header.add(leftButtons, BorderLayout.WEST);
        header.add(title, BorderLayout.CENTER);
        header.add(next, BorderLayout.EAST);
        header.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(header, BorderLayout.NORTH);

        // The grid
        JPanel grid = new JPanel(new GridLayout(0, 7, 4, 4));
        grid.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));

        String[] dayNames = { "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat" };
        for (String d : dayNames) {
            JLabel dayLabel = new JLabel(d, SwingConstants.CENTER);
            dayLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
            grid.add(dayLabel);
        }

        LocalDate firstOfMonth = currentMonth.withDayOfMonth(1);
        int leadingBlanks = firstOfMonth.getDayOfWeek().getValue() % 7;
        for (int i = 0; i < leadingBlanks; i++) {
            grid.add(new JLabel(""));
        }

        java.util.Map<String, List<Event>> grouped = Main.byDate(events);

        int daysInMonth = currentMonth.lengthOfMonth();
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate d = currentMonth.withDayOfMonth(day);
            grid.add(makeDayCell(d, grouped.getOrDefault(d.toString(), java.util.List.of())));
        }

        panel.add(new JScrollPane(grid), BorderLayout.CENTER);
        panel.revalidate();
        panel.repaint();
    }

    static JPanel makeDayCell(LocalDate date, List<Event> events) {
        JPanel cell = new JPanel();
        cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
        cell.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)));
        cell.setBackground(date.equals(LocalDate.now()) ? new Color(232, 240, 254) : Color.WHITE);

        JLabel number = new JLabel(String.valueOf(date.getDayOfMonth()));
        number.setFont(new Font("Segoe UI", Font.BOLD, 12));
        cell.add(number);

        for (Event e : events) {
            String label = e.isDone() ? e.name + "  [DONE]" : e.name;
            JLabel chip = new JLabel(label);
            chip.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            chip.setForeground(e.isDone() ? new Color(130, 130, 130) : Color.BLACK);
            cell.add(chip);
        }
        return cell;
    }
}