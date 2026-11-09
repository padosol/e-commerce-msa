package com.flashsale.order.application.port.out;

/** An unknown event surfaces as EventNotFoundException; any other failure as DownstreamException. No retries. */
public interface ProductPort {

    EventInfo getEvent(long eventId);
}
