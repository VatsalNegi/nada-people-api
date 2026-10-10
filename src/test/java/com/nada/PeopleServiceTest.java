package com.nada;

import com.nada.model.Person;
import com.nada.service.PeopleService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class PeopleServiceTest {

    @Test
    void shouldCreatePersonWithGeneratedId() {
        PeopleService service = new PeopleService();

        Person person = service.createPerson("Asha");

        assertEquals(1, person.getId());
        assertEquals("Asha", person.getName());
    }

    @Test
    void shouldCreateMutualConnection() {
        PeopleService service = new PeopleService();

        Person asha = service.createPerson("Asha");
        Person rohan = service.createPerson("Rohan");

        service.connect(asha.getId(), rohan.getId());

        List<Person> ashaContacts = service.getContacts(asha.getId());
        List<Person> rohanContacts = service.getContacts(rohan.getId());

        assertEquals(1, ashaContacts.size());
        assertEquals(rohan.getId(), ashaContacts.get(0).getId());

        assertEquals(1, rohanContacts.size());
        assertEquals(asha.getId(), rohanContacts.get(0).getId());
    }

    @Test
    void shouldNotDuplicateConnection() {
        PeopleService service = new PeopleService();

        Person asha = service.createPerson("Asha");
        Person rohan = service.createPerson("Rohan");

        service.connect(asha.getId(), rohan.getId());
        service.connect(asha.getId(), rohan.getId());

        assertEquals(1, service.getContacts(asha.getId()).size());
        assertEquals(1, service.getContacts(rohan.getId()).size());
    }

    @Test
    void shouldFindThreeHopPath() {
        PeopleService service = new PeopleService();

        Person a = service.createPerson("A");
        Person b = service.createPerson("B");
        Person c = service.createPerson("C");
        Person d = service.createPerson("D");

        service.connect(a.getId(), b.getId());
        service.connect(b.getId(), c.getId());
        service.connect(c.getId(), d.getId());

        var result = service.findShortestPath(
                a.getId(),
                d.getId(),
                3
        );

        assertTrue(result.isPresent());

        List<Person> path = result.get();

        assertEquals(4, path.size());
        assertEquals("A", path.get(0).getName());
        assertEquals("B", path.get(1).getName());
        assertEquals("C", path.get(2).getName());
        assertEquals("D", path.get(3).getName());
    }

    @Test
    void shouldRejectPathBeyondThreeHops() {
        PeopleService service = new PeopleService();

        Person a = service.createPerson("A");
        Person b = service.createPerson("B");
        Person c = service.createPerson("C");
        Person d = service.createPerson("D");
        Person e = service.createPerson("E");

        service.connect(a.getId(), b.getId());
        service.connect(b.getId(), c.getId());
        service.connect(c.getId(), d.getId());
        service.connect(d.getId(), e.getId());

        var result = service.findShortestPath(
                a.getId(),
                e.getId(),
                3
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnZeroHopPathForSamePerson() {
        PeopleService service = new PeopleService();

        Person asha = service.createPerson("Asha");

        var result = service.findShortestPath(
                asha.getId(),
                asha.getId(),
                3
        );

        assertTrue(result.isPresent());
        assertEquals(1, result.get().size());
        assertEquals(asha.getId(), result.get().get(0).getId());
    }

    @Test
    void shouldPreserveUnicodeName() {
        PeopleService service = new PeopleService();

        Person person = service.createPerson("आशा");

        assertEquals("आशा", person.getName());
    }

    @Test
    void shouldRejectUnknownPersonForContacts() {
        PeopleService service = new PeopleService();

        assertThrows(
                NoSuchElementException.class,
                () -> service.getContacts(999)
        );
    }

    @Test
    void shouldRejectUnknownPersonForPath() {
        PeopleService service = new PeopleService();
        Person a = service.createPerson("A");

        assertThrows(NoSuchElementException.class, () -> service.findShortestPath(a.getId(), 99, 3));
        assertThrows(NoSuchElementException.class, () -> service.findShortestPath(99, a.getId(), 3));
    }

    @Test
    void shouldPickShortestOfSeveralPaths() {
        PeopleService service = new PeopleService();
        Person a = service.createPerson("A");
        Person b = service.createPerson("B");
        Person c = service.createPerson("C");
        Person d = service.createPerson("D");
        Person e = service.createPerson("E");

        // long route A-B-C-D (3 hops) and short route A-E-D (2 hops)
        service.connect(a.getId(), b.getId());
        service.connect(b.getId(), c.getId());
        service.connect(c.getId(), d.getId());
        service.connect(a.getId(), e.getId());
        service.connect(e.getId(), d.getId());

        assertEquals(3, service.findShortestPath(a.getId(), d.getId(), 3).get().size());
    }
}
