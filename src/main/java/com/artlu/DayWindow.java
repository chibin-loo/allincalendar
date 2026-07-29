package com.artlu;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class DayWindow {
    static LocalDate currentDay = LocalDate.now();
    static JPanel panel = new JPanel(new BorderLayout());
    static final int HOUR_HEIGHT = 60;

    private static boolean built = false;
    private static List<Event> events = new ArrayList<>();
    private static CalendarUI.TimeGrid grid;
    private static CalendarUI.AllDayHeader header;
    private static CalendarUI.Sidebar sidebar;
    private static JSplitPane split;
    private static JScrollPane scroll;
    private static JLabel title;
    private static JButton sidebarToggle;
    private static boolean scrollPending = true;

    static void build(List<Event> evts) {
        events = evts;
        if (!built) {
            chrome();
            built = true;
        }
        refresh();
    }

    private static void chrome() {
        grid = new CalendarUI.TimeGrid(1, HOUR_HEIGHT);
        header = new CalendarUI.AllDayHeader(grid, 1);
        scroll = new JScrollPane(grid);
        scroll.setColumnHeaderView(header); // all-day strip, pinned above the grid
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        title = new JLabel("", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));

        sidebar = new CalendarUI.Sidebar(picked -> {
            currentDay = picked;
            scrollPending = true;
            refresh();
        });
        split = CalendarUI.split(scroll, sidebar);

        sidebarToggle = new JButton("Hide sidebar");
        sidebarToggle.addActionListener(e -> CalendarUI.toggleSidebar(split, sidebar, sidebarToggle));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        left.add(CalendarUI.nav("◀", () -> jump(currentDay.minusDays(1))));
        left.add(CalendarUI.nav("Today", () -> jump(LocalDate.now())));
        left.add(CalendarUI.nav("▶", () -> jump(currentDay.plusDays(1))));
        left.add(CalendarUI.nav("+ Task", () -> Window.newTaskAt(currentDay, "")));
        left.add(CalendarUI.nav("+ Event", () -> Window.newEventOn(currentDay)));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        right.add(sidebarToggle);

        JPanel header = new JPanel(new BorderLayout());
        header.add(left, BorderLayout.WEST);
        header.add(title, BorderLayout.CENTER);
        header.add(right, BorderLayout.EAST);
        header.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        panel.add(header, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
    }

    private static void jump(LocalDate d) {
        currentDay = d;
        scrollPending = true;
        refresh();
    }

    private static void refresh() {
        title.setText(currentDay.getDayOfWeek() + ", " + currentDay);
        grid.setEvents(currentDay, events);
        header.setEvents(currentDay, events);
        sidebar.setMini(currentDay);
        sidebar.setDeadlines(events, currentDay, currentDay, "Due this day");
        if (scrollPending) {
            scrollPending = false;
            SwingUtilities.invokeLater(
                    () -> scroll.getVerticalScrollBar().setValue(7 * HOUR_HEIGHT));
        }
        panel.revalidate();
        panel.repaint();
    }
}