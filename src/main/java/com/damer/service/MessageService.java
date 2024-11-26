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
import com.damer.handler.LambdaHandler;
import com.damer.utility.Utility;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessageService {
    private static String jsonBody = null;
    private static DynamoDBMapper dynamoDBMapper;
    private static final Logger logger = LoggerFactory.getLogger(LambdaHandler.class);
    private SesClient sesClient;

    private void initDynamoDB() {
        if (dynamoDBMapper == null) {
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
        try {
            Message message = Utility.convertStringToObj(apiGatewayRequest.getBody(), context);
            if (message == null || message.getContent() == null) {
                throw new IllegalArgumentException("Message content cannot be null");
            }
            setMessage(message);
            dynamoDBMapper.save(message);

            String messId = message.getMessId();
            String content = message.getContent();
            sendEmailNotification(messId, content);

            String jsonBody = Utility.convertObjToString(message, context);
            context.getLogger().log("Data saved successfully to DynamoDB: " + jsonBody);

            return createAPIResponse(jsonBody, 201, Utility.createHeaders());

        } catch (IllegalArgumentException ex) {
            context.getLogger().log("Invalid input: " + ex.getMessage());
            return createAPIResponse("Invalid input: " + ex.getMessage(), 400, Utility.createHeaders());
        } catch (Exception ex) {
            context.getLogger().log("Error processing request: " + ex.getMessage());
            return createAPIResponse("Internal server error: " + ex.getMessage(), 500, Utility.createHeaders());
        }
    }

    private static void setMessage(Message message) {
        long timestamp = Instant.now().getEpochSecond();
        message.setMessId("mess" + timestamp);
        message.setTimeForStamp(timestamp);
        message.setIsCorrected(false);
    }

    private void sendEmailNotification(String messageId, String content) {
        final String ADMIN_EMAIL = "spartanboy1984@gmail.com";
        String subject = "New message was created.";
        String bodyText = String.format("New message was created:\n\nID: %s\nContent: %s", messageId, content);

        if (sesClient == null) {
            sesClient = SesClient.builder().region(software.amazon.awssdk.regions.Region.EU_CENTRAL_1).build();
        }

        SendEmailRequest emailRequest = SendEmailRequest.builder()
                .destination(Destination.builder().toAddresses(ADMIN_EMAIL).build())
                .message(software.amazon.awssdk.services.ses.model.Message.builder()
                        .subject(Content.builder().data(subject).build())
                        .body(Body.builder().text(Content.builder().data(bodyText).build()).build())
                        .build())
                .source(ADMIN_EMAIL)
                .build();

        try {
            sesClient.sendEmail(emailRequest);
            logger.info("Email sent successfully.");
        } catch (Exception ex) {
            logger.error("Error sending email: {}", ex.getMessage(), ex);
        }
    }

    public APIGatewayProxyResponseEvent updateMessageById(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        initDynamoDB();

        APIGatewayProxyResponseEvent validationResponse = validateMessageByAuthor(apiGatewayRequest, context);
        if (validationResponse != null) {
            return validationResponse;
        }

        String messId = apiGatewayRequest.getPathParameters().get("messId");
        Message existingMessage = dynamoDBMapper.load(Message.class, messId);

        Message updatedMessage = Utility.convertStringToObj(apiGatewayRequest.getBody(), context);
        existingMessage.setContent(updatedMessage.getContent());
        existingMessage.setIsCorrected(true);

        dynamoDBMapper.save(existingMessage);
        String jsonBody = Utility.convertObjToString(existingMessage, context);
        context.getLogger().log("Message updated successfully: " + jsonBody);

        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }

    private APIGatewayProxyResponseEvent validateMessageByAuthor(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        String messId = apiGatewayRequest.getPathParameters().get("messId");
        String author = apiGatewayRequest.getQueryStringParameters().get("sender");

        Message existingMessage = dynamoDBMapper.load(Message.class, messId);

        if (existingMessage == null) {
            context.getLogger().log("Message Not Found: " + messId);
            return createAPIResponse("Message Not Found: " + messId, 404, Utility.createHeaders());
        }

        if (!existingMessage.getSender().equals(author)) {
            context.getLogger().log("Unauthorized access attempt by: " + author);
            return createAPIResponse("Unauthorized: Only the original author can update this message.", 403, Utility.createHeaders());
        }

        Message updatedMessage = Utility.convertStringToObj(apiGatewayRequest.getBody(), context);
        if (updatedMessage == null || updatedMessage.getContent() == null) {
            context.getLogger().log("Invalid message content for update.");
            return createAPIResponse("Message content cannot be null", 400, Utility.createHeaders());
        }

        return null;
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

        String roomId = getRoomId(apiGatewayRequest);
        Result result = getStartAndEndTime(apiGatewayRequest);

        if (roomId == null || roomId.isEmpty()) {
            return createAPIResponse("roomId cannot be null or empty", 400, Utility.createHeaders());
        }

        long startTimestamp, endTimestamp;
        try {
            startTimestamp = result.startTimestampStr() != null ? Long.parseLong(result.startTimestampStr()) : 0L;
            endTimestamp = result.endTimestampStr() != null ? Long.parseLong(result.endTimestampStr()) : Long.MAX_VALUE;
        } catch (NumberFormatException e) {
            return createAPIResponse("Invalid timestamp format", 400, Utility.createHeaders());
        }

        context.getLogger().log("Fetching messages for roomId: " + roomId +
                " between timestamps: " + startTimestamp + " and " + endTimestamp);

        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":roomId", new AttributeValue().withS(roomId));
        eav.put(":startTimestamp", new AttributeValue().withN(String.valueOf(startTimestamp)));
        eav.put(":endTimestamp", new AttributeValue().withN(String.valueOf(endTimestamp)));

        DynamoDBQueryExpression<Message> queryExpression = new DynamoDBQueryExpression<Message>()
                .withIndexName("RoomId-TimeForStamp-index")
                .withConsistentRead(false)
                .withKeyConditionExpression("RoomId = :roomId and TimeForStamp BETWEEN :startTimestamp and :endTimestamp")
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

    private record Result(String startTimestampStr, String endTimestampStr) {
    }

    private static Result getStartAndEndTime(APIGatewayProxyRequestEvent apiGatewayRequest) {
        String startTimestampStr = apiGatewayRequest.getQueryStringParameters() != null
                ? apiGatewayRequest.getQueryStringParameters().get("startTimestamp")
                : null;
        String endTimestampStr = apiGatewayRequest.getQueryStringParameters() != null
                ? apiGatewayRequest.getQueryStringParameters().get("endTimestamp")
                : null;
        return new Result(startTimestampStr, endTimestampStr);
    }

    private static String getRoomId(APIGatewayProxyRequestEvent apiGatewayRequest) {
        return apiGatewayRequest.getPathParameters() != null
                ? apiGatewayRequest.getPathParameters().get("roomId")
                : null;
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
