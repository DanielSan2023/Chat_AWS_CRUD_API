package com.damer.entity;

import com.amazonaws.services.dynamodbv2.datamodeling.*;

@DynamoDBTable(tableName = "message")
public class Message {

    @DynamoDBHashKey(attributeName = "messId")
    private String messId;

    @DynamoDBAttribute(attributeName = "sender")
    private String sender;

    @DynamoDBAttribute(attributeName = "content")
    private String content;

    @DynamoDBAttribute(attributeName = "timestamp")
    private String timestamp;

    @DynamoDBAttribute(attributeName = "isCorrected")
    private Boolean isCorrected;

    public String getMessId() {
        return messId;
    }

    public void setMessId(String messId) {
        this.messId = messId;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Boolean getIsCorrected() {
        return isCorrected;
    }

    public void setIsCorrected(Boolean isCorrected) {
        this.isCorrected = isCorrected;
    }
}