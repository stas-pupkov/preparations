package ru.stas.pupkov.preparations.livecoding.code;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Java 9+: {@code peek}+{@code count} может не выполнить peek; повтор Stream → ISE.
 */
public class StreamApiCountReuseDemo {

    public static class Client {
        private String name;
        private String city;

        public Client(String name, String city) {
            this.name = name;
            this.city = city;
        }

        public String getName() {
            return name;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }
    }

    public static Map<String, Long> countBy(Stream<Client> clients,
                                            Function<Client, String> clientParameterGetter) {
        return clients.collect(Collectors.groupingBy(
                clientParameterGetter, TreeMap::new, Collectors.counting()));
    }

    public static void main(String[] args) {
        List<Client> clients = List.of(
                new Client("Иванов", "Москва"),
                new Client("Михайлов", "Санкт-Петербург"),
                new Client("Сергеев", "Санкт-Петербург"),
                new Client("Иванов", null),
                new Client("Сергеев", null)
        );

        long count = clients.stream()
                .peek(client -> {
                    if (client.getCity() == null) {
                        client.setCity("Москва");
                    }
                })
                .count();

        Predicate<Client> clientPredicate = client -> client.getCity() != null;
        Stream<Client> validClients = clients.stream().filter(clientPredicate);

        System.out.println(count);
        System.out.println(countBy(validClients, Client::getName));
        try {
            System.out.println(countBy(validClients, Client::getCity));
        } catch (IllegalStateException e) {
            System.out.println(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
