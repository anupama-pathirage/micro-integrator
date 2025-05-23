/*
 *  Copyright (c) 2025, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.wso2.carbon.inbound.endpoint.protocol.rabbitmq;

import com.rabbitmq.client.AMQP;

public class RabbitMQMessageContext {

    private AMQP.BasicProperties properties;
    private byte[] body;
    private String host;
    private String port;
    private String queue;

    public RabbitMQMessageContext(AMQP.BasicProperties properties, byte[] body, String host, String port, String queue) {

        this.properties = properties;
        this.body = body;
        this.host = host;
        this.port = port;
        this.queue = queue;
    }

    public AMQP.BasicProperties getProperties() {

        return properties;
    }

    public byte[] getBody() {

        return body;
    }

    public String getHost() {

        return host;
    }

    public String getPort() {

        return port;
    }

    public String getQueue() {

        return queue;
    }
}
