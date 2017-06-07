package com.omearac.consumers

import akka.actor.{Actor, ActorRef}
import akka.event.Logging
import com.omearac.consumers.ConsumerStreamManager.Messages.{InitializeConsumerStream, TerminateConsumerStream}
import com.omearac.consumers.DataConsumer.Messages.{ConsumerActorReply, ManuallyInitializeStream, ManuallyTerminateStream}
import com.omearac.kafka_messages.ExampleAppEvent
import com.omearac.settings.Settings
import com.omearac.shared.EventMessages.ActivatedConsumerStream
import com.omearac.shared.EventSourcing
import scala.collection.mutable.ArrayBuffer


/**
  * This actor serves as a Sink for the kafka stream that is created by the ConsumerStreamManager.
  * The corresponding stream converts the json from the kafka topic AppEventChannel to the message type ExampleAppEvent.
  * Once this actor receives a batch of such messages he prints them out.
  *
  * This actor can be started and stopped manually from the HTTP interface, and in doing so, changes between receiving
  * states.
  */

object EventConsumer {
}

class EventConsumer extends Actor with EventSourcing {
    implicit val system = context.system
    val log = Logging(system, this.getClass.getName)

    //Once stream is started by manager, we save the actor ref of the manager
    var consumerStreamManager: ActorRef = null

    //Get Kafka Topic
    val kafkaTopic = Settings(system).KafkaConsumers.KafkaConsumerInfo("ExampleAppEvent")("subscription-topic")

    def receive: Receive = {
        case InitializeConsumerStream(_,ExampleAppEvent) =>
            consumerStreamManager ! InitializeConsumerStream(self, ExampleAppEvent)

        case ActivatedConsumerStream(_) => consumerStreamManager = sender()

        case "STREAM_INIT" =>
            sender() ! "OK"
            println("Event Consumer entered consuming state!")
            context.become(consumingEvents)

        case ManuallyTerminateStream => sender() ! ConsumerActorReply("Event Consumer Stream Already Stopped")

        case ManuallyInitializeStream =>
            consumerStreamManager ! InitializeConsumerStream(self, ExampleAppEvent)
            sender() ! ConsumerActorReply("Event Consumer Stream Started")

        case other => log.error("Event Consumer got unknown message while in idle:" + other )
    }

    def consumingEvents: Receive = {
        case ActivatedConsumerStream(_) => consumerStreamManager = sender()

        case consumerMessageBatch: ArrayBuffer[_] =>
            sender() ! "OK"
            consumerMessageBatch.foreach(println)

        case "STREAM_DONE" =>
            context.become(receive)

        case ManuallyInitializeStream => sender() ! ConsumerActorReply("Event Consumer Already Started")

        case ManuallyTerminateStream =>
            consumerStreamManager ! TerminateConsumerStream(kafkaTopic)
            sender() ! ConsumerActorReply("Event Consumer Stream Stopped")

        case other => log.error("Event Consumer got unknown message while in consuming: " + other)
    }
}

