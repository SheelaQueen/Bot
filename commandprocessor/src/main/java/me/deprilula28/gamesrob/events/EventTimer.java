package me.deprilula28.gamesrob.events;

import me.deprilula28.gamesrob.utility.Language;
import lombok.Data;
import me.deprilula28.gamesrobshardcluster.utilities.ShardClusterUtilities;
import me.deprilula28.jdacmdframework.CommandContext;
import me.deprilula28.jdacmdframework.exceptions.CommandArgsException;

import java.util.Calendar;

@Data
public abstract class EventTimer {
    private static final EventTimer[] EVENTS = {
            HalloweenEvent.TIMER
    };

    private String langCode;

    public abstract boolean isEventInTime();
    abstract long getNextStartTime();

    public void checkEvent(CommandContext context) {
        if (!isEventInTime()) throw new CommandArgsException(Language.transl(context, "events.hasntBegun",
                Language.transl(context, langCode), ShardClusterUtilities.formatTime(getNextStartTime())));
    }

    @Data
    public static class YearlyTimedEvent extends EventTimer {
        private int dayBegin;
        private int monthBegin;
        private int dayEnd;
        private int monthEnd;
        private String langCode;

        public YearlyTimedEvent(int dayBegin, int monthBegin, int dayEnd, int monthEnd, String langCode){
            super.langCode = langCode;
            this.dayBegin = dayBegin;
            this.monthBegin = monthBegin - 1;
            this.dayEnd = dayEnd;
            this.monthEnd = monthEnd - 1;
        }

        @Override
        public boolean isEventInTime() {
            Calendar cur = Calendar.getInstance();
            int day = cur.get(Calendar.DAY_OF_MONTH);
            int month = cur.get(Calendar.MONTH);

            return day >= dayBegin && day <= dayEnd && month >= monthBegin && month <= monthEnd;
        }

        @Override
        long getNextStartTime() {
            Calendar cur = Calendar.getInstance();
            int day = cur.get(Calendar.DAY_OF_MONTH);
            int month = cur.get(Calendar.MONTH);

            Calendar nextEvent = Calendar.getInstance();
            // After the event in calendar
            if (month >= monthEnd && day > dayEnd) nextEvent.set(Calendar.YEAR, cur.get(Calendar.YEAR) + 1);
            nextEvent.set(Calendar.DAY_OF_MONTH, dayBegin);
            nextEvent.set(Calendar.MONTH, monthBegin);

            return nextEvent.getTimeInMillis();
        }
    }
}
