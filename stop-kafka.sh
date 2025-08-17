#!/bin/bash

# Kafka 종료 스크립트

echo "🛑 Kafka 종료 중..."

if [ -f "kafka.pid" ]; then
    KAFKA_PID=$(cat kafka.pid)
    kill $KAFKA_PID
    rm kafka.pid
    echo "✅ Kafka 종료됨 (PID: $KAFKA_PID)"
fi

if [ -f "zookeeper.pid" ]; then
    ZOOKEEPER_PID=$(cat zookeeper.pid)
    kill $ZOOKEEPER_PID
    rm zookeeper.pid
    echo "✅ Zookeeper 종료됨 (PID: $ZOOKEEPER_PID)"
fi

echo "🏁 모든 서비스가 종료되었습니다."
