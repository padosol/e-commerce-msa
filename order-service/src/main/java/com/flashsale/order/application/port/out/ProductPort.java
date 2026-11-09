package com.flashsale.order.application.port.out;

import java.util.Collection;
import java.util.List;

/** An unknown event surfaces as EventNotFoundException; any other failure as DownstreamException. No retries. */
public interface ProductPort {

    EventInfo getEvent(long eventId);

    /** Only products that are currently sold come back; unknown and unlisted ids are omitted. Empty input makes no call. */
    List<CatalogProduct> findListed(Collection<Long> productIds);
}
