package beam.playground.metasim.events;

import org.matsim.core.events.handler.EventHandler;

public interface TransitionEventHandler extends EventHandler{
	public void handleEvent(TransitionEvent event);
}