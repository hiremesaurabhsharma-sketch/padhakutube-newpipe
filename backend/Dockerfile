FROM gradle:8.14-jdk21 AS build
WORKDIR /src
COPY . .
RUN gradle --no-daemon installDist
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/install/padhakutube-api/ /app/
USER 10001
EXPOSE 8080
CMD ["/app/bin/padhakutube-api"]
