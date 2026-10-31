import http from 'k6/http';
import { SharedArray } from 'k6/data';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EVENT_ID = __ENV.EVENT_ID;
const USERS = parseInt(__ENV.USERS || '5000', 10);
const TOKENS_FILE = __ENV.TOKENS_FILE;

if (!EVENT_ID) throw new Error('EVENT_ID is required');
if (!TOKENS_FILE) throw new Error('TOKENS_FILE is required');

const tokens = new SharedArray('tokens', () => JSON.parse(open(TOKENS_FILE)));
if (tokens.length < USERS) {
  throw new Error(`tokens (${tokens.length}) < USERS (${USERS})`);
}

const ordersPaid = new Counter('orders_paid');
const ordersSoldOut = new Counter('orders_sold_out');
const ordersOther = new Counter('orders_other');

http.setResponseCallback(http.expectedStatuses(201, 409));

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
  const token = tokens[__VU - 1].accessToken;
  const res = http.post(
    `${BASE_URL}/api/orders`,
    JSON.stringify({ eventId: parseInt(EVENT_ID, 10) }),
    {
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      timeout: '60s',
    },
  );
  if (res.status === 201) ordersPaid.add(1);
  else if (res.status === 409) ordersSoldOut.add(1);
  else ordersOther.add(1);
}
