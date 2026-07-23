package com.artlu;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class WeekWindow {
    static LocalDate weekStart = sundayOf(LocalDate.now());
    static JPanel panel = new JPanel(new BorderLayout());

    static final int HOUR_HEIGHT = 80;
    static final int LABEL_WIDTH = 60;
    static final int COL_WIDTH = 150;

    // Walks back to Monday of whatever week this date is in
    static LocalDate mondayOf(LocalDate d) {
        return d.minusDays(d.getDayOfWeek().getValue() - 1);
    }

    static void build(List<Event> events) {
        panel.removeAll();

        JPanel header = new JPanel(new BorderLayout());
        JButton prev = new JButton("◀");
        JButton next = new JButton("▶");
        JButton today = new JButton("Today");
        JLabel title = new JLabel(weekStart + "  to  " + weekStart.plusDays(6),
                SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));

        prev.addActionListener(e -> {
            weekStart = weekStart.minusWeeks(1);
            build(events);
        });
        next.addActionListener(e -> {
            weekStart = weekStart.plusWeeks(1);
            build(events);
        });
        today.addActionListener(e -> {
            weekStart = sundayOf(LocalDate.now());
            build(events);
        });

        // prev and Today sit together on the left
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        leftButtons.add(prev);
        leftButtons.add(today);

        header.add(leftButtons, BorderLayout.WEST);
        header.add(title, BorderLayout.CENTER);
        header.add(next, BorderLayout.EAST);
        header.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(header, BorderLayout.NORTH);

        // Day-name row across the top, aligned with the columns
        JPanel dayHeader = new JPanel(null);
        dayHeader.setPreferredSize(new Dimension(LABEL_WIDTH + 7 * COL_WIDTH, 28));
        for (int i = 0; i < 7; i++) {
            LocalDate d = weekStart.plusDays(i);
            JLabel lbl = new JLabel(d.getDayOfWeek().toString().substring(0, 3) + " " + d.getDayOfMonth(),
                    SwingConstants.CENTER);
            lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
            lbl.setBounds(LABEL_WIDTH + i * COL_WIDTH, 4, COL_WIDTH, 20);
            dayHeader.add(lbl);
        }

        // The grid
        JPanel grid = new JPanel(null);
        grid.setBackground(Color.WHITE);
        grid.setPreferredSize(new Dimension(LABEL_WIDTH + 7 * COL_WIDTH, 24 * HOUR_HEIGHT));

        // Hour labels and horizontal lines
        for (int hour = 0; hour < 24; hour++) {
            int y = hour * HOUR_HEIGHT;
            JLabel hourLabel = new JLabel(String.format("%02d:00", hour));
            hourLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            hourLabel.setForeground(new Color(120, 120, 120));
            hourLabel.setBounds(5, y, LABEL_WIDTH - 10, 15);
            grid.add(hourLabel);

            JPanel line = new JPanel();
            line.setBackground(new Color(230, 230, 230));
            line.setBounds(LABEL_WIDTH, y, 7 * COL_WIDTH, 1);
            grid.add(line);
        }

        // Vertical lines between days
        for (int i = 0; i <= 7; i++) {
            JPanel vline = new JPanel();
            vline.setBackground(new Color(230, 230, 230));
            vline.setBounds(LABEL_WIDTH + i * COL_WIDTH, 0, 1, 24 * HOUR_HEIGHT);
            grid.add(vline);
        }

        // Blocks, one column per day
        for (int i = 0; i < 7; i++) {
            String iso = weekStart.plusDays(i).toString();
            int columnX = LABEL_WIDTH + i * COL_WIDTH;

            for (Event e : events) {
                if (!e.date.equals(iso) || e.time.isBlank())
                    continue;

                int startMin = minutesOf(e.time);
                int endMin = e.endTime.isBlank() ? startMin + 30 : minutesOf(e.endTime);
                if (endMin <= startMin)
                    endMin = startMin + 30;

                int y = startMin * HOUR_HEIGHT / 60;
                int height = (endMin - startMin) * HOUR_HEIGHT / 60;

                JLabel block = new JLabel("<html><b>" + e.name + "</b><br>" + e.time + "</html>");
                block.setOpaque(true);
                block.setBackground(Main.colorFor(e));
                block.setForeground(Color.WHITE);
                block.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                block.setVerticalAlignment(SwingConstants.TOP);
                block.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
                block.setBounds(columnX + 2, y, COL_WIDTH - 5, Math.max(height - 3, 16));

                grid.add(block);
                grid.setComponentZOrder(block, 0);
            }
        }

        JScrollPane scroll = new JScrollPane(grid);
        scroll.setColumnHeaderView(dayHeader);
        panel.add(scroll, BorderLayout.CENTER);

        SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(7 * HOUR_HEIGHT));

        panel.revalidate();
        panel.repaint();
    }

    static int minutesOf(String time) {
        try {
            LocalTime t = LocalTime.parse(time);
            return t.getHour() * 60 + t.getMinute();
        } catch (Exception ex) {
            return 0;
        }
    }

    // Walks back to Sunday of whatever week this date is in
    static LocalDate sundayOf(LocalDate d) {
        return d.minusDays(d.getDayOfWeek().getValue() % 7);
    }

}