FROM gradle:8.10.2-jdk17 AS builder

WORKDIR /build
COPY . .
RUN gradle --no-daemon installDist

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
COPY --from=builder /build/build/install/gear-lead-engine /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /app/data

EXPOSE 3000

ENV APP_PORT=3000
ENV SQLITE_PATH=/app/data/lead-engine.db

CMD ["bin/gear-lead-engine"]
