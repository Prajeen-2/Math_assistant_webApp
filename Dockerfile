# 1. Use a JDK base image
FROM eclipse-temurin:17-jdk-jammy

# 2. Set working directory inside the container
WORKDIR /app

# 3. Copy source files into the image
COPY FibonacciServer.java .
COPY index.html .

# 4. Compile the Java server
RUN javac FibonacciServer.java

# 5. Expose the port Render will map to PORT
# (Render will set PORT env var, but we document 8080 here)
EXPOSE 8080

# 6. Start the server
# The server code already reads PORT from env and defaults to 8080
CMD ["java", "FibonacciServer"]
