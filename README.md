# Chat - Serverless Messaging Application with AWS

## Overview

The **eCharita** project is a serverless application designed to handle and manage chat messages 
for the website [echarita.cz](https://echarita.cz/). It leverages AWS services such as DynamoDB, Lambda, and SES to provide a
scalable and efficient backend for storing and notifying about messages. Notifications are sent to administrator whenever a new message is saved.

---

## Features

- **Serverless Architecture**: Uses AWS Lambda for compute and DynamoDB for data storage.
- **RESTful API**: Supports CRUD operations for managing messages.
- **Real-time Notifications**: Sends email notifications to administrators via Amazon SES.
- **Global Index Support**: Efficient querying using DynamoDB global secondary indexes.

---

## Project Structure

### Main Components:

1. **Entity - `Message`**
    - Represents a chat message stored in DynamoDB.
    - Uses DynamoDB annotations for seamless integration.

2. **Handler - `LambdaHandler`**
    - Entry point for AWS Lambda functions.
    - Routes HTTP methods (GET, POST, PUT, DELETE) to appropriate service methods.

3. **Service - `MessageService`**
    - Implements business logic for managing messages.
    - Interacts with DynamoDB using `DynamoDBMapper`.
    - Sends email notifications via Amazon SES.

4. **Utility - `Utility`**
    - Provides helper methods for JSON conversion and API response generation.

---

## DynamoDB Table Design

### Table Schema

| Attribute         | Type     | Notes                                   |
|-------------------|----------|-----------------------------------------|
| `MessId`          | String   | Primary key. Unique identifier for a message. |
| `Firma`           | String   | Name of the company (partitioning).   |
| `RoomId`          | String   | Secondary index for room-based queries. |
| `TimeForStamp`    | Number   | Range key for the `RoomId-TimeForStamp-index`. |
| `Sender`          | String   | Sender of the message.                 |
| `Content`         | String   | Message content.                       |
| `IsCorrected`     | Boolean  | Indicates if the message was updated.  |

### Index

- **Global Secondary Index**: `RoomId-TimeForStamp-index`
    - Partition Key: `RoomId`
    - Sort Key: `TimeForStamp`

---

## API Endpoints

### 1. Create Message
**POST** `/messages`
- **Body**: JSON with `firma`, `sender`, `content`.
- **Response**: 201 Created with message details.

### 2. Update Message
**PUT** `/messages/{messId}`
- **Body**: JSON with updated `content`.
- **Query Param**: `sender` to validate the author.
- **Response**: 200 OK with updated message details.

### 3. Retrieve Messages
**GET** `/messages`
- **Query Params**:
    - `firma`: Company name (required).
    - `roomId`: Room ID for the chat (required).
    - `startTimestamp`: Start timestamp (optional).
    - `endTimestamp`: End timestamp (optional).
- **Response**: 200 OK with a list of messages.

### 4. Delete Message
**DELETE** `/messages/{messId}`
- **Response**: 200 OK on success.

---

## Email Notifications

### Details
- Sent via **Amazon SES**.
- Email includes message ID and content.
- Administrator email is configurable (`xxxxxxxx@gmail.com` by default).

---

## Prerequisites

- **AWS Account**: Ensure AWS Lambda, DynamoDB, and SES are set up.
- **Tools**:
    - Java 11+
    - AWS CLI
    - Maven