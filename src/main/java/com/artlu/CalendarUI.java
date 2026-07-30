package com.artlu;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Shared widgets and helpers for the Day, Week and Month views. */
public class CalendarUI {

    static final int LABEL_WIDTH = 56;
    static final int MIN_COL = 90;
    static final Color LINE = new Color(230, 230, 230);
    static final Color DONE_BG = new Color(190, 190, 190);
    static final Font F11 = new Font("Segoe UI", Font.PLAIN, 11);

    // ---------- selection: one selected event, everyone listens ----------

    static Event selected;
    private static final List<Consumer<Event>> listeners = new ArrayList<>();

    static void onSelection(Consumer<Event> l) {
        listeners.add(l);
    }

    static void select(Event e) {
        selected = e;
        for (Consumer<Event> l : listeners) {
            l.accept(e);
        }
    }

    // ---------- small helpers ----------

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static Color lighten(Color c) {
        return new Color(Math.min(255, c.getRed() + 55),
                Math.min(255, c.getGreen() + 55),
                Math.min(255, c.getBlue() + 55));
    }

    static Color bgFor(Event e) {
        if (e.isDone()) {
            return DONE_BG;
        }
        Color base = Main.colorFor(e);
        return e.kind.equals("work") ? lighten(base) : base;
    }

    static boolean isDate(String s) {
        try {
            LocalDate.parse(s);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    static String fmtMin(int min) {
        min = Math.max(0, Math.min(1439, min));
        return String.format("%02d:%02d", min / 60, min % 60);
    }

    static int snap15(int min) {
        return Math.round(min / 15f) * 15; // round to the nearest quarter hour
    }

    static boolean draggable(Event e) {
        if (e.userAdded && e.sourceTask == null) {
            return true; // your own task
        }
        if (e.kind.equals("work") && e.sourceTask != null) {
            return true; // an auto or pinned work block — dragging pins it
        }
        return false;
    }

    static String detailsText(Event e) {
        if (e == null) {
            return "";
        }
        StringBuilder t = new StringBuilder();
        t.append(e.name).append("\n\n");
        t.append(e.date);
        if (!e.time.isBlank()) {
            t.append("  ").append(e.time);
        }
        if (!e.endTime.isBlank()) {
            t.append(" - ").append(e.endTime);
        }
        t.append("\n");
        if (e.sourceTask != null) {
            t.append("work block for: ").append(e.sourceTask.name).append("\n");
        }
        if (e.isDone()) {
            t.append("[done]\n");
        }
        if (e.durationMin > 0) {
            t.append("estimated ").append(e.durationMin).append(" min\n");
        }
        if (!e.url.isBlank()) {
            t.append("\n").append(e.url).append("\n");
        }
        if (!e.description.isBlank()) {
            t.append("\n").append(e.description);
        }
        return t.toString();
    }

    static void openLink(Event e) {
        if (e.url.isBlank()) {
            return;
        }
        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI(e.url));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // ---------- right-click menu: this is where "toggle done" lives ----------

    static JPopupMenu menuFor(Event e) {
        Event target = e.sourceTask != null ? e.sourceTask : e;
        JPopupMenu menu = new JPopupMenu();

        JMenuItem done = new JMenuItem(target.isDone() ? "Mark not done" : "Mark done");
        done.addActionListener(a -> Window.toggleDone(e));
        menu.add(done);

        JMenuItem open = new JMenuItem("Open link");
        open.setEnabled(!e.url.isBlank());
        open.addActionListener(a -> openLink(e));
        menu.add(open);

        JMenuItem goDay = new JMenuItem("Show this day");
        goDay.setEnabled(isDate(e.date));
        goDay.addActionListener(a -> Window.goToDay(LocalDate.parse(e.date)));
        menu.add(goDay);

        if (target.userAdded) {
            menu.addSeparator();
            JMenuItem edit = new JMenuItem("Edit…");
            edit.addActionListener(a -> Window.editEvent(target));
            menu.add(edit);

            JMenuItem del = new JMenuItem(target.kind.equals("event") ? "Delete event" : "Delete task");
            del.addActionListener(a -> Window.deleteEvent(target));
            menu.add(del);
        }
        return menu;
    }

    /** Click = select, double-click = open link, right-click = menu. */
    static void attach(JComponent c, Event e) {
        c.setToolTipText("<html>" + esc(e.name) + "<br>"
                + e.date + (e.time.isBlank() ? "" : " " + e.time) + "</html>");
        c.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent me) {
                popup(me);
            }

            public void mouseReleased(MouseEvent me) {
                popup(me);
            }

            public void mouseClicked(MouseEvent me) {
                if (me.isPopupTrigger()) {
                    return;
                }
                select(e);
                if (me.getClickCount() == 2) {
                    // your own item opens for editing; an imported one opens its link
                    Event target = e.sourceTask != null ? e.sourceTask : e;
                    if (target.userAdded) {
                        Window.editEvent(target);
                    } else {
                        openLink(e);
                    }
                }
            }

            private void popup(MouseEvent me) {
                if (me.isPopupTrigger()) {
                    select(e);
                    menuFor(e).show(c, me.getX(), me.getY());
                }
            }
        });
    }

    // ---------- chips (month cells, all-day row) ----------

    static JLabel chip(Event e) {
        JLabel c = new JLabel(e.name);
        c.setOpaque(true);
        c.setFont(F11);
        c.setForeground(Color.WHITE);
        c.setBackground(bgFor(e));
        c.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 2));
        attach(c, e);
        return c;
    }

    static JLabel block(Event e, boolean tall) {
        String name = esc(e.name);
        if (e.isDone()) {
            name = "<s>" + name + "</s>";
        }
        String time = e.time + (e.endTime.isBlank() ? "" : " - " + e.endTime);
        String html = tall
                ? "<html><b>" + name + "</b><br>" + time + "</html>"
                : "<html><b>" + name + "</b></html>";

        JLabel b = new JLabel(html);
        b.setOpaque(true);
        b.setFont(F11);
        b.setForeground(Color.WHITE);
        b.setBackground(bgFor(e));
        b.setVerticalAlignment(SwingConstants.TOP);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, bgFor(e).darker()),
                BorderFactory.createEmptyBorder(1, 4, 1, 3)));
        attach(b, e);
        return b;
    }

    // ---------- overlap handling ----------

    static class Slot {
        Event event;
        int startMin, endMin;
        int dayIndex;
        int lane = 0, lanes = 1;
        JComponent comp;
    }

    /** Greedy lane assignment within each cluster of overlapping events. */
    static void assignLanes(List<Slot> daySlots) {
        daySlots.sort((a, b) -> a.startMin != b.startMin
                ? Integer.compare(a.startMin, b.startMin)
                : Integer.compare(b.endMin, a.endMin));

        List<Slot> cluster = new ArrayList<>();
        int clusterEnd = Integer.MIN_VALUE;
        for (Slot s : daySlots) {
            if (!cluster.isEmpty() && s.startMin >= clusterEnd) {
                closeCluster(cluster);
                cluster = new ArrayList<>();
                clusterEnd = Integer.MIN_VALUE;
            }
            cluster.add(s);
            clusterEnd = Math.max(clusterEnd, s.endMin);
        }
        closeCluster(cluster);
    }

    private static void closeCluster(List<Slot> cluster) {
        if (cluster.isEmpty()) {
            return;
        }
        List<Integer> laneEnds = new ArrayList<>();
        for (Slot s : cluster) {
            int lane = -1;
            for (int i = 0; i < laneEnds.size(); i++) {
                if (laneEnds.get(i) <= s.startMin) {
                    lane = i;
                    break;
                }
            }
            if (lane < 0) {
                laneEnds.add(s.endMin);
                lane = laneEnds.size() - 1;
            } else {
                laneEnds.set(lane, s.endMin);
            }
            s.lane = lane;
        }
        for (Slot s : cluster) {
            s.lanes = laneEnds.size();
        }
    }

    // ---------- the timed grid ----------

    static class TimeGrid extends JPanel implements Scrollable {
        final int days;
        final int hourHeight;
        LocalDate start = LocalDate.now();
        final List<Slot> slots = new ArrayList<>();
        final JLabel[] hourLabels = new JLabel[24];
        AllDayHeader header;

        // The preview box drawn while you drag out a new event. -1 = not dragging.
        int ghostDay = -1;
        int ghostStart, ghostEnd;

        TimeGrid(int days, int hourHeight) {
            super(null);
            this.days = days;
            this.hourHeight = hourHeight;
            setBackground(Color.WHITE);
            for (int h = 0; h < 24; h++) {
                JLabel l = new JLabel(String.format("%02d:00", h));
                l.setFont(F11);
                l.setForeground(new Color(130, 130, 130));
                add(l);
                hourLabels[h] = l;
            }
            // On empty space: double-click makes a task, dragging makes an event.
            CreateDrag creator = new CreateDrag();
            addMouseListener(creator);
            addMouseMotionListener(creator); // dragging arrives on this one
        }

        // Which day column / which minute of the day a pixel lands on.
        int dayAt(int x) {
            return Math.max(0, Math.min(days - 1, (x - LABEL_WIDTH) / colWidth()));
        }

        int minuteAt(int y) {
            return Math.max(0, Math.min(1440, y * 60 / hourHeight));
        }

        int colWidth() {
            return Math.max(MIN_COL, (getWidth() - LABEL_WIDTH) / days);
        }

        void setEvents(LocalDate start, List<Event> events) {
            this.start = start;
            for (Slot s : slots) {
                remove(s.comp);
            }
            slots.clear();

            for (int d = 0; d < days; d++) {
                String iso = start.plusDays(d).toString();
                List<Slot> daySlots = new ArrayList<>();
                for (Event e : events) {
                    if (!e.date.equals(iso) || e.time.isBlank()) {
                        continue;
                    }
                    int s0 = Main.minutesOf(e.time);
                    if (s0 < 0) {
                        continue;
                    }
                    int s1 = e.endTime.isBlank() ? s0 + 30 : Main.minutesOf(e.endTime);
                    if (s1 <= s0) {
                        s1 = s0 + 30;
                    }
                    Slot slot = new Slot();
                    slot.event = e;
                    slot.startMin = s0;
                    slot.endMin = s1;
                    slot.dayIndex = d;
                    daySlots.add(slot);
                }
                assignLanes(daySlots);
                slots.addAll(daySlots);
            }

            for (Slot s : slots) {
                boolean tall = (s.endMin - s.startMin) * hourHeight / 60 >= 32;
                s.comp = block(s.event, tall);
                attachDrag(s);
                add(s.comp);
            }
            revalidate();
            repaint();
        }

        public void doLayout() {
            int cw = colWidth();
            for (int h = 0; h < 24; h++) {
                hourLabels[h].setBounds(4, h * hourHeight + 2, LABEL_WIDTH - 8, 14);
            }
            for (Slot s : slots) {
                int y = s.startMin * hourHeight / 60;
                int h = Math.max((s.endMin - s.startMin) * hourHeight / 60 - 2, 15);
                int colX = LABEL_WIDTH + s.dayIndex * cw;
                int laneW = Math.max(18, (cw - 4) / s.lanes);
                // last lane takes the rounding slack
                int w = (s.lane == s.lanes - 1) ? (cw - 4 - s.lane * laneW) : laneW;
                s.comp.setBounds(colX + 2 + s.lane * laneW, y, Math.max(w - 1, 18), h);
            }
            if (header != null && getWidth() > 0 && header.getWidth() != getWidth()) {
                header.setSize(getWidth(), header.getPreferredSize().height);
                header.doLayout();
                header.repaint();
            }
        }

        // Wire up drag-to-move for one block. No-op for anything not draggable.
        private void attachDrag(Slot s) {
            if (!draggable(s.event)) {
                return;
            }
            BlockDrag h = new BlockDrag(s);
            s.comp.addMouseListener(h);
            s.comp.addMouseMotionListener(h); // needed for mouseDragged to fire
            s.comp.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        }

        // Position one block live while it's being dragged — full column width,
        // ignoring lane/overlap math. The proper lane layout comes back on the
        // redraw after release.
        private void placeDuringDrag(Slot s) {
            int cw = colWidth();
            int y = s.startMin * hourHeight / 60;
            int h = Math.max((s.endMin - s.startMin) * hourHeight / 60 - 2, 15);
            int x = LABEL_WIDTH + s.dayIndex * cw;
            s.comp.setBounds(x + 2, y, cw - 5, h);
        }

        // Drag across empty grid to draw out a new event: press sets one edge of
        // the range, the cursor sets the other, release opens the form with both
        // times filled in. Nothing is created until you let go.
        private class CreateDrag extends MouseAdapter {
            int pressDay, pressMin;
            boolean armed, dragging;

            public void mousePressed(MouseEvent me) {
                armed = false;
                dragging = false;
                if (!SwingUtilities.isLeftMouseButton(me) || me.getX() < LABEL_WIDTH) {
                    return; // right-click, or the hour-label gutter
                }
                pressDay = dayAt(me.getX());
                pressMin = snap15(minuteAt(me.getY()));
                armed = true;
            }

            public void mouseDragged(MouseEvent me) {
                if (!armed) {
                    return;
                }
                int now = snap15(minuteAt(me.getY()));
                if (!dragging) {
                    if (Math.abs(now - pressMin) < 15) {
                        return; // hasn't crossed a quarter-hour yet — not a drag
                    }
                    dragging = true;
                }
                // Either end can be the one you grabbed, so sort them.
                ghostDay = pressDay;
                ghostStart = Math.min(pressMin, now);
                ghostEnd = Math.max(pressMin, now);
                repaint();
            }

            public void mouseReleased(MouseEvent me) {
                armed = false;
                if (!dragging) {
                    return; // it was really just a click
                }
                dragging = false;

                int day = ghostDay, from = ghostStart, to = ghostEnd;
                ghostDay = -1; // clear the preview before the dialog opens
                repaint();

                if (to - from >= 15) {
                    Window.newEventAt(start.plusDays(day), fmtMin(from), fmtMin(to));
                }
            }

            // Double-click still makes a task at that spot, same as before.
            public void mouseClicked(MouseEvent me) {
                if (me.getClickCount() != 2 || me.getX() < LABEL_WIDTH) {
                    return;
                }
                int minutes = (minuteAt(me.getY()) / 15) * 15;
                Window.newTaskAt(start.plusDays(dayAt(me.getX())), fmtMin(minutes));
            }
        }

        // press -> remember where we grabbed; drag -> follow the cursor;
        // release -> write the new time back. A plain click (no real movement)
        // falls through so the existing select/right-click menu still works.
        private class BlockDrag extends MouseAdapter {
            final Slot slot;
            int pressScreenX, pressScreenY; // where we pressed, in absolute screen pixels
            int origStart, dur, origDay; // the block's state at press time
            boolean origEndBlank; // did this task have no end time to start with?
            boolean dragging;
            boolean armed; // true once a valid left-press has happened
            boolean resizing; // grabbed the bottom edge, so only the end moves

            BlockDrag(Slot slot) {
                this.slot = slot;
            }

            // The bottom few pixels are the resize handle — but never more than a
            // third of a short block, or there'd be nothing left to grab to move.
            private boolean onBottomEdge(MouseEvent e) {
                int h = slot.comp.getHeight();
                return e.getY() >= h - Math.min(6, Math.max(3, h / 3));
            }

            // Swap the cursor as you pass over the handle, so the edge is findable.
            public void mouseMoved(MouseEvent e) {
                slot.comp.setCursor(Cursor.getPredefinedCursor(
                        onBottomEdge(e) ? Cursor.S_RESIZE_CURSOR : Cursor.MOVE_CURSOR));
            }

            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    armed = false;
                    return; // let right-click reach the popup menu
                }
                pressScreenX = e.getXOnScreen();
                pressScreenY = e.getYOnScreen();
                origStart = slot.startMin;
                dur = slot.endMin - slot.startMin;
                origDay = slot.dayIndex;
                origEndBlank = slot.event.endTime.isBlank();
                resizing = onBottomEdge(e);
                dragging = false;
                armed = true;
            }

            public void mouseDragged(MouseEvent e) {
                if (!armed) {
                    return;
                }
                int dy = e.getYOnScreen() - pressScreenY; // pixels moved since press
                int dx = e.getXOnScreen() - pressScreenX;
                if (!dragging) {
                    if (Math.abs(dy) < 4 && Math.abs(dx) < 4) {
                        return; // tiny wobble — not a real drag yet
                    }
                    dragging = true;
                    select(slot.event);
                    setComponentZOrder(slot.comp, 0); // draw on top while moving
                }
                if (resizing) {
                    // only the bottom moves; 15 min is the smallest block allowed
                    int newEnd = snap15(origStart + dur + dy * 60 / hourHeight);
                    slot.endMin = Math.max(origStart + 15, Math.min(newEnd, 1440));
                    placeDuringDrag(slot);
                    repaint();
                    return;
                }
                int newStart = snap15(origStart + dy * 60 / hourHeight);
                newStart = Math.max(0, Math.min(newStart, 1440 - dur));
                int newDay = origDay + Math.round((float) dx / colWidth());
                newDay = Math.max(0, Math.min(newDay, days - 1));
                slot.startMin = newStart;
                slot.endMin = newStart + dur;
                slot.dayIndex = newDay;
                placeDuringDrag(slot);
                repaint();
            }

            public void mouseReleased(MouseEvent e) {
                armed = false;
                if (!dragging) {
                    return; // it was really just a click
                }
                dragging = false;
                LocalDate newDate = start.plusDays(slot.dayIndex);
                Event ev = slot.event;

                if (ev.kind.equals("work")) {
                    // Pin it: forget the spot we grabbed it from, remember the drop.
                    LocalDate oldDate = start.plusDays(origDay);
                    Window.repinWork(ev.sourceTask, oldDate, origStart,
                            newDate, slot.startMin, slot.endMin);
                } else {
                    ev.date = newDate.toString();
                    ev.endDate = newDate.toString();
                    ev.time = fmtMin(slot.startMin);
                    // Moving never invents an end time, but resizing is a request
                    // for one — that's how a bare deadline gets a length.
                    if (resizing || !origEndBlank) {
                        ev.endTime = fmtMin(slot.endMin);
                    }
                    Window.commitEventEdit(ev);
                }
                resizing = false;
            }
        }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int cw = colWidth();
            int right = LABEL_WIDTH + days * cw;

            int workStart = Math.max(0, Main.minutesOf(Settings.get("day_start", "08:00")));
            int workEnd = Math.max(0, Main.minutesOf(Settings.get("day_end", "22:00")));
            g.setColor(new Color(249, 249, 251));
            g.fillRect(LABEL_WIDTH, 0, right - LABEL_WIDTH, workStart * hourHeight / 60);
            int endY = workEnd * hourHeight / 60;
            g.fillRect(LABEL_WIDTH, endY, right - LABEL_WIDTH, 24 * hourHeight - endY);

            g.setColor(LINE);
            for (int h = 0; h <= 24; h++) {
                g.drawLine(LABEL_WIDTH, h * hourHeight, right, h * hourHeight);
            }
            for (int d = 0; d <= days; d++) {
                int x = LABEL_WIDTH + d * cw;
                g.drawLine(x, 0, x, 24 * hourHeight);
            }

            // the block being dragged out right now, if any
            if (ghostDay >= 0 && ghostEnd > ghostStart) {
                int gx = LABEL_WIDTH + ghostDay * cw + 2;
                int gy = ghostStart * hourHeight / 60;
                int gh = (ghostEnd - ghostStart) * hourHeight / 60;
                g.setColor(new Color(90, 130, 220, 70)); // 70 = mostly see-through
                g.fillRect(gx, gy, cw - 5, gh);
                g.setColor(new Color(90, 130, 220));
                g.drawRect(gx, gy, cw - 5, gh);
                g.setFont(F11);
                g.drawString(fmtMin(ghostStart) + " - " + fmtMin(ghostEnd), gx + 4, gy + 13);
            }

            int idx = (int) ChronoUnit.DAYS.between(start, LocalDate.now());
            if (idx >= 0 && idx < days) {
                LocalTime now = LocalTime.now();
                int y = (now.getHour() * 60 + now.getMinute()) * hourHeight / 60;
                g.setColor(new Color(220, 80, 80));
                g.fillOval(LABEL_WIDTH + idx * cw - 3, y - 3, 7, 7);
                g.drawLine(LABEL_WIDTH + idx * cw, y, LABEL_WIDTH + (idx + 1) * cw, y);
            }
        }

        public Dimension getPreferredSize() {
            return new Dimension(LABEL_WIDTH + days * MIN_COL, 24 * hourHeight);
        }

        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        public int getScrollableUnitIncrement(Rectangle r, int orient, int dir) {
            return hourHeight / 4;
        }

        public int getScrollableBlockIncrement(Rectangle r, int orient, int dir) {
            return hourHeight * 3;
        }

        public boolean getScrollableTracksViewportWidth() {
            Container p = getParent();
            return p instanceof JViewport && p.getWidth() >= getPreferredSize().width;
        }

        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    // ---------- the all-day / deadline row above the week grid ----------

    static class AllDayHeader extends JPanel {
        final TimeGrid grid;
        final int days;
        LocalDate start = LocalDate.now();
        final List<JLabel> names = new ArrayList<>();
        final List<JLabel> mores = new ArrayList<>();
        final List<List<JComponent>> chips = new ArrayList<>();
        int rows = 1;

        AllDayHeader(TimeGrid grid, int days) {
            super(null);
            this.grid = grid;
            this.days = days;
            grid.header = this;
            setBackground(Color.WHITE);
            for (int i = 0; i < days; i++) {
                JLabel n = new JLabel("", SwingConstants.CENTER);
                n.setFont(new Font("Segoe UI", Font.BOLD, 12));
                add(n);
                names.add(n);

                JLabel m = new JLabel("");
                m.setFont(F11);
                m.setForeground(new Color(110, 110, 110));
                final int day = i;
                m.addMouseListener(new MouseAdapter() {
                    public void mouseClicked(MouseEvent me) {
                        Window.goToDay(start.plusDays(day));
                    }
                });
                add(m);
                mores.add(m);

                chips.add(new ArrayList<>());
            }
        }

        void setEvents(LocalDate start, List<Event> events) {
            this.start = start;
            for (List<JComponent> list : chips) {
                for (JComponent c : list) {
                    remove(c);
                }
                list.clear();
            }
            int max = 1;
            for (int i = 0; i < days; i++) {
                LocalDate d = start.plusDays(i);
                names.get(i).setText(d.getDayOfWeek().toString().substring(0, 3) + " " + d.getDayOfMonth());
                names.get(i).setForeground(d.equals(LocalDate.now())
                        ? new Color(50, 90, 190)
                        : Color.DARK_GRAY);

                String iso = d.toString();
                for (Event e : events) {
                    if (!e.date.equals(iso) || !e.time.isBlank()) {
                        continue;
                    }
                    JLabel c = chip(e);
                    chips.get(i).add(c);
                    add(c);
                }
                max = Math.max(max, chips.get(i).size());
            }
            rows = Math.min(max, 3);
            revalidate();
            repaint();
        }

        public void doLayout() {
            int cw = grid.colWidth();
            for (int i = 0; i < days; i++) {
                int x = LABEL_WIDTH + i * cw;
                names.get(i).setBounds(x, 4, cw, 18);

                List<JComponent> list = chips.get(i);
                int shown = list.size() <= rows ? list.size() : Math.max(0, rows - 1);
                int y = 24;
                for (int j = 0; j < list.size(); j++) {
                    JComponent c = list.get(j);
                    c.setVisible(j < shown);
                    if (j < shown) {
                        c.setBounds(x + 2, y, cw - 5, 18);
                        y += 20;
                    }
                }
                JLabel more = mores.get(i);
                if (shown < list.size()) {
                    more.setText("  +" + (list.size() - shown) + " more");
                    more.setVisible(true);
                    more.setBounds(x + 2, y, cw - 5, 18);
                } else {
                    more.setVisible(false);
                }
            }
        }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int cw = grid.colWidth();
            g.setColor(LINE);
            for (int d = 0; d <= days; d++) {
                int x = LABEL_WIDTH + d * cw;
                g.drawLine(x, 0, x, getHeight());
            }
            g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
        }

        public Dimension getPreferredSize() {
            int w = grid.getWidth() > 0 ? grid.getWidth() : grid.getPreferredSize().width;
            return new Dimension(w, 28 + rows * 20);
        }
    }

    // ---------- sidebar ----------

    static class Sidebar extends JPanel {
        private final JPanel miniHolder = new JPanel(new BorderLayout());
        private final DefaultListModel<String> dueModel = new DefaultListModel<>();
        private final JList<String> dueList = new JList<>(dueModel);
        private final List<Event> dueEvents = new ArrayList<>();
        private final JTextArea details = new JTextArea();
        private final JScrollPane dueScroll;
        private final Consumer<LocalDate> onPick;
        int lastWidth = 300;

        Sidebar(Consumer<LocalDate> onPick) {
            super(new BorderLayout(0, 8));
            this.onPick = onPick;
            setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));

            dueList.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            dueList.setFixedCellHeight(24);
            dueList.addListSelectionListener(ev -> {
                int i = dueList.getSelectedIndex();
                if (i >= 0 && i < dueEvents.size()) {
                    show(dueEvents.get(i));
                }
            });
            dueList.addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent me) {
                    popup(me);
                }

                public void mouseReleased(MouseEvent me) {
                    popup(me);
                }

                private void popup(MouseEvent me) {
                    if (!me.isPopupTrigger()) {
                        return;
                    }
                    int i = dueList.locationToIndex(me.getPoint());
                    if (i < 0 || i >= dueEvents.size()) {
                        return;
                    }
                    dueList.setSelectedIndex(i);
                    menuFor(dueEvents.get(i)).show(dueList, me.getX(), me.getY());
                }
            });

            dueScroll = new JScrollPane(dueList);
            dueScroll.setBorder(BorderFactory.createTitledBorder("Due"));
            dueScroll.setPreferredSize(new Dimension(280, 170));

            details.setEditable(false);
            details.setLineWrap(true);
            details.setWrapStyleWord(true);
            details.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            details.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
            JScrollPane detailsScroll = new JScrollPane(details);
            detailsScroll.setBorder(BorderFactory.createTitledBorder("Details"));

            JPanel top = new JPanel(new BorderLayout(0, 8));
            top.add(miniHolder, BorderLayout.NORTH);
            top.add(dueScroll, BorderLayout.CENTER);

            add(top, BorderLayout.NORTH);
            add(detailsScroll, BorderLayout.CENTER);
            setPreferredSize(new Dimension(300, 400));
            setMinimumSize(new Dimension(220, 200));

            onSelection(this::show);
        }

        void show(Event e) {
            details.setText(detailsText(e));
            details.setCaretPosition(0);
        }

        void setMini(LocalDate day) {
            miniHolder.removeAll();
            miniHolder.add(MiniCalendar.create(day, onPick), BorderLayout.CENTER);
            miniHolder.revalidate();
            miniHolder.repaint();
        }

        void setDeadlines(List<Event> events, LocalDate from, LocalDate to, String title) {
            dueScroll.setBorder(BorderFactory.createTitledBorder(title));
            dueModel.clear();
            dueEvents.clear();
            for (Event e : events) {
                if (e.kind.equals("work")) {
                    continue;
                }
                boolean deadlineish = e.kind.equals("task") || e.time.isBlank() || e.endTime.isBlank();
                if (!deadlineish || !isDate(e.date)) {
                    continue;
                }
                LocalDate d = LocalDate.parse(e.date);
                if (d.isBefore(from) || d.isAfter(to)) {
                    continue;
                }
                dueEvents.add(e);
            }
            dueEvents.sort((a, b) -> Main.whenKey(a).compareTo(Main.whenKey(b)));
            for (Event e : dueEvents) {
                String when = e.time.isBlank() ? e.date : e.date + " " + e.time;
                dueModel.addElement(when + "   " + e.name + (e.isDone() ? "  [done]" : ""));
            }
            if (dueModel.isEmpty()) {
                dueModel.addElement("(nothing due)");
            }
        }
    }

    // ---------- collapsible split ----------

    static JSplitPane split(JComponent main, Sidebar side) {
        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, main, side);
        sp.setResizeWeight(1.0);
        sp.setContinuousLayout(true);
        sp.setOneTouchExpandable(true);
        sp.setBorder(null);
        return sp;
    }

    static void toggleSidebar(JSplitPane sp, Sidebar side, JButton btn) {
        if (side.isVisible()) {
            if (side.getWidth() > 60) {
                side.lastWidth = side.getWidth();
            }
            side.setVisible(false);
            btn.setText("Show sidebar");
        } else {
            side.setVisible(true);
            btn.setText("Hide sidebar");
            SwingUtilities.invokeLater(() -> sp.setDividerLocation(
                    Math.max(100, sp.getWidth() - side.lastWidth - sp.getDividerSize())));
        }
        sp.revalidate();
        sp.repaint();
    }

    static JButton nav(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> action.run());
        return b;
    }
}