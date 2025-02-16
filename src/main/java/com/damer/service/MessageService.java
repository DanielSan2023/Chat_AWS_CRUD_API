package com.damer.service;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.damer.entity.Message;
import com.damer.handler.LambdaHandler;
import com.damer.utility.Utility;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessageService {
    private static final Logger logger = LoggerFactory.getLogger(LambdaHandler.class);
    private final Map<String, DynamoDBMapper> mappers = new HashMap<>();
    private SesClient sesClient;

    final String EMAIL_SUBJECT = "New message was created.";
    final String ADMIN_EMAIL = "spartanboy1984@gmail.com";

    private static final String TABLE_NAME_PREFIX = "message_assistance_";
    private static final String KEY_FOR_COGNITO_GROUPS = "cognito:groups";
    private static final String KEY_STAGE_VARIABLE = "table";

    private DynamoDBMapper getCachedDynamoDBMapper(String tableName) {
        return mappers.computeIfAbsent(tableName, name -> {
            AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
            DynamoDBMapperConfig config = DynamoDBMapperConfig.builder()
                    .withTableNameOverride(DynamoDBMapperConfig.TableNameOverride.withTableNameReplacement(name))
                    .build();
            return new DynamoDBMapper(client, config);
        });
    }

    private APIGatewayV2HTTPResponse createAPIResponse(String body, int statusCode, Map<String, String> headers) {
        APIGatewayV2HTTPResponse responseEvent = new APIGatewayV2HTTPResponse();
        responseEvent.setBody(body);
        responseEvent.setHeaders(headers);
        responseEvent.setStatusCode(statusCode);
        return responseEvent;
    }

    private void validateTableExists(String tableName, Context context) {
        AmazonDynamoDB dynamoDB = AmazonDynamoDBClientBuilder.defaultClient();
        try {
            dynamoDB.describeTable(tableName);
        } catch (ResourceNotFoundException e) {
            context.getLogger().log("Table validation failed: " + tableName);
            createAPIResponse("Table not found: " + e.getMessage(), 404, Utility.createHeaders());
            throw e;
        }
    }

    public APIGatewayV2HTTPResponse saveMessage(APIGatewayV2HTTPEvent apiGatewayRequest, Context context) {
        try {
            Message message = validateAndCreateMessage(apiGatewayRequest.getBody(), context);

            String tableName = getTableName(apiGatewayRequest, context);
            validateTableExists(tableName, context);

            getCachedDynamoDBMapper(tableName).save(message);
            sendEmailNotification(message.getMessId(), message.getContent());

            String jsonBody = Utility.convertObjToString(message, context);
            context.getLogger().log("Data saved successfully to DynamoDB: " + jsonBody);

            return createAPIResponse(jsonBody, HttpStatus.SC_CREATED, Utility.createHeaders());

        } catch (IllegalArgumentException ex) {
            context.getLogger().log("Invalid input: " + ex.getMessage());
            return createAPIResponse("Invalid input: " + ex.getMessage(), HttpStatus.SC_BAD_REQUEST, Utility.createHeaders());
        } catch (ResourceNotFoundException ex) {
            return createAPIResponse("Table not found: " + ex.getMessage(), HttpStatus.SC_NOT_FOUND, Utility.createHeaders());
        } catch (Exception ex) {
            context.getLogger().log("Error processing request: " + ex.getMessage());
            return createAPIResponse("Internal server error: " + ex.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR, Utility.createHeaders());
        }
    }

    private Message validateAndCreateMessage(String body, Context context) {
        Message message = Utility.convertStringToObj(body, context);
        if (message == null || message.getContent() == null) {
            throw new IllegalArgumentException("Message content cannot be null");
        }
        long timestamp = Instant.now().getEpochSecond();
        message.setMessId("mess" + timestamp);
        message.setTimeForStamp(timestamp);
        message.setIsCorrected(false);
        return message;
    }

    private void sendEmailNotification(String messageId, String content) {
        String bodyText = String.format("New message was created:\n\nID: %s\nContent: %s", messageId, content);

        if (sesClient == null) {
            sesClient = SesClient.builder().region(software.amazon.awssdk.regions.Region.EU_CENTRAL_1).build();
        }

        SendEmailRequest emailRequest = SendEmailRequest.builder()
                .destination(Destination.builder().toAddresses(ADMIN_EMAIL).build())
                .message(software.amazon.awssdk.services.ses.model.Message.builder()
                        .subject(Content.builder().data(EMAIL_SUBJECT).build())
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

    public APIGatewayV2HTTPResponse updateMessageById(APIGatewayV2HTTPEvent apiGatewayRequest, Context context) {
        try {
            Message message = Utility.convertStringToObj(apiGatewayRequest.getBody(), context);
            if (message == null || message.getContent() == null) {
                throw new IllegalArgumentException("Message content cannot be null");
            }


            String tableName = getTableName(apiGatewayRequest, context);
            DynamoDBMapper mapper = getCachedDynamoDBMapper(tableName);

            String messId = apiGatewayRequest.getPathParameters().get("messId");

            Message existingMessage = mapper.load(Message.class, messId);
            APIGatewayV2HTTPResponse validationResponse = validateMessageForUpdate(existingMessage, apiGatewayRequest, context);
            if (validationResponse != null) {
                return validationResponse;
            }

            Message updatedMessage = Utility.convertStringToObj(apiGatewayRequest.getBody(), context);
            existingMessage.setContent(updatedMessage.getContent());
            existingMessage.setIsCorrected(true);

            mapper.save(existingMessage);
            String jsonBody = Utility.convertObjToString(existingMessage, context);
            context.getLogger().log("Message updated successfully: " + jsonBody);

            return createAPIResponse(jsonBody, 200, Utility.createHeaders());

        } catch (IllegalArgumentException ex) {
            context.getLogger().log("Invalid input: " + ex.getMessage());
            return createAPIResponse("Invalid input: " + ex.getMessage(), 400, Utility.createHeaders());
        } catch (ResourceNotFoundException ex) {
            context.getLogger().log("Table not found: " + ex.getMessage());
            return createAPIResponse("Table not found: " + ex.getMessage(), 404, Utility.createHeaders());
        } catch (Exception ex) {
            context.getLogger().log("Error processing request: " + ex.getMessage());
            return createAPIResponse("Internal server error: " + ex.getMessage(), 500, Utility.createHeaders());
        }
    }

    private APIGatewayV2HTTPResponse validateMessageForUpdate(
            Message existingMessage, APIGatewayV2HTTPEvent apiGatewayRequest, Context context) {
        if (existingMessage == null) {
            context.getLogger().log("Message Not Found: " + apiGatewayRequest.getPathParameters().get("messId"));
            return createAPIResponse("Message Not Found", 404, Utility.createHeaders());
        }

        if (Utility.convertStringToObj(apiGatewayRequest.getBody(), context) == null) {
            context.getLogger().log("Invalid message content for update.");
            return createAPIResponse("Message content cannot be null", 400, Utility.createHeaders());
        }
        return null;
    }

    public APIGatewayV2HTTPResponse getMessagesByCompanyByRoomIdAndTimestamp(APIGatewayV2HTTPEvent apiGatewayRequest, Context context) {
        try {
            String roomId = getRoomId(apiGatewayRequest);
            if (roomId == null || roomId.isEmpty()) {
                return createAPIResponse("roomId cannot be null or empty", 400, Utility.createHeaders());
            }

            Result result = getStartAndEndTime(apiGatewayRequest);
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

            String tableName = getTableName(apiGatewayRequest, context);
            validateTableExists(tableName, context);

            DynamoDBMapper mapper = getCachedDynamoDBMapper(tableName);

            List<Message> messages = mapper.query(Message.class, queryExpression);
            String jsonBody = Utility.convertListOfObjToString(messages, context);
            context.getLogger().log("Fetched messages list for roomId: " + roomId + " ::: " + jsonBody);
            return createAPIResponse(jsonBody, 200, Utility.createHeaders());

        } catch (ResourceNotFoundException ex) {
            return createAPIResponse("Table not found: " + ex.getMessage(), 404, Utility.createHeaders());
        } catch (AmazonDynamoDBException e) {
            context.getLogger().log("DynamoDB error: " + e.getMessage());
            return createAPIResponse("Error fetching messages: " + e.getMessage(), 500, Utility.createHeaders());
        }
    }

    private record Result(String startTimestampStr, String endTimestampStr) {
    }

    private static Result getStartAndEndTime(APIGatewayV2HTTPEvent apiGatewayRequest) {
        String startTimestampStr = apiGatewayRequest.getQueryStringParameters() != null
                ? apiGatewayRequest.getQueryStringParameters().get("startTimestamp")
                : null;
        String endTimestampStr = apiGatewayRequest.getQueryStringParameters() != null
                ? apiGatewayRequest.getQueryStringParameters().get("endTimestamp")
                : null;
        return new Result(startTimestampStr, endTimestampStr);
    }

    private static String getRoomId(APIGatewayV2HTTPEvent apiGatewayRequest) {
        return apiGatewayRequest.getPathParameters() != null
                ? apiGatewayRequest.getPathParameters().get("roomId")
                : null;
    }


    private String getTableName(APIGatewayV2HTTPEvent apiGatewayV2HTTPEvent, Context context) {
        String tableName = TABLE_NAME_PREFIX + parsingAuthenticationToGetGroups(apiGatewayV2HTTPEvent);
        validateTableExists(tableName, context);
        return tableName;
    }

    private String parsingAuthenticationToGetGroups(APIGatewayV2HTTPEvent apiGatewayRequest) {
        Map<String, String> claims = apiGatewayRequest.getRequestContext().getAuthorizer().getJwt().getClaims();
        String cognitoGroups = claims.get(KEY_FOR_COGNITO_GROUPS);
        if (apiGatewayRequest.getStageVariables() != null) {
            return apiGatewayRequest.getStageVariables().get(KEY_STAGE_VARIABLE);
        }
        cognitoGroups = cognitoGroups.replaceAll("[\\[\\]()]", "");

        return cognitoGroups.split(", ")[0];
    }

    public APIGatewayV2HTTPResponse deleteMessageById(APIGatewayV2HTTPEvent apiGatewayRequest, Context context) {
        try {


            String tableName = getTableName(apiGatewayRequest, context);
            validateTableExists(tableName, context);

            DynamoDBMapper mapper = getCachedDynamoDBMapper(tableName);

            String messId = apiGatewayRequest.getPathParameters().get("messId");
            Message message = mapper.load(Message.class, messId);
            if (message != null) {
                mapper.delete(message);
                context.getLogger().log("data deleted successfully :::" + messId);
                return createAPIResponse("data deleted successfully." + messId, 200, Utility.createHeaders());
            } else {
                String jsonBody = "Message Not Found Exception :" + messId;
                return createAPIResponse(jsonBody, 400, Utility.createHeaders());
            }
        } catch (ResourceNotFoundException ex) {
            return createAPIResponse("Table not found: " + ex.getMessage(), 404, Utility.createHeaders());
        } catch (AmazonDynamoDBException e) {
            context.getLogger().log("DynamoDB error: " + e.getMessage());
            return createAPIResponse("Error deleting message: " + e.getMessage(), 500, Utility.createHeaders());
        }
    }
}
