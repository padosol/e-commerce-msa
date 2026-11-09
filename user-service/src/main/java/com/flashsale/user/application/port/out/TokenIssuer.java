package com.flashsale.user.application.port.out;

public interface TokenIssuer {

    String issue(long userId);
}
