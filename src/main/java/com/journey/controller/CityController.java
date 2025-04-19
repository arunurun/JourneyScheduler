package com.journey.controller;

import com.journey.RouteRequest;
import com.journey.service.CityService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cities")
public class CityController {

    private final CityService cityService = new CityService();

    @PostMapping("/findFastestRoute")
    public Map<String, Object> fastestRoute(@RequestBody RouteRequest request) {
        return cityService.getFastestRoutes(request.getFromCity(), request.getToCity());
    }
}
