package com.example.service;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/calculate")
@Produces(MediaType.APPLICATION_JSON)
public class CalculatorResource {
    @GET
    public Response calculate(@QueryParam("expression") String expression) {
        try {
            return Response.ok("{\"result\":" + Expression.evaluate(expression) + "}").build();
        } catch (IllegalArgumentException | ArithmeticException ex) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Invalid expression or division by zero\"}").build();
        }
    }
}
