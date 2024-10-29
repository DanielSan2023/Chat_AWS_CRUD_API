package com.damer.entity;

import com.amazonaws.services.dynamodbv2.datamodeling.*;

@DynamoDBTable(tableName = "message")
public class Message {

    @DynamoDBHashKey(attributeName = "messId")
    private String messId;


    private String roomId;


    private long timeForStamp;

    @DynamoDBAttribute(attributeName = "sender")
    private String sender;

    @DynamoDBAttribute(attributeName = "content")
    private String content;


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

    @DynamoDBIndexHashKey(globalSecondaryIndexName = "RoomIndex", attributeName = "roomId")
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

    @DynamoDBIndexRangeKey(globalSecondaryIndexName = "RoomIndex", attributeName = "timeForStamp")
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
