FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:copy-dependencies -DoutputDirectory=lib -DincludeScope=runtime
COPY src ./src
RUN mkdir out && javac -cp "lib/*" -d out $(find src -name '*.java')

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/lib ./lib
COPY --from=build /app/out ./out
EXPOSE 18018
CMD ["java", "-cp", "out:lib/*", "Main"]
