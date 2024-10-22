package com.damer.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.damer.service.MessageService;

public class LambdaHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        MessageService messageService = new MessageService();

        switch (apiGatewayRequest.getHttpMethod()) {

            case "POST":
                return messageService.saveMessage(apiGatewayRequest, context);

            case "GET":
                if (apiGatewayRequest.getPathParameters() != null) {
                    return messageService.getMessageById(apiGatewayRequest, context);
                }
                return messageService.getMessages(apiGatewayRequest, context);
            case "DELETE":
                if (apiGatewayRequest.getPathParameters() != null) {
                    return messageService.deleteMessageById(apiGatewayRequest, context);
                }
            default:
                throw new Error("Unsupported Methods:::" + apiGatewayRequest.getHttpMethod());

        }
    }
}