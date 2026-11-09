package com.flashsale.user.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface TestUserStore {

    /** 없는 이메일만 일괄 입력하고, 이메일별 id 를 돌려준다. */
    Map<String, Long> ensure(List<String> emails, String passwordHash, Instant now);
}
