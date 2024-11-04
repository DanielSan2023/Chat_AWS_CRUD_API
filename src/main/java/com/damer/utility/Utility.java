package com.damer.utility;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.lambda.runtime.Context;
import com.damer.entity.Message;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Utility {
    public static Map<String, String> createHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-amazon-author", "Daniel");
        headers.put("X-amazon-apiVersion", "v1");
        return headers;
    }

    public static String convertObjToString(Message message, Context context) {
        String jsonBody = null;
        try {
            jsonBody = new ObjectMapper().writeValueAsString(message);
        } catch (JsonProcessingException e) {
            context.getLogger().log("Error while converting obj to string:::" + e.getMessage());
        }
        return jsonBody;
    }

    public static Message convertStringToObj(String jsonBody, Context context) {
        Message message = null;
        try {
            message = new ObjectMapper().readValue(jsonBody, Message.class);
        } catch (JsonProcessingException e) {
            context.getLogger().log("Error while converting string to obj:::" + e.getMessage());
        }
        return message;
    }

    public static String convertListOfObjToString(List<Message> employees, Context context) {
        String jsonBody = null;
        try {
            jsonBody = new ObjectMapper().writeValueAsString(employees);
        } catch (JsonProcessingException e) {
            context.getLogger().log("Error while converting obj to string:::" + e.getMessage());
        }
        return jsonBody;
    }

}
