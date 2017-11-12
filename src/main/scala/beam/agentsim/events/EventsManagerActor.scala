package beam.agentsim.events

import akka.actor.{Actor, ActorLogging}
import org.matsim.api.core.v01.events.Event
import org.matsim.core.api.experimental.events.EventsManager


class EventsManagerActor(private val eventsManager: EventsManager) extends Actor with ActorLogging {

  def receive: Receive = {
    case event: Event =>
        eventsManager.processEvent(event)
  }

}


