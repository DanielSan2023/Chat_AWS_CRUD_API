package com.damer.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.damer.service.MessageService;

public class LambdaHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent apiGatewayRequest, Context context) {
        MessageService messageService = new MessageService();

        String httpMethod = apiGatewayRequest.getRequestContext().getHttp().getMethod();

        switch (httpMethod) {
            case "POST":
                return messageService.saveMessage(apiGatewayRequest, context);

            case "GET":
                if (apiGatewayRequest.getPathParameters() != null) {
                    return messageService.getMessagesByCompanyByRoomIdAndTimestamp(apiGatewayRequest, context);
                }
                return createErrorResponse(400, "Missing path parameters for GET request");

            case "PUT":
                return messageService.updateMessageById(apiGatewayRequest, context);

            case "DELETE":
                if (apiGatewayRequest.getPathParameters() != null) {
                    return messageService.deleteMessageById(apiGatewayRequest, context);
                }
                return createErrorResponse(400, "Missing path parameters for DELETE request");

            default:
                return createErrorResponse(405, "Unsupported Method: " + httpMethod);
        }
    }

    private APIGatewayV2HTTPResponse createErrorResponse(int statusCode, String message) {
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(statusCode)
                .withBody("{\"error\": \"" + message + "\"}")
                .build();
    }
}
