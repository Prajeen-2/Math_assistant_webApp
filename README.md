# Math_assistant_webApp

A small Java web application that provides:

- A **basic calculator** (addition, subtraction, multiplication, division) using a Java HTTP server.
- A **Fibonacci curve (spiral)** rendered as a PNG image generated on the server.

The backend is built with Java’s built‑in HTTP server and Java 2D, and the frontend is a simple HTML + JavaScript page.

---

## Features

### Calculator

- Four operations:
  - addition  
  - subtraction  
  - multiplication  
  - division  
- Takes two numeric inputs from the user.
- Sends a request to the backend and displays the numeric result.

### Fibonacci Curve (Spiral)

- Takes **one integer input** (number of Fibonacci terms, 1–40).
- Draws a continuous Fibonacci spiral where each Fibonacci number is the **radius** of one quarter arc.
- Rendered on the server as a PNG image and displayed in the browser.

---

## Tech Stack

**Backend (Java)**

- Java HTTP server from the JDK (`com.sun.net.httpserver.HttpServer`).
- Core Java libraries (`java.io`, `java.net`, `java.util`, `java.nio.charset`).
- Java 2D / AWT (`java.awt`, `java.awt.geom`, `java.awt.image`) for drawing, plus `javax.imageio.ImageIO` for PNG output.

**Frontend**

- HTML5 + CSS for layout and styling.
- Vanilla JavaScript (`fetch`, `URLSearchParams`) for calling the backend.
- `<img>` element for displaying the generated Fibonacci curve image.

---


