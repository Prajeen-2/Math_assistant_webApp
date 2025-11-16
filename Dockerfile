FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

COPY FibonacciServer.java .
COPY index.html .

RUN javac FibonacciServer.java


EXPOSE 8080


CMD ["java", "FibonacciServer"]
