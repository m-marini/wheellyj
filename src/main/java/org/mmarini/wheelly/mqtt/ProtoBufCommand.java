/*
 * Copyright (c) 2025-2026 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 *    END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini.wheelly.mqtt;

import com.google.protobuf.InvalidProtocolBufferException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.mmarini.wheelly.protobuf.robot.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.function.Function;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Represents a Protocol Buffers-encoded device command transmitted over MQTT.
 * <p>
 * Encapsulates the command routing identifier, the serialised MQTT payload, and
 * a response transformation function used to deserialize incoming Protobuf
 * replies into strongly typed result objects.
 *
 * @param <R> the expected response type returned when parsing the device reply
 */
public class ProtoBufCommand<R> implements DeviceCommand<R> {
    private static final Logger logger = LoggerFactory.getLogger(ProtoBufCommand.class);

    /**
     * Creates a command to update the device configuration parameters.
     *
     * @param msgId  the unique message correlation identifier
     * @param config the {@link Configuration} payload containing the updated parameters
     * @return a configured {@link ProtoBufCommand} expecting a {@link CommandAck} response
     */
    public static ProtoBufCommand<CommandAck> configCommand(long msgId, Configuration config) {
        MqttMessage msg = new MqttMessage(ConfigurationCommand.newBuilder()
                .setId(msgId)
                .setConfig(config)
                .build().toByteArray());
        return new ProtoBufCommand<>("cf", msg, ProtoBufCommand::toCommandAck);
    }

    /**
     * Creates a command to immediately halt all device movement.
     *
     * @param msgId the unique message correlation identifier
     * @return a configured {@link ProtoBufCommand} expecting a {@link CommandAck} response
     */
    public static ProtoBufCommand<CommandAck> haltCommand(long msgId) {
        MqttMessage msg = new MqttMessage(HaltCommand.newBuilder()
                .setId(msgId)
                .build().toByteArray());
        return new ProtoBufCommand<>("ha", msg, ProtoBufCommand::toCommandAck);
    }

    /**
     * Creates a command to drive the robot forward or backward towards a target 2D coordinate.
     *
     * @param msgId     the unique message correlation identifier
     * @param frontMove {@code true} to move forward; {@code false} to move backward
     * @param target    the target destination coordinates in the reference space
     * @return a configured {@link ProtoBufCommand} expecting a {@link CommandAck} response
     */
    public static ProtoBufCommand<CommandAck> moveCommand(long msgId, boolean frontMove, Point2D target) {
        MqttMessage msg = new MqttMessage(MoveCommand.newBuilder()
                .setId(msgId)
                .setFrontMove(frontMove)
                .setX((float) target.getX())
                .setY((float) target.getY())
                .build().toByteArray());
        return new ProtoBufCommand<>("mv", msg, ProtoBufCommand::toCommandAck);
    }

    /**
     * Creates a command to rotate the robot towards a target heading.
     *
     * @param msgId     the unique message correlation identifier
     * @param direction the target rotation angle in degrees
     * @return a configured {@link ProtoBufCommand} expecting a {@link CommandAck} response
     */
    public static ProtoBufCommand<CommandAck> rotateCommand(long msgId, int direction) {
        MqttMessage msg = new MqttMessage(RotateCommand.newBuilder()
                .setId(msgId)
                .setDirection(direction)
                .build().toByteArray());
        return new ProtoBufCommand<>("ro", msg, ProtoBufCommand::toCommandAck);
    }

    /**
     * Creates a command to orient the head scanner towards a specific angle.
     *
     * @param msgId     the unique message correlation identifier
     * @param direction the target scan angle in degrees
     * @return a configured {@link ProtoBufCommand} expecting a {@link CommandAck} response
     */
    public static ProtoBufCommand<CommandAck> scanCommand(long msgId, int direction) {
        MqttMessage msg = new MqttMessage(ScanCommand.newBuilder()
                .setId(msgId)
                .setDirection(direction)
                .build().toByteArray());
        return new ProtoBufCommand<>("sc", msg, ProtoBufCommand::toCommandAck);
    }

    /**
     * Deserializes an incoming MQTT response payload into a {@link CommandAck} message.
     *
     * @param mqttMessage the raw MQTT message received from the device
     * @return the parsed {@link CommandAck} instance
     * @throws RuntimeException if the payload cannot be parsed as a valid {@link CommandAck}
     */
    private static CommandAck toCommandAck(MqttMessage mqttMessage) {
        try {
            return CommandAck.parseFrom(mqttMessage.getPayload());
        } catch (InvalidProtocolBufferException e) {
            logger.atError().setCause(e).log("Error parsing command ack");
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a command to initiate head tracking towards a target 2D coordinate.
     *
     * @param msgId      the unique message correlation identifier
     * @param frontTrack {@code true} for front-facing tracking; {@code false} for rear-facing tracking
     * @param target     the target point coordinates in the reference space
     * @return a configured {@link ProtoBufCommand} expecting a {@link CommandAck} response
     */
    public static ProtoBufCommand<CommandAck> trackCommand(long msgId, boolean frontTrack, Point2D target) {
        MqttMessage msg = new MqttMessage(TrackCommand.newBuilder()
                .setId(msgId)
                .setFrontTrack(frontTrack)
                .setX((float) target.getX())
                .setY((float) target.getY())
                .build().toByteArray());
        return new ProtoBufCommand<>("ht", msg, ProtoBufCommand::toCommandAck);
    }

    private final String id;
    private final MqttMessage message;
    private final Function<MqttMessage, R> transform;

    /**
     * Constructs a new {@code ProtoBufCommand} instance.
     *
     * @param id        the command suffix or routing identifier (must not be {@code null})
     * @param message   the pre-serialised MQTT message payload (must not be {@code null})
     * @param transform the function used to parse the raw MQTT response into type {@code R} (must not be {@code null})
     * @throws NullPointerException if any of the arguments is {@code null}
     */
    public ProtoBufCommand(String id, MqttMessage message, Function<MqttMessage, R> transform) {
        this.id = requireNonNull(id);
        this.message = requireNonNull(message);
        this.transform = requireNonNull(transform);
    }

    /**
     * Parses an error response message returned by the device into a {@link Throwable}.
     *
     * @param message the raw MQTT error message received from the device
     * @return a {@link DeviceException} containing the decoded error description, or the
     * {@link InvalidProtocolBufferException} if payload parsing fails
     */
    @Override
    public Throwable error(MqttMessage message) {
        try {
            CommandError msg = CommandError.parseFrom(message.getPayload());
            return new DeviceException(
                    format("Error %s",
                            msg.getMessage()));
        } catch (InvalidProtocolBufferException e) {
            return e;
        }
    }

    /**
     * Returns the routing identifier associated with this command.
     *
     * @return the command routing suffix (e.g. {@code "mv"}, {@code "ro"}, {@code "cf"})
     */
    @Override
    public String id() {
        return id;
    }

    /**
     * Deserializes the device's MQTT reply into the expected response type.
     *
     * @param message the raw MQTT response message
     * @return the decoded response object of type {@code R}
     */
    @Override
    public R response(MqttMessage message) {
        return transform.apply(message);
    }

    /**
     * Returns the serialised MQTT message ready for transmission.
     *
     * @return the {@link MqttMessage} containing the Protobuf-encoded command payload
     */
    @Override
    public MqttMessage toMessage() {
        return message;
    }
}
