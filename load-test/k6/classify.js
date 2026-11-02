// 주문 응답 하나를 7종 중 하나로 분류한다.
const ORDER_TIMEOUT_REASONS = ['INVENTORY_TIMEOUT', 'PAYMENT_TIMEOUT', 'PRODUCT_TIMEOUT', 'ORDER_DB_TIMEOUT'];
const K6_TIMEOUT_ERROR_CODE = 1050;

function parseBody(res) {
  try {
    const parsed = typeof res.body === 'string' ? JSON.parse(res.body) : null;
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch (e) {
    return {};
  }
}

export function classify(res) {
  const status = res.status;
  if (status === 201) return 'paid';
  if (status === 0) return res.error_code === K6_TIMEOUT_ERROR_CODE ? 'client_timeout' : 'other';
  const body = parseBody(res);
  if (status === 409) {
    if (body.reason === 'SOLD_OUT') return 'sold_out';
    if (body.reason === 'STOCK_CONFLICT') return 'conflict';
    return 'other';
  }
  if (status === 504) {
    if (ORDER_TIMEOUT_REASONS.includes(body.reason)) return 'order_timeout';
    if (body.code === 'GATEWAY_TIMEOUT') return 'gateway_timeout';
  }
  return 'other';
}
