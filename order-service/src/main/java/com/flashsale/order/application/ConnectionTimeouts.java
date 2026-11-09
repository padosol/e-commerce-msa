package com.flashsale.order.application;

import java.sql.SQLTransientConnectionException;

final class ConnectionTimeouts {

    private ConnectionTimeouts() {
    }

    /** True when the DB connection pool timed out somewhere in the cause chain. */
    static boolean isCausing(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof SQLTransientConnectionException) {
                return true;
            }
        }
        return false;
    }
}
