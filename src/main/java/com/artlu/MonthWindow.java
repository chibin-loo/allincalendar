package com.artlu;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MonthWindow {
    static LocalDate currentMonth = LocalDate.now();
    static JPanel panel = new JPanel(new BorderLayout());

    private static boolean built = false;
    private static List<Event> events = new ArrayList<>();
    private static final JPanel grid = new JPanel(new GridLayout(0, 7, 3, 3));
    private static CalendarUI.Sidebar sidebar;
    private static JSplitPane split;
    private static JLabel title;
    private static JButton sidebarToggle;

    static void build(List<Event> evts) {
        events = evts;
        if (!built) {
            chrome();
            built = true;
        }
        refresh();
    }

    private static void chrome() {
        title = new JLabel("", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));

        JPanel dayNames = new JPanel(new GridLayout(1, 7, 3, 3));
        for (String d : new String[] { "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat" }) {
            JLabel l = new JLabel(d, SwingConstants.CENTER);
            l.setFont(new Font("Segoe UI", Font.BOLD, 12));
            l.setForeground(new Color(90, 90, 90));
            dayNames.add(l);
        }

        JPanel calendarArea = new JPanel(new BorderLayout(0, 4));
        calendarArea.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 4));
        calendarArea.add(dayNames, BorderLayout.NORTH);
        calendarArea.add(grid, BorderLayout.CENTER);

        sidebar = new CalendarUI.Sidebar(picked -> {
            currentMonth = picked;
            refresh();
        });
        split = CalendarUI.split(calendarArea, sidebar);

        sidebarToggle = new JButton("Hide sidebar");
        sidebarToggle.addActionListener(e -> CalendarUI.toggleSidebar(split, sidebar, sidebarToggle));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        left.add(CalendarUI.nav("◀", () -> jump(currentMonth.minusMonths(1))));
        left.add(CalendarUI.nav("Today", () -> jump(LocalDate.now())));
        left.add(CalendarUI.nav("▶", () -> jump(currentMonth.plusMonths(1))));
        left.add(CalendarUI.nav("+ Task", () -> Window.newTaskAt(currentMonth, "")));
        left.add(CalendarUI.nav("+ Event", () -> Window.newEventOn(currentMonth)));

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
        currentMonth = d;
        refresh();
    }

    private static void refresh() {
        title.setText(CalendarUI_monthName() + " " + currentMonth.getYear());
        grid.removeAll();

        java.util.Map<String, List<Event>> grouped = Main.byDate(events);
        LocalDate first = currentMonth.withDayOfMonth(1);
        int blanks = first.getDayOfWeek().getValue() % 7;
        for (int i = 0; i < blanks; i++) {
            grid.add(blankCell());
        }
        for (int day = 1; day <= currentMonth.lengthOfMonth(); day++) {
            LocalDate d = currentMonth.withDayOfMonth(day);
            grid.add(new DayCell(d, grouped.getOrDefault(d.toString(), List.of())));
        }
        int cells = blanks + currentMonth.lengthOfMonth();
        while (cells % 7 != 0) {
            grid.add(blankCell());
            cells++;
        }

        sidebar.setMini(currentMonth);
        sidebar.setDeadlines(events, first, first.plusMonths(1).minusDays(1), "Due this month");

        panel.revalidate();
        panel.repaint();
    }

    private static String CalendarUI_monthName() {
        String m = currentMonth.getMonth().toString();
        return m.charAt(0) + m.substring(1).toLowerCase();
    }

    private static JPanel blankCell() {
        JPanel p = new JPanel();
        p.setBackground(new Color(248, 248, 248));
        p.setBorder(BorderFactory.createLineBorder(new Color(235, 235, 235)));
        return p;
    }

    /** One month cell: day number, as many chips as fit, then "+N more". */
    static class DayCell extends JPanel {
        private static final int ROW_H = 17;
        final LocalDate date;
        final JLabel number = new JLabel();
        final JLabel more = new JLabel();
        final List<JComponent> chips = new ArrayList<>();

        DayCell(LocalDate date, List<Event> dayEvents) {
            super(null);
            this.date = date;
            setBackground(date.equals(LocalDate.now()) ? new Color(232, 240, 254) : Color.WHITE);
            setBorder(BorderFactory.createLineBorder(new Color(224, 224, 224)));

            number.setText(String.valueOf(date.getDayOfMonth()));
            number.setFont(new Font("Segoe UI", Font.BOLD, 12));
            add(number);

            for (Event e : dayEvents) {
                JLabel c = CalendarUI.chip(e);
                chips.add(c);
                add(c);
            }

            more.setFont(CalendarUI.F11);
            more.setForeground(new Color(110, 110, 110));
            more.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent me) {
                    Window.goToDay(date);
                }
            });
            add(more);

            addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent me) {
                    if (me.getClickCount() == 2) {
                        Window.newTaskAt(date, "");
                    }
                }
            });
        }

        public void doLayout() {
            int w = getWidth();
            number.setBounds(4, 2, w - 8, 14);

            int y = 18;
            int avail = getHeight() - y - 2;
            int fit = Math.max(0, avail / ROW_H);
            int shown = chips.size() <= fit ? chips.size() : Math.max(0, fit - 1);

            for (int i = 0; i < chips.size(); i++) {
                JComponent c = chips.get(i);
                c.setVisible(i < shown);
                if (i < shown) {
                    c.setBounds(3, y, w - 6, ROW_H - 2);
                    y += ROW_H;
                }
            }
            if (shown < chips.size()) {
                more.setText("+" + (chips.size() - shown) + " more");
                more.setVisible(true);
                more.setBounds(5, y, w - 8, ROW_H - 2);
            } else {
                more.setVisible(false);
            }
        }

        public Dimension getPreferredSize() {
            return new Dimension(90, 96);
        }

        public Dimension getMinimumSize() {
            return new Dimension(60, 40);
        }
    }
}