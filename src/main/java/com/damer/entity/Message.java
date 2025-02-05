package com.damer.entity;

import com.amazonaws.services.dynamodbv2.datamodeling.*;

@DynamoDBTable(tableName = "message_assistance_try")
public class Message {

    @DynamoDBHashKey(attributeName = "MessId")
    private String messId;

    @DynamoDBAttribute(attributeName = "TimeForStamp")
    private long timeForStamp;

    @DynamoDBAttribute(attributeName = "RoomId")
    private String roomId;

    @DynamoDBAttribute(attributeName = "Sender")
    private String sender;

    @DynamoDBAttribute(attributeName = "Content")
    private String content;

    @DynamoDBAttribute(attributeName = "IsCorrected")
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

    @DynamoDBIndexHashKey(globalSecondaryIndexName = "RoomId-TimeForStamp-index", attributeName = "RoomId")
    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @DynamoDBIndexRangeKey(globalSecondaryIndexName = "RoomId-TimeForStamp-index", attributeName = "TimeForStamp")
    public long getTimeForStamp() {
        return timeForStamp;
    }

    public void setTimeForStamp(long timeForStamp) {
        this.timeForStamp = timeForStamp;
    }

    public Boolean getIsCorrected() {
        return isCorrected;
    }

    public void setIsCorrected(Boolean isCorrected) {
        this.isCorrected = isCorrected;
    }
}
