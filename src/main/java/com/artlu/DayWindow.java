package com.artlu;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class DayWindow {
    static LocalDate currentDay = LocalDate.now();
    static JPanel panel = new JPanel(new BorderLayout());
    static JTextArea detailsArea = new JTextArea();

    static final int HOUR_HEIGHT = 60; // pixels per hour
    static final int LABEL_WIDTH = 60; // width of the "8:00" column

    static void build(List<Event> events) {
        panel.removeAll();

        // Header with prev/today/next and the date
        JPanel header = new JPanel(new BorderLayout());
        JButton prev = new JButton("◀");
        JButton next = new JButton("▶");
        JButton today = new JButton("Today");
        JLabel title = new JLabel(currentDay.getDayOfWeek() + ", " + currentDay,
                SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));

        prev.addActionListener(e -> {
            currentDay = currentDay.minusDays(1);
            build(events);
        });
        next.addActionListener(e -> {
            currentDay = currentDay.plusDays(1);
            build(events);
        });
        today.addActionListener(e -> {
            currentDay = LocalDate.now();
            build(events);
        });

        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        leftButtons.add(prev);
        leftButtons.add(today);
        JButton addHere = new JButton("+ Task");
        addHere.addActionListener(e -> {
            Event t = TaskDialog.open(null, currentDay);
            if (t != null) {
                try {
                    Main.saveNewTask(t);
                    Window.currentEvents.add(t);
                    Window.redrawAll();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
        leftButtons.add(addHere);

        header.add(leftButtons, BorderLayout.WEST);
        header.add(title, BorderLayout.CENTER);
        header.add(next, BorderLayout.EAST);
        header.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(header, BorderLayout.NORTH);

        // The timed grid — null layout means WE place things by pixel
        JPanel grid = new JPanel(null);
        grid.setBackground(Color.WHITE);
        grid.setPreferredSize(new Dimension(700, 24 * HOUR_HEIGHT));

        // Hour lines and labels
        for (int hour = 0; hour < 24; hour++) {
            int y = hour * HOUR_HEIGHT;

            JLabel hourLabel = new JLabel(String.format("%02d:00", hour));
            hourLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            hourLabel.setForeground(new Color(120, 120, 120));
            hourLabel.setBounds(5, y, LABEL_WIDTH - 10, 15);
            grid.add(hourLabel);

            JPanel line = new JPanel();
            line.setBackground(new Color(230, 230, 230));
            line.setBounds(LABEL_WIDTH, y, 640, 1);
            grid.add(line);
        }

        // Place each event as a block
        String iso = currentDay.toString();
        for (Event e : events) {
            if (!e.date.equals(iso) || e.time.isBlank()) {
                continue; // not today, or has no time (handled later)
            }
            int startMin = Main.minutesOf(e.time);
            if (startMin < 0) {
                continue; // can't place it, skip
            }
            int endMin = e.endTime.isBlank() ? startMin + 30 : Main.minutesOf(e.endTime);
            if (endMin <= startMin) {
                endMin = startMin + 30; // covers -1 too, since -1 < startMin
            }
            int y = startMin * HOUR_HEIGHT / 60;
            int height = (endMin - startMin) * HOUR_HEIGHT / 60;

            String timeText = e.time + " - " + (e.endTime.isBlank() ? "" : e.endTime);
            String nameText = e.isDone() ? e.name + "  [DONE]" : e.name;
            JLabel block = new JLabel("<html><b>" + nameText + "</b><br>" + timeText + "</html>");
            block.setOpaque(true);
            block.setBackground(e.isDone() ? new Color(190, 190, 190) : Main.colorFor(e));
            block.setForeground(Color.WHITE);
            block.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            block.setVerticalAlignment(SwingConstants.TOP);
            block.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            block.setBounds(LABEL_WIDTH + 5, y, 630, Math.max(height - 3, 18));

            // Clicking a block shows its details on the right
            final Event clicked = e;
            block.addMouseListener(new java.awt.event.MouseAdapter() {
                public void mouseClicked(java.awt.event.MouseEvent me) {
                    showDetails(clicked);
                }
            });

            grid.add(block);
            grid.setComponentZOrder(block, 0);
        }

        JScrollPane scroll = new JScrollPane(grid);
        scroll.setPreferredSize(new Dimension(720, 400));
        panel.add(scroll, BorderLayout.CENTER);

        DefaultListModel<String> allDayModel = new DefaultListModel<>();
        for (Event e : events) {
            if (e.date.equals(iso) && e.time.isBlank()) {
                allDayModel.addElement(e.isDone() ? e.name + "  [DONE]" : e.name);
            }
        }

        if (allDayModel.isEmpty()) {
            allDayModel.addElement("(nothing)");
        }
        JList<String> allDayList = new JList<>(allDayModel);
        allDayList.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        allDayList.setFixedCellHeight(28);

        // Clicking an all-day item shows its details
        final List<Event> allDayEvents = new java.util.ArrayList<>();
        for (Event e : events) {
            if (e.date.equals(iso) && e.time.isBlank()) {
                allDayEvents.add(e);
            }
        }
        allDayList.addListSelectionListener(selectionEvent -> {
            int row = allDayList.getSelectedIndex();
            if (row >= 0 && row < allDayEvents.size()) {
                showDetails(allDayEvents.get(row));
            }
        });

        JScrollPane allDayScroll = new JScrollPane(allDayList);
        allDayScroll.setBorder(BorderFactory.createTitledBorder("All day / no time"));
        allDayScroll.setPreferredSize(new Dimension(420, 180));

        detailsArea.setEditable(false);
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);
        detailsArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        detailsArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane detailsScroll = new JScrollPane(detailsArea);
        detailsScroll.setBorder(BorderFactory.createTitledBorder("Details"));

        JPanel miniCal = MiniCalendar.create(currentDay, picked -> {
            currentDay = picked;
            build(events);
        });
        miniCal.setBorder(BorderFactory.createTitledBorder("Jump to"));

        JPanel side = new JPanel(new BorderLayout(0, 8));
        side.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));

        JPanel sideTop = new JPanel(new BorderLayout(0, 8));
        sideTop.add(miniCal, BorderLayout.NORTH);
        sideTop.add(allDayScroll, BorderLayout.CENTER);

        side.add(sideTop, BorderLayout.NORTH);
        side.add(detailsScroll, BorderLayout.CENTER);
        side.setPreferredSize(new Dimension(430, 400));
        panel.add(side, BorderLayout.EAST);
        panel.add(side, BorderLayout.EAST);

        // Scroll to 7am once the panel is laid out
        SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(7 * HOUR_HEIGHT));

        panel.revalidate();
        panel.repaint();
    }

    static void showDetails(Event e) {
        StringBuilder text = new StringBuilder();
        text.append(e.name).append("\n\n");
        text.append(e.date).append("  ").append(e.time);
        if (!e.endTime.isBlank()) {
            text.append(" - ").append(e.endTime);
        }
        text.append("\n");
        if (e.isDone()) {
            text.append("[done]\n");
        }
        if (!e.url.isBlank()) {
            text.append("\n").append(e.url).append("\n");
        }
        if (!e.description.isBlank()) {
            text.append("\n").append(e.description);
        }
        detailsArea.setText(text.toString());
        detailsArea.setCaretPosition(0);
    }

}