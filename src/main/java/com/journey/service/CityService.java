package com.journey.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CityService {
    JourneyScheduler scheduler = new JourneyScheduler();
  public Map<String, Object> getFastestRoutes(String from, String to) {

        try {
            return scheduler.getFastestRoutes(from, to);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
