package com.example.service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/time")
public class TimeResource {
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String getCurrentTime() {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        return "{\"time\": \"" + time + "\"}";
    }
}
