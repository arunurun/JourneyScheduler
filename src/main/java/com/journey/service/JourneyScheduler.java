package com.journey.service;
import java.io.*;
import java.time.Duration;
import java.time.LocalTime;
import java.util.*;

public class JourneyScheduler {

    private static class Journey {

        String fromCode;
        String toCode;
        LocalTime startTime;
        LocalTime endTime;
        String mode;
        int price;
        String fromCity;
        String toCity;

        Journey(String[] fields) {
            this.fromCode = fields[0];
            this.toCode = fields[1];
            this.startTime = LocalTime.parse(fields[2], java.time.format.DateTimeFormatter.ofPattern("HHmm"));
            this.endTime = LocalTime.parse(fields[3], java.time.format.DateTimeFormatter.ofPattern("HHmm"));
            this.mode = fields[4];
            this.price = Integer.parseInt(fields[5]);
            this.fromCity = fields[6];
            this.toCity = fields[7];
        }

        long getDuration() {
            Duration duration;
            if (endTime.isBefore(startTime)) {
                // Journey crosses midnight
                duration = Duration.between(startTime, LocalTime.MAX)
                        .plus(Duration.between(LocalTime.MIN, endTime));
            } else {
                duration = Duration.between(startTime, endTime);
            }
            return duration.toMinutes();
        }
    }

    private static class Route {

        List<Journey> journeys;
        long totalTime;
        int totalPrice;

        Route() {
            journeys = new ArrayList<>();
            totalTime = 0;
            totalPrice = 0;
        }

        Route(Route other) {
            journeys = new ArrayList<>(other.journeys);
            totalTime = other.totalTime;
            totalPrice = other.totalPrice;
        }

        String getRouteString() {
            if (journeys.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder(journeys.get(0).fromCode);
            for (Journey j : journeys) {
                sb.append("_").append(j.toCode);
            }
            return sb.toString();
        }

        List<String> getTransportList() {
            List<String> transport = new ArrayList<>();
            for (Journey j : journeys) {
                transport.add(j.mode + "_" + j.price);
            }
            return transport;
        }
    }

    private Map<String, List<Journey>> routeGraph = new HashMap<>();
    private Set<String> destinations = new HashSet<>();
    private static final int TRANSFER_TIME = 120; // minutes
    private static final int MAX_ROUTES = 5;
    private static final int MAX_DEPTH = 5; // Maximum number of connections allowed
    Map<String,String> nameToCodeMap = new HashMap<>();
    public void loadSchedule(String filename) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            br.readLine(); // Skip header
            while ((line = br.readLine()) != null) {
                String[] fields = line.split(",");
                if (fields.length >= 8) {  // Ensure we have all required fields
                    Journey journey = new Journey(fields);
                    destinations.add(journey.toCode);
                    routeGraph.computeIfAbsent(journey.fromCode, k -> new ArrayList<>()).add(journey);
                }
            }
        }
        System.out.println("Loaded routes: " + routeGraph.size());
        for (Map.Entry<String, List<Journey>> entry : routeGraph.entrySet()) {
            System.out.println("From " + entry.getKey() + ": " + entry.getValue().size() + " journeys");
        }
        nameToCodeMap.put("mumbai","BOM");
        nameToCodeMap.put("bombay","BOM");
        nameToCodeMap.put("delhi","DEL");
        nameToCodeMap.put("pune","PNQ");
        nameToCodeMap.put("hyderabad","HYD");
        nameToCodeMap.put("jaipur","JAI");
        nameToCodeMap.put("kolkata","CCU");
        nameToCodeMap.put("calcutta","CCU");
        nameToCodeMap.put("chandigarh","IXC");
        nameToCodeMap.put("ahmedabad","AMD");
        nameToCodeMap.put("bangalore","BLR");
        nameToCodeMap.put("guwahati","GAU");
        nameToCodeMap.put("chennai","MAA");
        nameToCodeMap.put("cochin","COK");
        nameToCodeMap.put("kochi","COK");
    }

    public Map<String, Object> getFastestRoutes(String from, String to) throws IOException {
        if(routeGraph.isEmpty())
            loadSchedule("src/schedule.csv");
        from = nameToCodeMap.get(from.toLowerCase());
        to = nameToCodeMap.get(to.toLowerCase());
        if (!routeGraph.containsKey(from)) {
            System.out.println("No routes found from: " + from);
            return createEmptyResponse();
        }
        if (!destinations.contains(to)) {
            System.out.println("No routes found to: " + to);
            return createEmptyResponse();
        }

        PriorityQueue<Route> routes = new PriorityQueue<>((r1, r2) -> Long.compare(r1.totalTime, r2.totalTime));
        Set<String> visited = new HashSet<>();
        Route initialRoute = new Route();

        findRoutes(from, to, visited, initialRoute, routes, true, 0);

        if (routes.isEmpty()) {
            System.out.println("No routes found between " + from + " and " + to);
            return createEmptyResponse();
        }

        return formatOutput(getTopRoutes(routes));
    }

    public Map<String, Object> getCheapestRoutes(String from, String to) {
        if (!routeGraph.containsKey(from)) {
            System.out.println("No routes found from: " + from);
            return createEmptyResponse();
        }
        if (!destinations.contains(to)) {
            System.out.println("No routes found to: " + to);
            return createEmptyResponse();
        }

        PriorityQueue<Route> routes = new PriorityQueue<>((r1, r2) -> Integer.compare(r1.totalPrice, r2.totalPrice));
        Set<String> visited = new HashSet<>();
        Route initialRoute = new Route();

        findRoutes(from, to, visited, initialRoute, routes, false, 0);

        if (routes.isEmpty()) {
            System.out.println("No routes found between " + from + " and " + to);
            return createEmptyResponse();
        }

        return formatOutput(getTopRoutes(routes));
    }

    private Map<String, Object> createEmptyResponse() {
        Map<String, Object> output = new HashMap<>();
        output.put("routes", new ArrayList<>());
        return output;
    }

    private void findRoutes(String current, String destination, Set<String> visited, Route currentRoute,
                            PriorityQueue<Route> routes, boolean prioritizeFast, int depth) {

        // Early termination conditions
        if (depth >= MAX_DEPTH || visited.size() > routeGraph.size()) {
            return;
        }

        if (current.equals(destination) && !currentRoute.journeys.isEmpty()) {
            routes.offer(new Route(currentRoute));
            return;
        }

        visited.add(current);

        List<Journey> possibleJourneys = routeGraph.getOrDefault(current, Collections.emptyList());

        // Sort possible journeys based on optimization criteria
        if (prioritizeFast) {
            possibleJourneys.sort((j1, j2) -> Long.compare(j1.getDuration(), j2.getDuration()));
        } else {
            possibleJourneys.sort((j1, j2) -> Integer.compare(j1.price, j2.price));
        }

        for (Journey journey : possibleJourneys) {
            if (!visited.contains(journey.toCode)) {
                long transferTime = 0;
                if (!currentRoute.journeys.isEmpty()) {
                    Journey lastJourney = currentRoute.journeys.get(currentRoute.journeys.size() - 1);
                    if (!lastJourney.mode.equals(journey.mode)) {
                        transferTime = TRANSFER_TIME;
                    }
                }

                // Early pruning: skip if this route is already worse than existing solutions
                if (!routes.isEmpty()) {
                    Route bestRoute = routes.peek();
                    if (prioritizeFast && (currentRoute.totalTime + journey.getDuration() + transferTime > bestRoute.totalTime)) {
                        continue;
                    } else if (!prioritizeFast && (currentRoute.totalPrice + journey.price > bestRoute.totalPrice)) {
                        continue;
                    }
                }

                currentRoute.journeys.add(journey);
                currentRoute.totalTime += journey.getDuration() + transferTime;
                currentRoute.totalPrice += journey.price;

                findRoutes(journey.toCode, destination, new HashSet<>(visited), currentRoute, routes, prioritizeFast, depth + 1);

                currentRoute.journeys.remove(currentRoute.journeys.size() - 1);
                currentRoute.totalTime -= (journey.getDuration() + transferTime);
                currentRoute.totalPrice -= journey.price;
            }
        }
    }

    private List<Route> getTopRoutes(PriorityQueue<Route> routes) {
        List<Route> topRoutes = new ArrayList<>();
        Set<String> uniqueRoutes = new HashSet<>();

        while (!routes.isEmpty() && topRoutes.size() < MAX_ROUTES) {
            Route route = routes.poll();
            String routeString = route.getRouteString();
            if (!uniqueRoutes.contains(routeString)) {
                topRoutes.add(route);
                uniqueRoutes.add(routeString);
            }
        }
        return topRoutes;
    }

    private Map<String, Object> formatOutput(List<Route> routes) {
        List<Map<String, Object>> routeList = new ArrayList<>();
        for (Route route : routes) {
            Map<String, Object> routeMap = new HashMap<>();
            routeMap.put("route", route.getRouteString());
            routeMap.put("transport", route.getTransportList());
            routeMap.put("total_time", route.totalTime);
            routeMap.put("total_price", route.totalPrice);
            routeList.add(routeMap);
        }

        Map<String, Object> output = new HashMap<>();
        output.put("routes", routeList);
        return output;
    }

    public static void main(String[] args) {
        JourneyScheduler router = new JourneyScheduler();
        try {
            // Test Case 1: Load schedule and find fastest routes
            router.loadSchedule("src/schedule.csv");

            // Test Case 2: Find fastest routes
            Map<String, Object> fastestRoutes = router.getFastestRoutes("LHR", "CDG");
            System.out.println("Fastest Routes from LHR to CDG:");
            System.out.println(fastestRoutes);

            // Test Case 3: Find cheapest routes
            Map<String, Object> cheapestRoutes = router.getCheapestRoutes("LHR", "CDG");
            System.out.println("\nCheapest Routes from LHR to CDG:");
            System.out.println(cheapestRoutes);

            // Test Case 4: Find fastest routes
            fastestRoutes = router.getFastestRoutes("DEL", "BOM");
            System.out.println("\nFastest Routes from DEL to BOM");
            System.out.println(fastestRoutes);

            // Test Case 5: Find cheapest routes
            cheapestRoutes = router.getCheapestRoutes("DEL", "BOM");
            System.out.println("\nCheapest Routes from DEL to BOM");
            System.out.println(cheapestRoutes);

            // Test Case 4: Find fastest routes
            fastestRoutes = router.getFastestRoutes("BOM", "BLR");
            System.out.println("\nFastest Routes from BOM to BLR");
            System.out.println(fastestRoutes);

            // Test Case 5: Find cheapest routes
            cheapestRoutes = router.getCheapestRoutes("BOM", "BLR");
            System.out.println("\nCheapest Routes from BOM to BLR");
            System.out.println(cheapestRoutes);

            // Test Case 4: Find fastest routes
            fastestRoutes = router.getFastestRoutes("PNQ", "LR");
            System.out.println("\nFastest Routes from PNQ to LR");
            System.out.println(fastestRoutes);

            // Test Case 5: Find cheapest routes
            cheapestRoutes = router.getCheapestRoutes("PNQ", "LR");
            System.out.println("\nCheapest Routes from PNQ to LR");
            System.out.println(cheapestRoutes);

        } catch (IOException e) {
            System.err.println("Error reading schedule file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
