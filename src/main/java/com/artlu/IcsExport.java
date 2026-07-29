package com.artlu;

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.FluentCalendar;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.Uid;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.time.LocalDate;
import java.util.List;

/**
 * Writes your calendar out as an .ics file — the standard format every calendar
 * app can import. One-way and offline: no accounts, no keys, no network.
 *
 * Only the things that live in this app are exported. The Brightspace and
 * Purdue feeds are left out, because you can subscribe to those directly in any
 * calendar app and exporting them here would just duplicate them.
 */
public class IcsExport {

    /** Asks where to save, writes the file, then reports what happened. */
    static void run(Component parent, List<Event> events) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export calendar");
        chooser.setSelectedFile(new File("allincalendar.ics"));
        chooser.setFileFilter(new FileNameExtensionFilter("Calendar files (*.ics)", "ics"));

        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return; // you cancelled
        }

        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".ics")) {
            file = new File(file.getParentFile(), file.getName() + ".ics");
        }

        try {
            int written = write(file, events);
            JOptionPane.showMessageDialog(parent,
                    "Exported " + written + " items to\n" + file.getAbsolutePath());
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(parent, "Couldn't write that file — see console.");
        }
    }

    /** Builds the calendar and writes it. Returns how many items went in. */
    static int write(File file, List<Event> events) throws Exception {
        FluentCalendar building = new Calendar()
                .withProdId("-//AllInCalendar//EN")
                .withDefaults(); // fills in VERSION and CALSCALE for us

        int written = 0;
        for (Event e : events) {
            if (!exportable(e)) {
                continue;
            }
            VEvent vevent = toVEvent(e);
            if (vevent == null) {
                continue;
            }
            building = building.withComponent(vevent);
            written++;
        }

        Calendar calendar = building.getFluentTarget();
        try (Writer out = new FileWriter(file)) {
            new CalendarOutputter().output(calendar, out);
        }
        return written;
    }

    // Your own tasks and events, plus the work blocks the scheduler placed.
    // Imported calendar events are skipped — see the note at the top.
    private static boolean exportable(Event e) {
        return e.userAdded || e.kind.equals("work");
    }

    private static VEvent toVEvent(Event e) {
        if (!CalendarUI.isDate(e.date)) {
            return null; // "no date" tasks have nowhere to sit on a calendar
        }
        LocalDate day = LocalDate.parse(e.date);

        VEvent vevent;
        if (e.time.isBlank()) {
            vevent = new VEvent(day, e.name); // an all-day entry
        } else {
            int start = Main.minutesOf(e.time);
            if (start < 0) {
                return null; // unreadable time — leave it out rather than guess
            }
            // iCalendar has no "just a deadline", so a due time with no end
            // becomes a short block — the same 30 minutes the day grid draws.
            int end = e.endTime.isBlank() ? start + 30 : Main.minutesOf(e.endTime);
            if (end <= start) {
                end = start + 30;
            }
            vevent = new VEvent(day.atStartOfDay().plusMinutes(start),
                    day.atStartOfDay().plusMinutes(end), e.name);
        }

        vevent.add(new Uid(uidFor(e)));
        if (!e.description.isBlank()) {
            vevent.add(new Description(e.description));
        }
        return vevent;
    }

    // Stable for a given item, so importing the file a second time updates the
    // same entry instead of leaving you with two copies of everything.
    private static String uidFor(Event e) {
        String key = e.kind + "|" + e.name + "|" + e.date + "|" + e.time;
        return Math.abs(key.hashCode()) + "@allincalendar";
    }
}
