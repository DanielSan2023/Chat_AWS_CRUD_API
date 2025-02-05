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

        switch (apiGatewayRequest.getRequestContext().getHttp().getMethod()) {

            case "POST":
                return messageService.saveMessage(apiGatewayRequest, context);
            case "PUT":
                return messageService.updateMessageById(apiGatewayRequest, context);
            case "GET":
                if (apiGatewayRequest.getPathParameters() != null) {
                    return messageService.getMessagesByCompanyByRoomIdAndTimestamp(apiGatewayRequest, context);
                }
            case "DELETE":
                if (apiGatewayRequest.getPathParameters() != null) {
                    return messageService.deleteMessageById(apiGatewayRequest, context);
                }
            default:
                throw new Error("Unsupported Methods:::" + apiGatewayRequest.getRequestContext().getHttp().getMethod());

        }
    }
}