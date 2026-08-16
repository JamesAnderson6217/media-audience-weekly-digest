#!/usr/bin/env sh
set -eu

: "${INFRAI_API_KEY:?Set INFRAI_API_KEY before starting the service}"
exec mvn spring-boot:run -Dspring-boot.run.profiles=local
