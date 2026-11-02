import { check } from 'k6';
import { classify } from './classify.js';

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: { checks: ['rate==1'] },
};

const cases = [
  ['201 -> paid', { status: 201, body: '{}' }, 'paid'],
  ['409 SOLD_OUT -> sold_out', { status: 409, body: '{"reason":"SOLD_OUT"}' }, 'sold_out'],
  ['409 STOCK_CONFLICT -> conflict', { status: 409, body: '{"reason":"STOCK_CONFLICT"}' }, 'conflict'],
  ['409 other reason -> other', { status: 409, body: '{"reason":"WHATEVER"}' }, 'other'],
  ['504 INVENTORY_TIMEOUT -> order_timeout', { status: 504, body: '{"reason":"INVENTORY_TIMEOUT"}' }, 'order_timeout'],
  ['504 ORDER_DB_TIMEOUT -> order_timeout', { status: 504, body: '{"reason":"ORDER_DB_TIMEOUT"}' }, 'order_timeout'],
  ['504 GATEWAY_TIMEOUT -> gateway_timeout', { status: 504, body: '{"code":"GATEWAY_TIMEOUT","message":"x"}' }, 'gateway_timeout'],
  ['504 no body -> other', { status: 504, body: '' }, 'other'],
  ['status 0 + 1050 -> client_timeout', { status: 0, body: null, error_code: 1050 }, 'client_timeout'],
  ['status0NonTimeoutIsOther', { status: 0, body: null, error_code: 1000 }, 'other'],
  ['502 INVENTORY_ERROR -> other', { status: 502, body: '{"reason":"INVENTORY_ERROR"}' }, 'other'],
  ['400 -> other', { status: 400, body: '{}' }, 'other'],
  ['401 -> other', { status: 401, body: '' }, 'other'],
];

export default function () {
  for (const [name, res, expected] of cases) {
    check(res, { [name]: (r) => classify(r) === expected });
  }
}
