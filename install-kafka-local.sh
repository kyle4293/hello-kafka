#!/bin/bash

# Kafka 직접 설치 및 실행 스크립트 (macOS/Linux)

echo "🚀 Kafka 직접 설치 가이드"
echo "=========================="

# Kafka 다운로드 및 설치
KAFKA_VERSION="2.8.2"
SCALA_VERSION="2.13"

echo "1. Kafka 다운로드 중..."
if [ ! -d "kafka_${SCALA_VERSION}-${KAFKA_VERSION}" ]; then
    curl -L "https://downloads.apache.org/kafka/${KAFKA_VERSION}/kafka_${SCALA_VERSION}-${KAFKA_VERSION}.tgz" -o kafka.tgz
    tar -xzf kafka.tgz
    rm kafka.tgz
fi

cd kafka_${SCALA_VERSION}-${KAFKA_VERSION}

echo "2. Zookeeper 시작..."
bin/zookeeper-server-start.sh config/zookeeper.properties &
ZOOKEEPER_PID=$!

sleep 5

echo "3. Kafka 브로커 시작..."
bin/kafka-server-start.sh config/server.properties &
KAFKA_PID=$!

echo "4. 토픽 생성..."
sleep 10
bin/kafka-topics.sh --create --topic simple-messages --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
bin/kafka-topics.sh --create --topic order-events --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
bin/kafka-topics.sh --create --topic inventory-updates --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
bin/kafka-topics.sh --create --topic payment-events --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1

echo "✅ Kafka 설정 완료!"
echo "📝 종료하려면 다음 프로세스를 kill하세요:"
echo "   Kafka PID: $KAFKA_PID"
echo "   Zookeeper PID: $ZOOKEEPER_PID"

# 프로세스 ID 저장
echo $KAFKA_PID > kafka.pid
echo $ZOOKEEPER_PID > zookeeper.pid

echo "🛑 종료 스크립트: ./stop-kafka.sh"
