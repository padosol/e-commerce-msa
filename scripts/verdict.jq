# 입력: {saleQuantity, paid, pending, finalStock} -> verdict 문자열
# OVERSOLD: 초과 판매 / INCONCLUSIVE: 주문 처리 중이거나 재고가 남았는데 판매가 모자람(실행이 제대로 일어나지 않음) / OK
if (.paid - .saleQuantity) > 0 then "OVERSOLD"
elif .pending > 0 or (.finalStock > 0 and .paid < .saleQuantity) then "INCONCLUSIVE"
else "OK" end
