package com.artlu;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class WeekWindow {
    static LocalDate weekStart = sundayOf(LocalDate.now());
    static JPanel panel = new JPanel(new BorderLayout());
    static final int HOUR_HEIGHT = 70;

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
        grid = new CalendarUI.TimeGrid(7, HOUR_HEIGHT);
        header = new CalendarUI.AllDayHeader(grid, 7);
        scroll = new JScrollPane(grid);
        scroll.setColumnHeaderView(header);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        title = new JLabel("", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));

        sidebar = new CalendarUI.Sidebar(picked -> {
            weekStart = sundayOf(picked);
            scrollPending = true;
            refresh();
        });
        split = CalendarUI.split(scroll, sidebar);

        sidebarToggle = new JButton("Hide sidebar");
        sidebarToggle.addActionListener(e -> CalendarUI.toggleSidebar(split, sidebar, sidebarToggle));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        left.add(CalendarUI.nav("◀", () -> jump(weekStart.minusWeeks(1))));
        left.add(CalendarUI.nav("Today", () -> jump(LocalDate.now())));
        left.add(CalendarUI.nav("▶", () -> jump(weekStart.plusWeeks(1))));
        left.add(CalendarUI.nav("+ Task", () -> Window.newTaskAt(focusDay(), "")));
        left.add(CalendarUI.nav("+ Event", () -> Window.newEventOn(focusDay())));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        right.add(sidebarToggle);

        JPanel bar = new JPanel(new BorderLayout());
        bar.add(left, BorderLayout.WEST);
        bar.add(title, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
        bar.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        panel.add(bar, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
    }

    private static void jump(LocalDate d) {
        weekStart = sundayOf(d);
        scrollPending = true;
        refresh();
    }

    private static void refresh() {
        title.setText(weekStart + "  to  " + weekStart.plusDays(6));
        grid.setEvents(weekStart, events);
        header.setEvents(weekStart, events);
        sidebar.setMini(weekStart);
        sidebar.setDeadlines(events, weekStart, weekStart.plusDays(6), "Due this week");
        if (scrollPending) {
            scrollPending = false;
            SwingUtilities.invokeLater(
                    () -> scroll.getVerticalScrollBar().setValue(7 * HOUR_HEIGHT));
        }
        panel.revalidate();
        panel.repaint();
    }

    static LocalDate sundayOf(LocalDate d) {
        return d.minusDays(d.getDayOfWeek().getValue() % 7);
    }

    // Which day the + buttons should target. The week view shows seven days, so
    // "add" is ambiguous: pick today when today is on screen, otherwise the
    // Sunday of whatever week you've navigated to.
    private static LocalDate focusDay() {
        LocalDate today = LocalDate.now();
        boolean onScreen = !today.isBefore(weekStart) && !today.isAfter(weekStart.plusDays(6));
        return onScreen ? today : weekStart;
    }
}