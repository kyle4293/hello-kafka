#!/bin/bash

# Kafka 학습용 테스트 스크립트

echo "🚀 Hello Kafka 테스트 스크립트"
echo "=================================="

BASE_URL="http://localhost:8080/api/kafka"

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 함수 정의
test_api() {
    local name="$1"
    local method="$2"
    local url="$3"
    local data="$4"
    
    echo -e "\n${BLUE}📡 테스트: $name${NC}"
    echo "----------------------------"
    
    if [ "$method" == "GET" ]; then
        response=$(curl -s -w "HTTPSTATUS:%{http_code}" "$url")
    else
        response=$(curl -s -w "HTTPSTATUS:%{http_code}" -X "$method" -H "Content-Type: application/json" -d "$data" "$url")
    fi
    
    http_code=$(echo $response | grep -o "HTTPSTATUS:[0-9]*" | cut -d: -f2)
    body=$(echo $response | sed -E 's/HTTPSTATUS:[0-9]*$//')
    
    if [ "$http_code" -eq 200 ]; then
        echo -e "${GREEN}✅ 성공 (HTTP $http_code)${NC}"
        echo "$body" | jq '.' 2>/dev/null || echo "$body"
    else
        echo -e "${RED}❌ 실패 (HTTP $http_code)${NC}"
        echo "$body"
    fi
}

# 서버 상태 확인
echo -e "${YELLOW}🔍 서버 상태 확인 중...${NC}"
if ! curl -s "$BASE_URL/health" > /dev/null; then
    echo -e "${RED}❌ 서버가 실행되지 않았습니다. 다음 명령어로 실행하세요:${NC}"
    echo "   ./gradlew bootRun"
    exit 1
fi

echo -e "${GREEN}✅ 서버가 실행 중입니다!${NC}"

# 1. 헬스체크
test_api "헬스체크" "GET" "$BASE_URL/health"

# 1-1. Actuator 헬스체크
test_api "Actuator 헬스체크" "GET" "http://localhost:8080/actuator/health"

# 1-2. 애플리케이션 정보
test_api "애플리케이션 정보" "GET" "$BASE_URL/info"

# 1-3. Actuator 메트릭
test_api "Actuator 메트릭" "GET" "http://localhost:8080/actuator/metrics"

# 2. 간단한 메시지 전송
test_api "간단한 메시지 전송" "POST" "$BASE_URL/message" '{"message": "Hello Kafka from script!"}'

# 3. 키-값 메시지 전송
test_api "키-값 메시지 전송" "POST" "$BASE_URL/message/keyed?key=test-key" '{"message": "키가 있는 메시지입니다"}'

# 4. 주문 생성
test_api "주문 생성" "POST" "$BASE_URL/order" '{
    "userId": "USER001",
    "productId": "PROD001",
    "productName": "테스트 상품",
    "quantity": 2,
    "unitPrice": 50000
}'

# 5. 샘플 데이터 생성
test_api "샘플 데이터 생성" "POST" "$BASE_URL/sample"

echo -e "\n${YELLOW}🎉 모든 테스트가 완료되었습니다!${NC}"
echo -e "${BLUE}💡 Kafka UI 확인: http://localhost:8090${NC}"
echo -e "${BLUE}📊 Actuator Health: http://localhost:8080/actuator/health${NC}"
echo -e "${BLUE}📈 Actuator Metrics: http://localhost:8080/actuator/metrics${NC}"
echo -e "${BLUE}📋 애플리케이션 로그를 확인하여 메시지 처리 과정을 살펴보세요.${NC}"
