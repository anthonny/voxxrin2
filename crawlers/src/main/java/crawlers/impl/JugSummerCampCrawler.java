package crawlers.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.kevinsawicki.http.HttpRequest;
import com.google.common.collect.ImmutableList;
import crawlers.AbstractHttpCrawler;
import crawlers.CrawlingResult;
import crawlers.configuration.CrawlingConfiguration;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import restx.factory.Component;
import voxxrin2.domain.*;
import voxxrin2.domain.technical.Reference;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class JugSummerCampCrawler extends AbstractHttpCrawler {

    private static final String BASE_URL = "http://www.jugsummercamp.org/api/edition/";
    private static final String NAME = "JUG SummerCamp";

    public JugSummerCampCrawler() {
        super("jsc", ImmutableList.of("jsc-publisher"));
    }

    private static DateTime transformDate(DateTime dateTime) {
        return dateTime.minusHours(2);
    }

    @Override
    public CrawlingResult crawl(CrawlingConfiguration configuration) throws IOException {

        String eventUrl = BASE_URL + configuration.getExternalEventRef();

        JSCPayload payload = MAPPER.readValue(HttpRequest.get(eventUrl).body(), JSCPayload.class);
        Event event = payload.toStdEvent();

        CrawlingResult result = new CrawlingResult(event);
        Day day = payload.toStdDay(result.getEvent());
        result.getDays().add(day);

        Map<String, Speaker> speakers = new HashMap<>();
        Map<String, Room> rooms = new HashMap<>();
        for (JSCPresentation jscPresentation : payload.presentations) {
            Presentation presentation = jscPresentation.toStdPresentation(event, day);
            presentation.setSpeakers(getSpeakers(speakers, jscPresentation));
            presentation.setLocation(getRoom(rooms, jscPresentation));
            result.getPresentations().add(presentation);
        }
        result.getSpeakers().addAll(speakers.values());
        result.getRooms().addAll(rooms.values());

        return result;
    }

    @Override
    public CrawlingResult setup(CrawlingResult result, CrawlingConfiguration configuration) {
        result.getEvent().setName(NAME);
        return super.setup(result, configuration);
    }

    private Reference<Room> getRoom(Map<String, Room> rooms, JSCPresentation jscPresentation) {
        Room room = rooms.get(jscPresentation.place);
        if (room == null) {
            room = (Room) new Room()
                    .setFullName(jscPresentation.place)
                    .setName(jscPresentation.place)
                    .setKey(new ObjectId().toString());
            rooms.put(jscPresentation.place, room);
        }
        return Reference.of(Type.room, room.getKey());
    }

    private List<Reference<Speaker>> getSpeakers(Map<String, Speaker> speakers, JSCPresentation jscPresentation) {
        List<Reference<Speaker>> presentationSpeakers = new ArrayList<>();
        for (JSCSpeaker speaker : jscPresentation.speakers) {
            Speaker stdSpeaker = speakers.get(speaker.id);
            if (stdSpeaker == null) {
                stdSpeaker = speaker.toStdSpeaker();
                speakers.put(speaker.id, stdSpeaker);
            }
            presentationSpeakers.add(Reference.<Speaker>of(Type.speaker, stdSpeaker.getKey()));
        }
        return presentationSpeakers;
    }

    private static class JSCPayload {

        public String description;

        public String label;

        public String place;

        public List<JSCPresentation> presentations = new ArrayList<>();

        public long date;

        public Event toStdEvent() {
            DateTime from = transformDate(new DateTime(date));
            return (Event) new Event()
                    .setImageUrl("http://www.jugsummercamp.org/assets/images/logo-summercamp.png")
                    .setDescription(description)
                    .setFrom(from)
                    // JSC generally lasts one day
                    .setTo(from.plusDays(1).withTimeAtStartOfDay())
                    .setLocation(place)
                    .setKey(new ObjectId().toString());
        }

        public Day toStdDay(Event event) {
            Reference<Event> eventRef = Reference.of(Type.event, event.getKey());
            DateTimeFormatter fmt = DateTimeFormat.forPattern("dd/MM/yyyy");
            return (Day) new Day()
                    .setEvent(eventRef)
                    .setDate(event.getFrom())
                    .setName(fmt.print(transformDate(new DateTime(date))))
                    .setKey(new ObjectId().toString());
        }
    }

    private static class JSCPresentation {

        public String description;

        public String title;

        public String place;

        public List<JSCSpeaker> speakers;

        @JsonProperty("type-id")
        public JSCPresentationType type;

        @JsonProperty("start-date")
        public long startDate;

        @JsonProperty("end-date")
        public long endDate;

        public String id;

        public Presentation toStdPresentation(Event event, Day day) {
            Reference<Event> eventRef = Reference.of(Type.event, event.getKey());
            Reference<Day> dayRef = Reference.of(Type.day, day.getKey());
            return (Presentation) new Presentation()
                    .setEvent(eventRef)
                    .setTitle(title)
                    .setDay(dayRef)
                    .setExternalId(id)
                    .setSummary(description)
                    .setFrom(transformDate(new DateTime(startDate)))
                    .setTo(transformDate(new DateTime(endDate)))
                    .setKind(type.label)
                    .setLocation(null)
                    .setKey(new ObjectId().toString());
        }
    }

    private static class JSCPresentationType {

        public String label;
    }

    private static class JSCSpeaker {

        public String id;

        public String name;

        public String description;

        public String pictureUrl;

        public Speaker toStdSpeaker() {
            String[] names = name.split(" ");
            return (Speaker) new Speaker()
                    .setFirstName(names[0])
                    .setLastName(names[1])
                    .setName(name)
                    .setAvatarUrl(pictureUrl)
                    .setBio(description)
                    .setKey(new ObjectId().toString());
        }
    }
}
