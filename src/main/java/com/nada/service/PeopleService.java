package com.nada.service;

import com.nada.model.Person;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class PeopleService {

    private final Map<Integer, Person> people = new HashMap<>();
    private final Map<Integer, Set<Integer>> connections = new HashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    /**
     * Creates and stores a new person.
     */
    public synchronized Person createPerson(String name) {
        int id = nextId.getAndIncrement();

        Person person = new Person(id, name);

        people.put(id, person);
        connections.put(id, new LinkedHashSet<>());

        return person;
    }

    /**
     * Finds a person by ID.
     */
    public synchronized Optional<Person> findPerson(int id) {
        return Optional.ofNullable(people.get(id));
    }

    /**
     * Returns the direct contacts of a person.
     */
    public synchronized List<Person> getContacts(int personId) {
        Set<Integer> contactIds = connections.get(personId);

        if (contactIds == null) {
            throw new NoSuchElementException("no person " + personId);
        }

        return contactIds.stream()
                .map(people::get)
                .toList();
    }

    /**
     * Creates a mutual connection between two existing people.
     */
    public synchronized void connect(int a, int b) {
        Set<Integer> contactsA = connections.get(a);
        Set<Integer> contactsB = connections.get(b);

        if (contactsA == null) {
            throw new NoSuchElementException("no person " + a);
        }

        if (contactsB == null) {
            throw new NoSuchElementException("no person " + b);
        }

        contactsA.add(b);
        contactsB.add(a);
    }

    /**
     * Finds the shortest path between two people.
     *
     * The search is limited to maxHops. Returns empty if no path exists within
     * the limit; throws NoSuchElementException if either person is unknown.
     */
    public synchronized Optional<List<Person>> findShortestPath(
            int from,
            int to,
            int maxHops
    ) {

        if (!people.containsKey(from)) {
            throw new NoSuchElementException("no person " + from);
        }

        if (!people.containsKey(to)) {
            throw new NoSuchElementException("no person " + to);
        }

        // Same person: zero hops.
        if (from == to) {
            return Optional.of(List.of(people.get(from)));
        }

        record PathNode(int personId, List<Integer> path) {}

        Queue<PathNode> queue = new ArrayDeque<>();
        Set<Integer> visited = new HashSet<>();

        queue.add(new PathNode(from, List.of(from)));
        visited.add(from);

        while (!queue.isEmpty()) {

            PathNode current = queue.poll();

            int currentHops = current.path().size() - 1;

            // Do not explore beyond the allowed hop count.
            if (currentHops >= maxHops) {
                continue;
            }

            Set<Integer> neighbors = connections.get(current.personId());

            for (int neighbor : neighbors) {

                if (visited.contains(neighbor)) {
                    continue;
                }

                List<Integer> newPath = new ArrayList<>(current.path());
                newPath.add(neighbor);

                if (neighbor == to) {
                    return Optional.of(
                            newPath.stream()
                                    .map(people::get)
                                    .toList()
                    );
                }

                visited.add(neighbor);

                queue.add(
                        new PathNode(neighbor, newPath)
                );
            }
        }

        return Optional.empty();
    }
}