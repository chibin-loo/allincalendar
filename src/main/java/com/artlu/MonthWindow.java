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

        // Header: prev button, month name, next button
        JPanel header = new JPanel(new BorderLayout());
        JButton prev = new JButton("◀");
        JButton next = new JButton("▶");
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

        header.add(prev, BorderLayout.WEST);
        header.add(title, BorderLayout.CENTER);
        header.add(next, BorderLayout.EAST);
        header.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(header, BorderLayout.NORTH);

        // The grid
        JPanel grid = new JPanel(new GridLayout(0, 7, 4, 4));
        grid.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));

        String[] dayNames = { "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun" };
        for (String d : dayNames) {
            JLabel dayLabel = new JLabel(d, SwingConstants.CENTER);
            dayLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
            grid.add(dayLabel);
        }

        LocalDate firstOfMonth = currentMonth.withDayOfMonth(1);
        int leadingBlanks = firstOfMonth.getDayOfWeek().getValue() - 1;
        for (int i = 0; i < leadingBlanks; i++) {
            grid.add(new JLabel(""));
        }

        int daysInMonth = currentMonth.lengthOfMonth();
        for (int day = 1; day <= daysInMonth; day++) {
            grid.add(makeDayCell(currentMonth.withDayOfMonth(day), events));
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

        String iso = date.toString();
        for (Event e : events) {
            if (e.date.equals(iso)) {
                JLabel chip = new JLabel(e.name);
                chip.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                cell.add(chip);
            }
        }
        return cell;
    }
}