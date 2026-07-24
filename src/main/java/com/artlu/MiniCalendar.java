package com.artlu;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.function.Consumer;

public class MiniCalendar {

    // Builds a small month picker. onPick runs with whatever date is clicked.
    static JPanel create(LocalDate selected, Consumer<LocalDate> onPick) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        rebuild(wrapper, selected.withDayOfMonth(1), selected, onPick);
        return wrapper;
    }

    // Fills the wrapper with a month grid. shownMonth = which month to draw.
    static void rebuild(JPanel wrapper, LocalDate shownMonth, LocalDate selected,
            Consumer<LocalDate> onPick) {
        wrapper.removeAll();

        // Header: month name with arrows
        JPanel header = new JPanel(new BorderLayout());
        JButton prev = new JButton("‹");
        JButton next = new JButton("›");
        prev.setMargin(new Insets(0, 6, 0, 6));
        next.setMargin(new Insets(0, 6, 0, 6));

        JLabel title = new JLabel(monthName(shownMonth) + " " + shownMonth.getYear(),
                SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));

        prev.addActionListener(e -> rebuild(wrapper, shownMonth.minusMonths(1), selected, onPick));
        next.addActionListener(e -> rebuild(wrapper, shownMonth.plusMonths(1), selected, onPick));

        header.add(prev, BorderLayout.WEST);
        header.add(title, BorderLayout.CENTER);
        header.add(next, BorderLayout.EAST);
        wrapper.add(header, BorderLayout.NORTH);

        // Grid of day numbers
        JPanel grid = new JPanel(new GridLayout(0, 7, 2, 2));

        for (String d : new String[] { "S", "M", "T", "W", "T", "F", "S" }) {
            JLabel lbl = new JLabel(d, SwingConstants.CENTER);
            lbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            lbl.setForeground(new Color(140, 140, 140));
            grid.add(lbl);
        }

        int blanks = shownMonth.getDayOfWeek().getValue() % 7;
        for (int i = 0; i < blanks; i++) {
            grid.add(new JLabel(""));
        }

        LocalDate today = LocalDate.now();
        for (int day = 1; day <= shownMonth.lengthOfMonth(); day++) {
            LocalDate cellDate = shownMonth.withDayOfMonth(day);

            JButton dayButton = new JButton(String.valueOf(day));
            dayButton.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            dayButton.setMargin(new Insets(1, 1, 1, 1));
            dayButton.setFocusPainted(false);

            if (cellDate.equals(selected)) {
                dayButton.setBackground(new Color(70, 110, 200));
                dayButton.setForeground(Color.WHITE);
            } else if (cellDate.equals(today)) {
                dayButton.setBackground(new Color(220, 232, 255));
            }

            dayButton.addActionListener(e -> onPick.accept(cellDate));
            grid.add(dayButton);
        }

        wrapper.add(grid, BorderLayout.CENTER);
        wrapper.revalidate();
        wrapper.repaint();
    }

    static String monthName(LocalDate d) {
        String m = d.getMonth().toString();
        return m.charAt(0) + m.substring(1).toLowerCase();
    }
}