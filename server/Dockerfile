# The research server (M10.6). Build: docker build -t calore-research server/
# The image builds with the project's Gradle wrapper, so build it from the repository root:
#   docker build -f server/Dockerfile -t calore-research .
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY gradlew ./
COPY gradle ./gradle
COPY server ./server
RUN ./gradlew -p server installDist --no-daemon

FROM eclipse-temurin:17-jre
RUN useradd --system --no-create-home research
COPY --from=build /src/server/build/install/calore-research-server /app
USER research
EXPOSE 8080
ENTRYPOINT ["/app/bin/calore-research-server"]
CMD ["serve"]
