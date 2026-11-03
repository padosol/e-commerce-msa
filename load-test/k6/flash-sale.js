import http from 'k6/http';
import { SharedArray } from 'k6/data';
import { sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { classify } from './classify.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EVENT_ID = __ENV.EVENT_ID;
const USERS = parseInt(__ENV.USERS || '500', 10);
// 요청이 도착하는 시간(초). 0 보다 크면 사용자 i(0부터)가 i × ARRIVAL_SECONDS / USERS 초 뒤에 요청해서,
// USERS 건이 이 시간 동안 같은 간격으로 도착한다. 0 이면 USERS 명이 같은 순간에 요청한다(0단계~1단계 4차 측정 방식).
const ARRIVAL_SECONDS = parseFloat(__ENV.ARRIVAL_SECONDS || '1');
if (!(ARRIVAL_SECONDS >= 0)) throw new Error(`ARRIVAL_SECONDS must be >= 0: ${__ENV.ARRIVAL_SECONDS}`);
const TOKENS_FILE = __ENV.TOKENS_FILE;

if (!EVENT_ID) throw new Error('EVENT_ID is required');
if (!TOKENS_FILE) throw new Error('TOKENS_FILE is required');

const tokens = new SharedArray('tokens', () => JSON.parse(open(TOKENS_FILE)));
if (tokens.length < USERS) {
  throw new Error(`tokens (${tokens.length}) < USERS (${USERS})`);
}

const counters = {
  paid: new Counter('orders_paid'),
  sold_out: new Counter('orders_sold_out'),
  conflict: new Counter('orders_conflict'),
  order_timeout: new Counter('orders_order_timeout'),
  gateway_timeout: new Counter('orders_gateway_timeout'),
  client_timeout: new Counter('orders_client_timeout'),
  other: new Counter('orders_other'),
};

http.setResponseCallback(http.expectedStatuses(201, 409));

// 사용자(VU) USERS 명이 한 번씩 요청한다. 도착 간격은 아래 default 함수의 sleep 이 만든다.
// (constant-arrival-rate 는 구간 끝 경계에서 반복을 한 번 더 시작해 요청 수가 USERS 와 어긋날 수 있어 쓰지 않는다)
export const options = {
  scenarios: {
    flash_sale: {
      executor: 'per-vu-iterations',
      vus: USERS,
      iterations: 1,
      maxDuration: '5m',
    },
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const i = __VU - 1;
  if (ARRIVAL_SECONDS > 0) sleep((i * ARRIVAL_SECONDS) / USERS);
  const token = tokens[i].accessToken;
  const res = http.post(
    `${BASE_URL}/api/orders`,
    JSON.stringify({ eventId: parseInt(EVENT_ID, 10) }),
    {
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      timeout: '60s',
    },
  );
  counters[classify(res)].add(1);
}
