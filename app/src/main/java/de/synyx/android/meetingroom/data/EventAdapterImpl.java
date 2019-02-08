package de.synyx.android.meetingroom.data;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;

import android.database.Cursor;

import android.net.Uri;

import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Instances;

import android.support.annotation.NonNull;

import android.util.Log;

import de.synyx.android.meetingroom.business.event.EventModel;
import de.synyx.android.meetingroom.config.Registry;

import io.reactivex.Maybe;
import io.reactivex.Observable;

import io.reactivex.functions.Function;

import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;

import java.security.AccessControlException;

import java.util.Date;
import java.util.TimeZone;

import static de.synyx.android.meetingroom.util.rx.CursorIterable.closeCursorIfLast;
import static de.synyx.android.meetingroom.util.rx.CursorIterable.fromCursor;


/**
 * @author  Max Dobler - dobler@synyx.de
 */
public class EventAdapterImpl implements EventAdapter {

    private static final String TAG = EventAdapterImpl.class.getSimpleName();
    private final ContentResolver contentResolver;

    public EventAdapterImpl() {

        contentResolver = Registry.get(ContentResolver.class);
    }

    @Override
    public Observable<EventModel> getEventsForRoom(long roomId) {

        String[] projection = {
            Instances.EVENT_ID, //
            Instances.TITLE, //
            Instances.BEGIN, //
            Instances.END,
        };
        String selection = Instances.CALENDAR_ID + " = " + roomId;
        String sortChronological = Instances.BEGIN + " ASC";

        Cursor result = contentResolver.query(constructContentUri(), projection, selection, null, sortChronological);

        return Observable.fromIterable(fromCursor(result)) //
            .doAfterNext(closeCursorIfLast()) //
            .map(toEvent());
    }


    @Override
    public Maybe<Long> insertEvent(long calendarId, DateTime start, DateTime end, String title) {

        ContentValues values = new ContentValues();
        values.put(Events.DTSTART, start.getMillis());
        values.put(Events.DTEND, end.getMillis());
        values.put(Events.TITLE, title);
        values.put(Events.CALENDAR_ID, calendarId);
        values.put(Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());

        try {
            Uri insert = contentResolver.insert(Events.CONTENT_URI, values);

            return Maybe.just(Long.valueOf(insert.getLastPathSegment()));
        } catch (AccessControlException exception) {
            Log.e(TAG, "insertEvent: ", exception);

            return Maybe.empty();
        }
    }


    private Uri constructContentUri() {

        Uri.Builder builder = Instances.CONTENT_URI.buildUpon();
        long now = new Date().getTime();
        long endOfDay = LocalDateTime.now().withTime(23, 59, 59, 999).toDate().getTime();
        ContentUris.appendId(builder, now);
        ContentUris.appendId(builder, endOfDay);

        return builder.build();
    }


    @NonNull
    private Function<Cursor, EventModel> toEvent() {

        return
            cursor -> {
            long eventId = cursor.getLong(cursor.getColumnIndex(Instances.EVENT_ID));
            String title = cursor.getString(cursor.getColumnIndex(Instances.TITLE));
            long beginMillis = cursor.getLong(cursor.getColumnIndex(Instances.BEGIN));
            long endMillis = cursor.getLong(cursor.getColumnIndex(Instances.END));

            DateTime begin = new DateTime(beginMillis);
            DateTime end = new DateTime(endMillis);

            return new EventModel(eventId, title, begin, end);
        };
    }
}