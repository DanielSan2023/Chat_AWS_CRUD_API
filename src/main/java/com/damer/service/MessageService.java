package com.damer.service;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.damer.entity.Message;
import com.damer.utility.Utility;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessageService {
    private static DynamoDBMapper dynamoDBMapper;
    private static String jsonBody = null;

    // Initialize DynamoDBMapper only once
    private void initDynamoDB() {
        if (dynamoDBMapper == null) {  // Check if dynamoDBMapper is already initialized
            AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
            dynamoDBMapper = new DynamoDBMapper(client);
        }
    }

    private APIGatewayProxyResponseEvent createAPIResponse(String body, int statusCode, Map<String, String> headers) {
        APIGatewayProxyResponseEvent responseEvent = new APIGatewayProxyResponseEvent();
        responseEvent.setBody(body);
        responseEvent.setHeaders(headers);
        responseEvent.setStatusCode(statusCode);
        return responseEvent;
    }

    public APIGatewayProxyResponseEvent saveMessage(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        initDynamoDB();
        Message message = Utility.convertStringToObj(apiGatewayRequest.getBody(), context);
        if (message == null || message.getContent() == null) {
            throw new IllegalArgumentException("Message content cannot be null");
        }
        setMessage(message);
        System.out.println("Saving Message: " + message.getMessId() + ", " + message.getSender() + ", " + message.getContent());
        dynamoDBMapper.save(message);
        jsonBody = Utility.convertObjToString(message, context);
        context.getLogger().log("data saved successfully to dynamodb:::" + jsonBody);
        return createAPIResponse(jsonBody, 201, Utility.createHeaders());
    }

    private static void setMessage(Message message) {
        long timestamp = Instant.now().getEpochSecond();
        message.setMessId("mess" + timestamp);
        message.setTimeForStamp(timestamp);
        message.setIsCorrected(false);
    }

    public APIGatewayProxyResponseEvent getMessageById(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        initDynamoDB();
        String messId = apiGatewayRequest.getPathParameters().get("messId");
        Message message = dynamoDBMapper.load(Message.class, messId);
        if (message != null) {
            jsonBody = Utility.convertObjToString(message, context);
            context.getLogger().log("fetch message By ID:::" + jsonBody);
            return createAPIResponse(jsonBody, 200, Utility.createHeaders());
        } else {
            jsonBody = "Message Not Found Exception :" + messId;
            return createAPIResponse(jsonBody, 400, Utility.createHeaders());
        }
    }

    public APIGatewayProxyResponseEvent getAllMessages(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        initDynamoDB();
        List<Message> messages = dynamoDBMapper.scan(Message.class, new DynamoDBScanExpression());
        jsonBody = Utility.convertListOfObjToString(messages, context);
        context.getLogger().log("fetch messages List:::" + jsonBody);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }

    public APIGatewayProxyResponseEvent getMessagesByRoomIdAndTimestamp(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        initDynamoDB();

        String roomId = apiGatewayRequest.getPathParameters() != null
                ? apiGatewayRequest.getPathParameters().get("roomId")
                : null;

        String timestampStr = apiGatewayRequest.getQueryStringParameters() != null
                ? apiGatewayRequest.getQueryStringParameters().get("timeForStamp")
                : null;

        if (roomId == null || roomId.isEmpty()) {
            return createAPIResponse("roomId cannot be null or empty", 400, Utility.createHeaders());
        }

        long timestamp;
        try {
            timestamp = timestampStr != null ? Long.parseLong(timestampStr) : 0L; // default to 0 if not provided
        } catch (NumberFormatException e) {
            return createAPIResponse("Invalid timestamp format", 400, Utility.createHeaders());
        }

        // Log the roomId and timestamp
        context.getLogger().log("Fetching messages for roomId: " + roomId + " with timestamp: " + timestamp);

        // Create the expression attribute values for the query
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":roomId", new AttributeValue().withS(roomId));
        eav.put(":timestamp", new AttributeValue().withN(String.valueOf(timestamp)));

        // Correct the key condition expression to match the entity
        DynamoDBQueryExpression<Message> queryExpression = new DynamoDBQueryExpression<Message>()
                .withIndexName("RoomIndex")  // Use the GSI
                .withConsistentRead(false)   // For GSI, consistent read must be false
                .withKeyConditionExpression("roomId = :roomId and timeForStamp >= :timestamp") // Use the correct attribute names
                .withExpressionAttributeValues(eav);

        try {
            List<Message> messages = dynamoDBMapper.query(Message.class, queryExpression);
            String jsonBody = Utility.convertListOfObjToString(messages, context);
            context.getLogger().log("Fetched messages list for roomId: " + roomId + " ::: " + jsonBody);
            return createAPIResponse(jsonBody, 200, Utility.createHeaders());
        } catch (AmazonDynamoDBException e) {
            context.getLogger().log("DynamoDB error: " + e.getMessage());
            return createAPIResponse("Error fetching messages: " + e.getMessage(), 500, Utility.createHeaders());
        }
    }

    public APIGatewayProxyResponseEvent deleteMessageById(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        initDynamoDB();
        String messId = apiGatewayRequest.getPathParameters().get("messId");
        Message message = dynamoDBMapper.load(Message.class, messId);
        if (message != null) {
            dynamoDBMapper.delete(message);
            context.getLogger().log("data deleted successfully :::" + messId);
            return createAPIResponse("data deleted successfully." + messId, 200, Utility.createHeaders());
        } else {
            jsonBody = "Message Not Found Exception :" + messId;
            return createAPIResponse(jsonBody, 400, Utility.createHeaders());
        }
    }
}
